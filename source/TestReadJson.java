import com.ibm.as400.access.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;


public class TestReadJson {
  public static void main(String... args){
    AS400 sys = new AS400();
    //TODO: Add listener
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");

    try {
      if(!file.exists()){
        System.out.println("File does not exists: " + file.getPath());
        return;
      }
  
      if(!file.canRead()){
        System.out.println("Can't read from file: " + file.getPath());
      }
    } catch(Exception e){
      e.printStackTrace();
    }    

    try{
      IFSFileInputStream fis = new IFSFileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

      // Read the entire file into a StringBuilder
      StringBuilder jsonContent = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
          jsonContent.append(line);
      }
      
    } catch (Exception e2){
      e2.printStackTrace();
    }

    // Check if the file exists exists()
    // Get CCSID => getCCSID()
    // Returns storage space available to the user. => getFreeSpace(AS400 system)

  }
}
