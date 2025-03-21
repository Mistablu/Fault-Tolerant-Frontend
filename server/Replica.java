import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;


public class Replica implements Server{
    //Initialises data structures for storing information regarding items
    AuctionItem[] allitems;
    Object[][] auctions;
    Object[][] bids;
    //Initialises data structures for storing information regarding users
    String[] newUsers;
    String[] verifiedUsers;
    boolean[][] verified;
    NewUserInfo[] userkeys;

    KeyPair keyPair; //The keypair held by the server

    public Replica() throws NoSuchAlgorithmException {
        super();
        allitems = new AuctionItem[50]; //up to 50 concurrent items
        newUsers = new String[100]; //up to 100 concurrent users
        verifiedUsers = new String[100];
        userkeys = new NewUserInfo[100];
        auctions = new Object[50][2];
        bids = new Object[50][2];
        verified = new boolean[100][2];
        //Generates and stores the servers' keypair at runtime
        keyPair = generateKey();
        storeKey(keyPair);
    }

    /**
     * Allows a client to register a user into the database, provided the user does not already exist,
     * provides the user with an ID and public and private keys to be used for asymmetric cryptography.
     * @param email the email address of the user to be added to the database
     * @return an object containing the users' ID and the byte arrays of their public and private keys
     * @throws RemoteException if a remote exception occurs
     */
    public synchronized NewUserInfo newUser(String email) throws RemoteException {
        byte[] privatKey = null;
        byte[] publiKey = null;

        //Attempts to generate a new public and private key for the user
        try {
        KeyPair userkeys = generateKey();
        PrivateKey privKey = userkeys.getPrivate();
        PublicKey pubKey = userkeys.getPublic();
        //turns the keys into byte arrays for transfer
        privatKey = privKey.getEncoded();
        publiKey = pubKey.getEncoded();
        } catch (NoSuchAlgorithmException e) { //Catches exceptions
            System.out.println("No such Algorithm");
        }
        //Prevents users with duplicate email addresses being stored in the database.
        for (String user:newUsers) {
            if (email.equals(user))
                return null; //user already exists
        }
        for (String user:verifiedUsers) {
            if (email.equals(user))
                return null; //user already exists
        }
        //Assigns the new user to the first available ID beginning with 0
        for (int i=0;i<100;i++) {
            if (newUsers[i] == null) {
                newUsers[i] = email; //set new user
                NewUserInfo newuser = new NewUserInfo();
                //Stores the users' new id and key pair into the return object newuser
                newuser.userID = i;
                newuser.privateKey = privatKey;
                newuser.publicKey = publiKey;
                userkeys[i] = newuser; //Stores the users' keys for later authentication 
                updateState();
                return newuser; //return newuserinfo
            }
        }

        return null; //Maximum number of users reached.
    }

    /**
     * Allows the user to register a new auction, allowing the item they have provided to be
     * listed for sale in our database. The user must be verified to perform this interaction.
     * 
     * @param userID The ID of the user making the request
     * @param item The item which the user wishes to list
     * @return The ID of the item which has been listed (-1 if an error has occurred)
     * @throws RemoteException if a remote exception occurs
     */
    public synchronized int newAuction(int userID, AuctionSaleItem item) throws RemoteException {
        if (verifiedUsers[userID] == null)
            return -1; //User not verified
        //Assigns the new item to the first available ID beginning with 0
        for (int i=0;i<50;i++) {
            if (auctions[i][0] == null) {
                auctions[i][0] = userID; //Stores the ID of the owner of the auction
                auctions[i][1] = item; //Stores the details of the item to be sold in the auction

                //Adds the item to the list of items that are currently on sale
                AuctionItem newItem = new AuctionItem();
                newItem.name = item.name;
                newItem.description = item.description;
                newItem.itemID = i;
                allitems[i] = newItem;
                updateState();
                return i; //returns the ID of the new item to the client
            }
        }
        return -1; //Maximum number of auctions reached (50)
    }

