import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;

public class Client{
  private static PublicKey readKey() {
    byte[] sessionKey = null;
    PublicKey originalKey = null;
    try {
      // Read objects
      sessionKey = Files.readAllBytes(Paths.get("../keys/server_public.key"));
      originalKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(sessionKey));

    } catch (Exception e) {
      System.out.println("Exception");
      e.printStackTrace();
    }
    return originalKey;
  }
     public static void main(String[] args) {
      NewUserInfo userinfo = null;
      String email = null;
        while (true) {
          String input = System.console().readLine();
          args = input.split(" ");
         String cmd = (String)(args[0]);
         try {
               String name = "FrontEnd";
               Registry registry = LocateRegistry.getRegistry("localhost");
               Auction server = (Auction) registry.lookup(name);
                //gets the listing of an item based on its id
                if (cmd.equals("getspec")) {
                  int itemid = Integer.parseInt(args[1]);
                  AuctionItem result = server.getSpec(itemid);
                  if (result == null)
                    System.out.println("Error, ID not Found.");
                  else {
                    System.out.println("Found item "+result.name);
                  }
                }
                else if (cmd.equals("newauction")) {
                  int n = Integer.parseInt(args[1]);
                  String itemName = (String)args[2];
                  int reservePrice = Integer.parseInt(args[3]);
                  AuctionSaleItem saleitem = new AuctionSaleItem();
                  saleitem.name = itemName;
                  saleitem.reservePrice = reservePrice;
                  int result = server.newAuction(n, saleitem);
                  System.out.println(result);
                }
                //creates a new user with the given email, a userid will be generated and returned to you
                else if (cmd.equals("newuser")) {
                  String n = (String)args[1];
                  email = n;
                  userinfo = server.newUser(n);
                  System.out.println("User created, your ID is: "+userinfo.userID);
                }
                //lists all items and their current highest bid amount
                else if (cmd.equals("listitems")) {
                  AuctionItem[] allitems = server.listItems();
                  for (AuctionItem item:allitems)
                    System.out.println("Item: "+item.name+", Current Price: £"+item.highestBid);
                }
                //closes an auction
                else if (cmd.equals("closeauction")) {
                  int n = Integer.parseInt(args[1]);
                  int m = Integer.parseInt(args[2]);
                  AuctionCloseInfo closeInfo = server.closeAuction(n, m);
                  System.out.println(closeInfo.winningEmail+" £"+closeInfo.winningPrice);
                }
                else if (cmd.equals("bid")) {
                  int n = Integer.parseInt(args[1]);
                  int m = Integer.parseInt(args[2]);
                  int b = Integer.parseInt(args[3]);
                  boolean result = server.bid(n, m, b);
                  System.out.println(result);
                }
                //challenges the authenticity of the server
                else if (cmd.equals("challenge")) {
                  int n = Integer.parseInt(args[1]);
                  byte[] signature = server.challenge(n);
                  PublicKey serverKey = readKey();
                  System.out.println("im trying");
                  byte[] test = "auction".getBytes("UTF8");
                  Signature sig = Signature.getInstance("SHA1WithRSA");
                  sig.initVerify(serverKey);
                  sig.update(test);
                  System.out.println(sig.verify(signature));
                }
                //authenticates the credentials of the user
                else if (cmd.equals("authenticate")) {
                  int n = Integer.parseInt(args[1]);
                  Signature sig = Signature.getInstance("SHA1WithRSA");
                  byte[] data = email.getBytes("UTF8");
                  PrivateKey userKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(userinfo.privateKey));
                  sig.initSign(userKey);
                  sig.update(data);
                  byte[] signatureBytes = sig.sign();
                  boolean result = server.authenticate(n, signatureBytes);
                  System.out.println(result);
                }
                //returns the id of the current primary replica (run another command to update this value)
                else if (cmd.equals("getprimary")) {
                  int id = server.getPrimaryReplicaID();
                  System.out.println("ID is "+id);
                }
                else  
                  System.out.println("Input string not recognised.");
              }
              catch (Exception e) {
               System.err.println("Exception:");
               e.printStackTrace();
               }
      }
    }
}
