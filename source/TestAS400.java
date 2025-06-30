import com.ibm.as400.access.*; 

public class TestAS400 {
  public static void main(String... args){
    try{
      AS400 system = new AS400();

      System.out.println(system.getVersion() + "." + system.getRelease());
      System.out.println(system.getSystemName());
      System.out.println(system.getUserId());

      System.out.println("Service is connected: " + system.isConnected());
      System.out.println("Is connection alive?: " + system.isConnectionAlive());
      

    } catch(Exception e){
      e.printStackTrace();
    }

    System.exit(0);
  }
}
