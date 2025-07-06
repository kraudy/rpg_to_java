import com.ibm.as400.access.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;


public class TestReadJson {
  public static void main(String... args){
    AS400 sys = new AS400();
    //TODO: Add listener
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");
    IFSFile logFile = new IFSFile(sys, "/home/ROBKRAUDY/log.txt"); // Log file path

    Connection conn = null;

    try {
      if(!file.exists()){
        System.out.println("File does not exists: " + file.getPath());
        return;
      }
  
      if(!file.canRead()){
        System.out.println("Can't read from file: " + file.getPath());
      }

      // Establish JDBC connection using AS400JDBCConnection
      AS400JDBCDataSource dataSource = new AS400JDBCDataSource(sys);
      conn = dataSource.getConnection();
      conn.setAutoCommit(true); // We don't want transaction control

      // Prepare SQL statement for inserting logs
      String sql = "INSERT INTO ROBKRAUDY2.NOTIF_LOG (LOG_TIMESTAMP, LOG_MESSAGE) VALUES (CURRENT_TIMESTAMP, ?)";
      PreparedStatement pstmt = conn.prepareStatement(sql);
      
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

      // Open log file for writing (append mode)
      IFSFileOutputStream fos = new IFSFileOutputStream(logFile); // creates or replace
      PrintWriter logWriter = new PrintWriter(fos);
 
            // Example: Print the array or process it
      System.out.println("Parsed JSON Array: " + jsonArray.toString());

      for (Object obj : jsonArray) {
        JSONObject employee = (JSONObject) obj;
        System.out.println("Id: " + employee.getInt("id"));
        System.out.println("First name: " + employee.getString("firstName"));
        System.out.println("Last name: " + employee.getString("lastName"));
        System.out.println("Department: " + employee.getString("department"));
        System.out.println("Salary: " + employee.getInt("salary"));

        // Write to log file with timestamp
        String logEntry = new Date() + " - Processed: " + employee.getString("firstName") + " " + employee.getString("lastName");
        logWriter.println(logEntry);

        // Insert into database
        pstmt.setString(1, logEntry);
        pstmt.executeUpdate();
      }
      // Close log file
      logWriter.close();
      fos.close();

      // Close connection
      pstmt.close();
      conn.close();

      // Additional file checks
      System.out.println("File CCSID: " + file.getCCSID());
      System.out.println("Free Space: " + (file.getFreeSpace(sys)/1024) + " MG");

    } catch (Exception e){
      e.printStackTrace();
    } finally {
      try {
        conn.close();
      } catch (Exception closeEx) {
        closeEx.printStackTrace();
      }
    }

    // Check if the file exists exists()
    // Get CCSID => getCCSID()
    // Returns storage space available to the user. => getFreeSpace(AS400 system)

  }
}
