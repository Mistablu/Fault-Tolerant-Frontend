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

                if (cmd.equals("getspec")) {
                  int n = Integer.parseInt(args[1]);
                  AuctionItem result = server.getSpec(n);
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
                else if (cmd.equals("newuser")) {
                  String n = (String)args[1];
                  email = n;
                  userinfo = server.newUser(n);
                  System.out.println("User created, your ID is: "+userinfo.userID);
                }
                else if (cmd.equals("listitems")) {
                  AuctionItem[] allitems = server.listItems();
                  for (AuctionItem item:allitems)
                    System.out.println("Item: "+item.name+", Current Price: £"+item.highestBid);
                }
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
                else if (cmd.equals("challenge")) {
                  int n = Integer.parseInt(args[1]);
                  byte[] signature = server.challenge(n);
                  PublicKey serverKey = readKey();
                  byte[] test = "auction".getBytes("UTF8");
                  Signature sig = Signature.getInstance("SHA1WithRSA");
                  sig.initVerify(serverKey);
                  sig.update(test);
                  System.out.println(sig.verify(signature));
                }
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
