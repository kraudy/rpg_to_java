import com.ibm.as400.access.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class JacksonJsonParser {
  public static void main(String... args){
    AS400 sys = new AS400();
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");

    Connection conn = null;

    try{
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
      
      // Initialize ObjectMapper for JSON parsing
      ObjectMapper mapper = new ObjectMapper();
      IFSFileInputStream fis = new IFSFileInputStream(file);

      // Parse JSON file
      JsonNode rootNode = mapper.readTree(fis);
      JsonNode employeesNode = rootNode.get("employees");

      /* 
       * This uses a mix of org.json and jackson packages which i don't really like.
       * Kinda messy
      */

      // Check if employeesNode is an array
      if (employeesNode != null && employeesNode.isArray()) {
        for (JsonNode employeeNode : (ArrayNode) employeesNode) {
            // Convert JsonNode to JSONObject if needed
            JSONObject employee = new JSONObject(employeeNode.toString());

            // Write to log file with timestamp
            String logEntry = new Date() + " - Processed: " + employee.getString("firstName") + " " + employee.getString("lastName");
            System.out.println(logEntry);

            // Optionally, log to database
            pstmt.setString(1, logEntry);
            pstmt.executeUpdate();
        }
      } else {
          System.out.println("No 'employees' array found in JSON");
      }

      // Close connection
      pstmt.close();
      conn.close();

    }catch (Exception e){
      e.printStackTrace();
    } finally {
      // Close resources in reverse order
      try {
          if (conn != null) conn.close();
          if (sys != null) sys.disconnectAllServices();
      } catch (Exception e) {
          e.printStackTrace();
      }
  }

  }
}