    /**
     * Returns a list consisting only of items which are currently on sale in the
     * auction house. A user does not have to be verified to perform this interaction.
     * 
     * @return the list of all items which are currently up for sale 
     * @throws RemoteException if a remote exception occurs
     */
    public AuctionItem[] listItems() throws RemoteException {
        AuctionItem[] items;
        ArrayList<AuctionItem> listedItems = new ArrayList<AuctionItem>(); //Creates a temporary list of items
        //Adds all current items into the list of items, without adding null indexes
        for (AuctionItem item : allitems)
            if (item != null)
                listedItems.add(item);

        items = listedItems.toArray(new AuctionItem[listedItems.size()]); //Converts the list into an output array containing no null values
        return items; //Returns the output array to the client
    }

    /**
     * Allows the user to close an existing auction and returns the results of the auction
     * to the requesting user, the auction must be owned by the user in order to close it.
     * The user must be verified to perform this interaction.
     * 
     * @param userID The user making the request to close the auction
     * @param itemID The ID of the item which is being auctioned
     * @return an object which contains the contact information of the winner alongside their bid.
     * @throws RemoteException if a remote exception occurs
     */
    public synchronized AuctionCloseInfo closeAuction(int userID, int itemID) throws RemoteException {
        if (verifiedUsers[userID] == null || allitems[itemID] == null)
            return null; //User not verified or item with ID itemID does not exist

        AuctionSaleItem item = (AuctionSaleItem)auctions[itemID][1]; //Pulls the original sale object from memory
        int reservePrice = item.reservePrice; //Identifies the reserve price
        AuctionCloseInfo info = new AuctionCloseInfo();
        int winnerPrice = 0;

        if (auctions[itemID][0] != null) { //Checks if the auction exists
            if ((int)auctions[itemID][0] == userID) { //Checks if the requesting user owns the auction
                //Prepares AuctionCloseInfo object with parameters for return 
                if (bids[itemID][0] !=null) { //if any bids have been placed on the item
                    int winnerID = (int) bids[itemID][0];
                    winnerPrice = (int) bids[itemID][1];
                    String winnerEmail = verifiedUsers[winnerID];
                    info.winningEmail = winnerEmail;
                    info.winningPrice = winnerPrice;
                }

                //Removes all references to this itemID from database
                allitems[itemID] = null;
                bids[itemID][0] = null;
                bids[itemID][1] = null;
                auctions[itemID][0] = null;
                auctions[itemID][1] = null;

                //Handles situation where reserve price is not met by the highest bid.
                    if (winnerPrice < reservePrice || bids[itemID][0] == null) {
                        info.winningEmail = null; //Removes the winner from the return
                        info.winningPrice = reservePrice; //Sets winning price to reserve (indicates reserve was not met)
                    }
                updateState();
                return info; // returns the initialised AuctionCloseInfo object
            }
        }
        return null; //Auction does not exist or request not made by the auction owner
    }

    /**
     * Allows a user to make bids on an item which is currently listed in the Auction house.
     * The user must specify which item they wish to bid on and what price they will pay,
     * the server will return false if the item does not exist or if their price does not
     * exceed the current highest bid. The user must be verified to perform this interaction.
     * 
     * @param userID The ID of the user making the request
     * @param itemID The ID of the item which the user is bidding on
     * @param price The bid that the user wishes to place on the item
     * @return true if bid is placed succesfully or false if any issue occurrs
     * @throws RemoteException if a remote exception occurs
     */
    public synchronized boolean bid(int userID, int itemID, int price) throws RemoteException {
        if (verifiedUsers[userID] == null || allitems[itemID] == null)
            return false; //User not verified or item with ID itemID does not exist

        if (bids[itemID][0] != null) { //If a current bid exists
            if ((int) bids[itemID][1] < price) { //If the new bid is highest than the current bid
                //Overwrite the current bid with the new bid
                bids[itemID][0] = userID;
                bids[itemID][1] = price;
                allitems[itemID].highestBid = price; //Updates the item listing to reflect the highest bid
                updateState();
                return true; //Bid placed successfully
            }
            else
                return false; //The new bid on the item is not higher than the current bid
        }
        //If there is no current bid for this item
        else {
            //Sets new bid info
            bids[itemID][0] = userID;
            bids[itemID][1] = price;
            allitems[itemID].highestBid = price; //Updates the item listing to reflect the highest bid
            updateState();
            return true; //Bid placed successfully
        }
    }

