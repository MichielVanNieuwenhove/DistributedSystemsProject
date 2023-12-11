package Client;

import Interface.BulletinBoard;
import Interface.publicCryptographicHash;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;


public class Main_Client {
    private BulletinBoard bulletinBoard;
    private final String name;
    private int boardSize = 8;

    private Connection connection;
    private Map<String, Connection> connections = new HashMap<>();

    private JTextArea textArea;
    private DefaultListModel<String> listModel;

    boolean connected = true;
    Semaphore semaphore = new Semaphore(1);

    public Main_Client(String name){
        this.name = name;
        startClient();
    }

    private void startClient() {
        try {
            Registry myRegistry = LocateRegistry.getRegistry("localhost", 1099);
            bulletinBoard = (BulletinBoard) myRegistry.lookup("BulletinBoard");
            createGUI();

        } catch (Exception e) { e.printStackTrace();
        }
    }

    private void createGUI() {
        JFrame f = new JFrame("Chat: " + name);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        textArea = new JTextArea(10, 20);
        JTextField textField1 = new JTextField(16);
        JButton connectButton = new JButton("Connect/Disconnect");

        JScrollPane scrollPane = new JScrollPane(textArea);
        textArea.setEditable(false);

        // Add textField1 to the top
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        p.add(textField1, gbc);

        // Add scrollPane to the center
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        p.add(scrollPane, gbc);

        // Create a panel to hold the button and nameScrollPane
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

        // Add the JList for names to the buttonPanel
        listModel = new DefaultListModel<>();
        JList<String> nameList = new JList<>(listModel);
        JScrollPane nameScrollPane = new JScrollPane(nameList);
        buttonPanel.add(nameScrollPane);

        // Add the button to the buttonPanel
        buttonPanel.add(connectButton);

        // Add the buttonPanel to the SOUTH region of the main panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        p.add(buttonPanel, gbc);

        // Set up your frame
        f.setContentPane(p);
        f.setSize(400, 400);
        f.setVisible(true);

        // List selection listener for names
        nameList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedName = nameList.getSelectedValue();
                if (selectedName != null && !selectedName.equals(connection.getName_other())) {
                    textArea.setText("");
                    connection = connections.get(selectedName);
                    List<String> history = connection.getHistory();
                    for (int i = history.size()-1; i >= 0; i--){
                        try {
                            textArea.getDocument().insertString(0, history.get(i), null);
                        } catch (BadLocationException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        });

        textField1.addActionListener(e -> {
            try {
                if (connected){
                    String text = textField1.getText();
                    send(text);
                    String fullText = "[" + name + "]" + ": " + text + "\n";
                    textArea.append(fullText);
                    connection.addToHistory(fullText);
                    textField1.setText("");
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        connectButton.addActionListener(e -> {
            try {
                connected = !connected;
                if (connected) semaphore.release();
                else semaphore.acquire();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public Connection setup(String name_other) throws Exception {
        listModel.addElement(name_other);

        //generate key
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        SecretKey key = keyGenerator.generateKey();

        //generate current index & tag:
        int currentIndex = new SecureRandom().nextInt(boardSize);
        String currentTag = generateNewTag();

        Connection newConnection = new Connection();
        connections.put(name_other, newConnection);
        connection = newConnection;
        connection.setAttributes_mine(currentIndex, currentTag, key);
        connection.setName_other(name_other);

        return connection;
    }

    public void setConnection(Connection c){
        SecretKey decodedKey = c.getKey_mine();
        connection.setAttributes_other(c.getIndex_mine(), c.getTag_mine(), decodedKey);

        new ReadThread(bulletinBoard, connection, this).start();
    }

    private void send(String m) throws Exception {
        if (connection.getKey_mine() == null) return;

        //generate next index & tag
        boardSize = bulletinBoard.getSize();
        int nextIndex = new SecureRandom().nextInt(boardSize);
        String nextTag = generateNewTag();

        //message = idx||:||tag||value -> we weten da tag 128 lang is
        String data = nextIndex + ":" + nextTag + m;
        String encryptedData = encrypt(data, connection.getKey_mine());
        bulletinBoard.write(connection.getIndex_mine(), encryptedData, publicCryptographicHash.hashPreimageTag(connection.getTag_mine()));

        //generate new key
        SecretKey nextKey = deriveKey(connection.getKey_mine());

        connection.setAttributes_mine(nextIndex, nextTag, nextKey);
    }

    public boolean receive(String encryptedData, Connection connection) throws InterruptedException {
        semaphore.acquire();
        semaphore.release();
        if (connected){
            try{
                if (encryptedData != null){
                    String decryptedData = decrypt(encryptedData, connection.getKey_other());
                    String[] data = splitMessage(decryptedData);

                    //generate new key
                    SecretKey nextKey = deriveKey(connection.getKey_other());

                    connection.setAttributes_other(Integer.parseInt(data[0]), data[1], nextKey);
                    String text = "[" + connection.getName_other() + "]: " + data[2] + "\n";
                    if (this.connection.equals(connection)) {
                        textArea.append(text);
                    }
                    connection.addToHistory(text);
                }
            }
            catch (Exception ignored){}
        }

        return connected;
    }

    private String[] splitMessage(String s){
        String[] data = new String[3];
        String[] split = s.split(":", 2);
        data[0] = split[0];
        data[1] = split[1].substring(0, 172);
        data[2] = split[1].substring(172);

        return data;
    }

    private String generateNewTag(){
        byte[] randomBytes1 = new byte[128];
        new SecureRandom().nextBytes(randomBytes1);
        return Base64.getEncoder().encodeToString(randomBytes1);
    }

    private String encrypt(String plaintext, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    private String decrypt(String encryptedText, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private SecretKey deriveKey(SecretKey key) throws Exception {
        String salt = "salt";
        int iterationCount = 1000;
        int derivedKeyLength = 128;

        // Derive a new key using PBKDF2
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(Base64.getEncoder().encodeToString(key.getEncoded()).toCharArray(), salt.getBytes(), iterationCount, derivedKeyLength);

        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public String getName(){
        return name;
    }
}