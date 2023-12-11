package Interface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

public interface BulletinBoard extends Remote {
    void write(int idx, String u, String tag) throws RemoteException, InterruptedException;

    String get(int idx, String tag) throws RemoteException, NoSuchAlgorithmException;

    int getSize() throws RemoteException, InterruptedException;
}
