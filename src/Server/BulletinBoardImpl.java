package Server;

import Interface.BulletinBoard;
import Interface.publicCryptographicHash;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Semaphore;

public class BulletinBoardImpl extends UnicastRemoteObject implements BulletinBoard {
    int chunkSize = 8;
    int size = chunkSize;
    int amountOccupied = 0;
    int maxOccupiedPerChunk = 4;
    int currentAmountChunks = 1;
    List<Board> boards = new LinkedList<>();
    Semaphore semaphore = new Semaphore(1);


    public BulletinBoardImpl() throws RemoteException {
        boards.add(new Board(chunkSize));
    }

    @Override
    public synchronized void write(int idx, String u, String tag) throws RemoteException, InterruptedException {
        boards.get(boards.size()-1).write(idx, u, tag);
        notifyAll();
        semaphore.release();
    }

    @Override
    public synchronized String get(int idx, String tag) throws RemoteException, NoSuchAlgorithmException {
        boolean conditionMet = false;
        Board retBoard = null;
        List<Board> removeBoards = new LinkedList<>();
        while (!conditionMet) {
            for (Board board : boards){
                if (board.isEmpty() && boards.indexOf(board) != boards.size()-1){
                    removeBoards.add(board);
                    System.out.println("remove board");
                }
                else{
                    if (board.get(idx) != null){
                        if (board.get(idx).containsKey(publicCryptographicHash.hashPreimageTag(tag))){
                            conditionMet = true;
                            retBoard = board;
                            break;
                        }
                    }
                }
            }
            for (Board board : removeBoards) boards.remove(board);
            if (conditionMet) break;
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        amountOccupied--;
        String message = retBoard.get(idx).get(publicCryptographicHash.hashPreimageTag(tag));
        retBoard.get(idx).remove(publicCryptographicHash.hashPreimageTag(tag));
        return message;
    }

    @Override
    public synchronized int getSize() throws InterruptedException {
        amountOccupied++;
        int newScale = getScale();
        if (newScale != size){
            size = newScale;
            currentAmountChunks++;
            boards.add(new Board(newScale));
            System.out.println("new size: " + newScale);
        }
        semaphore.acquire();
        return size;
    }

    private synchronized int getScale(){
        return ((amountOccupied/maxOccupiedPerChunk)+1) * chunkSize;
    }
}
