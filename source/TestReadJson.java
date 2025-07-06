import com.ibm.as400.access.*;
import java.util.Date;

public class TestReadJson {
  public static void main(String... args){
    AS400 sys = new AS400();
    //TODO: Add listener
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");

    String directory = file.getParent();
    String name = file.getName();
    long length = 0;
    Date date = null;
    try {
      length = file.length();
      date = new Date(file.lastModified());
    } catch (Exception e){
      e.printStackTrace();
    }

    // Check if the file exists exists()
    // Get CCSID => getCCSID()
    // Returns storage space available to the user. => getFreeSpace(AS400 system)

    // Check if it can be read
    if(!file.canRead()){
      System.out.println("Can't read from file");
    }
    

    System.out.println("Dir: " + directory + " Name: " + name + " Length: " + length + " Date: " + date);
    
  }
}
