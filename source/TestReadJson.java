import com.ibm.as400.access.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;


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

      /* 
      So, we read the entire file into a StringBuilder, generate the json object
      and extract the json array
      */
      StringBuilder jsonContent = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
          jsonContent.append(line);
      }
      reader.close();

      // Parse JSON as JSONObject
      JSONObject jsonObject = new JSONObject(jsonContent.toString());
      // Extract the "employees" array
      JSONArray jsonArray = jsonObject.getJSONArray("employees");

      // Example: Print the array or process it
      System.out.println("Parsed JSON Array: " + jsonArray.toString());

      for (Object obj : jsonArray) {
        JSONObject employee = (JSONObject) obj;
        System.out.println("Id: " + employee.getInt("id"));
        System.out.println("First name: " + employee.getString("firstName"));
        System.out.println("Last name: " + employee.getString("lastName"));
        System.out.println("Department: " + employee.getString("department"));
        System.out.println("Salary: " + employee.getInt("salary"));
      }

      // Additional file checks
      System.out.println("File CCSID: " + file.getCCSID());
      System.out.println("Free Space: " + (file.getFreeSpace(sys)/1024) + " MG");

    } catch (Exception e2){
      e2.printStackTrace();
    }

    // Check if the file exists exists()
    // Get CCSID => getCCSID()
    // Returns storage space available to the user. => getFreeSpace(AS400 system)

  }
}
