import com.ibm.as400.access.*;
import java.util.Date;

public class TestIFSFile {
  public static void main(String... args){
    AS400 sys = new AS400();
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/hello.txt");

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

    System.out.println("Dir: " + directory + " Name: " + name + " Length: " + length + " Date: " + date);
    
  }
}