    /**
     * Lets any user query the details of a specific item ID which is currently on sale
     * in the Auction house, A user does not have to be verified to perform this interaction.
     * 
     * @param itemID The ID of the item which is being queried
     * @return the item currently on sale in the database
     * @throws RemoteException if a remote exception occurs
     */
    public AuctionItem getSpec(int itemID) throws RemoteException{
        return allitems[itemID];
    }   

    /**
     * Part of the verification process for each user. Allows a user to challenge the identity
     * of the server by requesting a signature of the string "auction", if the client can 
     * verify this signature using the servers' public key then it can authenticate the server.
     * 
     * @param userID The ID of the user which is challenging the server
     * @return the bytes of the signature generated by encrypting the string "auction" with the servers' private key
     * @throws RemoteException if a remote exception occurs
     */
    public synchronized byte[] challenge(int userID) throws RemoteException {
        if (newUsers[userID] == null)
            return null; //This user ID does not exist 
        byte[] sessionKey = null;
        PrivateKey originalKey = null;

        try {
            sessionKey = Files.readAllBytes(Paths.get("../keys/server_private.key"));
            originalKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(sessionKey));
            Signature sig = Signature.getInstance("SHA1WithRSA"); //initialises a signature using the SHA1WithRSA algorithm
            byte[] data = "auction".getBytes("UTF8"); //converts string "auction" into a byte array
            //Creates a signature of the byte array using the servers' private key
            sig.initSign(originalKey);
            sig.update(data);
            byte[] signatureBytes = sig.sign();

            verified[userID][0] = true; //Indicates that the user has performed this stage of verification
            authenticateUser(userID); //Attempts to verify the user
            updateState();
            return signatureBytes; //Returns the signature to the user
        }
        //Catches all exceptions that may occur
        catch (Exception e) {
            System.out.println("Exception:");
            e.printStackTrace();
        }
        
