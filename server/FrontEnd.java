import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

public class FrontEnd implements Auction {
    String[] replicas;
    String primary = null;
    public FrontEnd() {
    }

/**
 * This group of functions passes requests from the user over to the current primary replica,
 * if the primary replica is unavailable then the function will recursively attempt to locate and 
 * assign a new primary replica before re-calling itself and attempting to handle the users' request
 * once more.
 */
    public NewUserInfo newUser(String email) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            NewUserInfo newuser = server.newUser(email);
            return newuser;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return newUser(email);
        }
    }

    public byte[] challenge(int userID) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            byte[] signatureBytes = server.challenge(userID);
            return signatureBytes;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return challenge(userID);
        }
    }

    public boolean authenticate(int userID, byte[] signature) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            boolean verified = server.authenticate(userID,signature);
            return verified;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return authenticate(userID,signature);
        }
    }

    public AuctionItem getSpec(int itemID) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            AuctionItem item = server.getSpec(itemID);
            return item;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return getSpec(itemID);
        }
    }

    public int newAuction(int userID, AuctionSaleItem item) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            int id = server.newAuction(userID, item);
            return id;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return newAuction(userID, item);
        }
    }

    public AuctionItem[] listItems() throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            AuctionItem[] items = server.listItems();
            return items;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return listItems();
        }
    }

    public AuctionCloseInfo closeAuction(int userID, int itemID) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            AuctionCloseInfo info = server.closeAuction(userID, itemID);
            return info;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return closeAuction(userID, itemID);
        }
    }

    public boolean bid(int userID, int itemID, int price) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(primary);
            boolean result = server.bid(userID, itemID, price);
            return result;
        }
        catch (Exception e) {
            System.out.println("Primary Replica is unavailable");
            findReplicas();
            findNewPrimary();
            return bid(userID, itemID, price);
        }
    }

    /**
     * Returns the id of the primary replica, if no primary replica exists, for
     * example, at runtime, the function will attempt to assign a new primary
     * replica from the list of advertised replicas.
     */
    public int getPrimaryReplicaID() throws RemoteException {
        if (primary == null) {
            findReplicas();
            findNewPrimary();
            return getPrimaryReplicaID();
        }
        String id = primary.substring(primary.length() - 1); 
        return Integer.parseInt(id);
    }

    /**
     * Generates a list of available replicas that are being advertised on the "localhost"
     * registry. This function also removes the FrontEnd and current primary replica from
     * this list as they are redundant.
     */
    private void findReplicas() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            String[] names = registry.list();
            for (int i=0;i<names.length;i++) {
                if (names[i].equals("FrontEnd") || names[i].equals(primary))
                    names[i] = null;
            }
            replicas = names;
            return;
        }
        catch (Exception e) {
            System.err.println("Exception: cannot find any replicas");
            e.printStackTrace();
        }
    }

    /**
     * Iterates through the list generated by findReplicas() and attempts to make contact with
     * each replica. The first replica that gives a response is set as the new primary replica.
     */
    private void findNewPrimary() {
        for (String replica:replicas) {
            if (replica != null) {
                try {
                    String name = replica;
                    Registry registry = LocateRegistry.getRegistry("localhost");
                    Server server = (Server) registry.lookup(name);
                    if (server.checkAlive()) {
                        primary = replica;
                        return;
                    }
                }
                catch (Exception e) {}
            }
        }
    }
    public static void main(String[] args) {
        Registry registry;
        System.out.println(System.getProperty("java.version"));
        try {
         FrontEnd s = new FrontEnd();
         String name = "FrontEnd";
         Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
         registry = LocateRegistry.getRegistry();
         registry.rebind(name, stub);
         System.out.println("Server ready");
        } catch (Exception e) {
         System.err.println("Exception:");
         e.printStackTrace();
        }
      }
    
}