        return null; //If an error has occurred
    }
    
    /**
     * Part of the verification process for each user. Lets the user ask the server to verify them 
     * by providing the server with a signature of their email address encrypted with their private key.
     * The server can verify the sender by attempting to decrypt the data using the public key of the user
     * which corresponds to the ID which was provided as an arguement.
     * @param userID The ID of the user to authenticate
     * @param signature The signature of the email address of the user, encrypted with their private key
     * @return the verdict made by the server, true if the client is legitimate, false if they are not
     * @throws RemoteException if a remote exception occurs
     */
    public synchronized boolean authenticate(int userID, byte[] signature) throws RemoteException {
        byte[] userKeyBytes;
        if (userkeys[userID] != null) 
            userKeyBytes = userkeys[userID].publicKey; //Gets the public key of the user specified by userID
        else    
            return false; //This user ID does not exist
        try {
            PublicKey userKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(userKeyBytes)); //Converts the byte array back into a public key
            byte[] test = newUsers[userID].getBytes("UTF8"); //Finds the email address of the user specified by the ID
            //Creates a instance of signature which holds the public key and encoded email address of the user
            Signature sig = Signature.getInstance("SHA1WithRSA");
            sig.initVerify(userKey);
            sig.update(test);

            if (sig.verify(signature) == true) { //Attempts to decrypt the signature using the public key and compares it against the email address of the user
                verified[userID][1] = true; //Indicates that the user has performed this stage of verification
                authenticateUser(userID); //Attempts to verify the user
                updateState();
                return true; // The server was able to verify the identity of the client successfully
            }
        //Catches all exceptions that may occur
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
        return false; //The signature cannot be decrypted or the signed email is not the users' registered email
    }

    /**
     * Checks if a user has completed both challenge and authenticate, allowing them to be
     * stored inside a group of verified users which can now access all functions on the server
     * @param userID the user to be queried for verification
     */
    private void authenticateUser(int userID) {
        if (verified[userID][0] == true && verified[userID][1] == true)
            verifiedUsers[userID] = newUsers[userID];
    }

    /**
     * Generates a key pair to be used for asymmetric cryptography
     * @return a keypair generated using an RSA algorithm
     * @throws NoSuchAlgorithmException if the algorithm is not recognised
     */
    private KeyPair generateKey() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        KeyPair kp = keyPairGenerator.generateKeyPair();
        return kp;
    }
    /**
     * Stores a keypair for asymmetric cryptography into a .key file in a shared directory.
     * This allows the client to access the public key of the server for challenging its authenticity.
     * @param kp the keypair to be stored
     */
    private void storeKey(KeyPair kp) {
        try {
			FileOutputStream f = new FileOutputStream(new File("../keys/server_private.key"));
            PrivateKey privateKey = kp.getPrivate(); //Gets the private key from the keypair
            byte[] encodedkey = privateKey.getEncoded(); //Encodes the key into a raw byte array
            //Writes the encoded key to server_private.key
			f.write(encodedkey);
			f.close();

            FileOutputStream w = new FileOutputStream(new File("../keys/server_public.key"));
            PublicKey publicKey = kp.getPublic(); //Gets the public key from the keypair
            encodedkey = publicKey.getEncoded(); //Encodes the key into a raw byte array
            //Writes the encoded key to server_public.key
			w.write(encodedkey);
			w.close();

        //Catches all exceptions that may occur
		} catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }

    /**
     * Simpy returns true if the server is able to respond
     */
    public Boolean checkAlive() throws RemoteException {
        return true;
    }

    /**
     * Pushes all data stored in the current replica to another replica
     */
    public void changeState(AuctionItem[] allitems, String[] newUsers, String[] verifiedUsers, NewUserInfo[] userkeys, Object[][] auctions, Object[][] bids, boolean[][] verified) throws RemoteException {
        this.allitems = allitems;
        this.newUsers = newUsers;
        this.verifiedUsers = verifiedUsers;
        this.userkeys = userkeys;
        this.auctions = auctions;
        this.bids = bids;
        this.verified = verified;
    }

    /**
     * Cycles through all available replicas, pushing the current state of this replica onto them.
     */
    private void updateState() {
        String[] replicas = getReplicas();
        for (String replica:replicas) {
            if (replica != null) {
                try {
                    String name = replica;
                    Registry registry = LocateRegistry.getRegistry("localhost");
                    Server server = (Server) registry.lookup(name);
                    server.changeState(allitems, newUsers, verifiedUsers, userkeys, auctions, bids, verified);
                }
                catch (Exception e) {}
            }
        }
    }

    /**
     * creates a list of all rmi services running on the local port, removing FrontEnd from the list so that it 
     * only contains a list of registered replicas.
     * @return array of registered Replicas
     */
    private String[] getReplicas() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            String[] names = registry.list();
            for (int i=0;i<names.length;i++) 
                if (names[i].equals("FrontEnd"))
                    names[i] = null;
            return names;
        }
        catch (Exception e) {
            System.err.println("Exception: cannot find any replicas");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Allows the current replica to ask the primary replica to copy it's state to this replica
     * upon joining the network.
     */
    public void requestUpdate(int id) throws RemoteException {
        try {
            String name = "Replica"+id;
            Registry registry = LocateRegistry.getRegistry("localhost");
            Server server = (Server) registry.lookup(name);
            server.changeState(allitems, newUsers, verifiedUsers, userkeys, auctions, bids, verified);
        }
        catch (Exception e) {}
    }
        public static void main(String[] args) {
            int id = Integer.parseInt(args[0]);
            try {
                //Advtertises this replca as an RMI service with a unique ID
                Replica s = new Replica();
                String name = "Replica"+id;
                Server stub = (Server) UnicastRemoteObject.exportObject(s, 0);
                Registry registry = LocateRegistry.getRegistry();
                registry.rebind(name, stub);
                System.out.println("Server ready");

                //Connects to the FrontEnd to receive the id of the current primary replica
                name = "FrontEnd";
                Registry reqRegistry = LocateRegistry.getRegistry("localhost");
                Auction frontend = (Auction) reqRegistry.lookup(name);
                int primaryID = frontend.getPrimaryReplicaID();

                //If this replica is not the priamry replica then it will ask the primary replica to share its data 
                if (primaryID != id) {
                    name = "Replica"+primaryID;
                    Server server = (Server) reqRegistry.lookup(name);
                    server.requestUpdate(id);
                }

               } catch (Exception e) {
                System.err.println("Exception:");
                e.printStackTrace();
               }
        }
    }
