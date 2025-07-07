package json2;

import com.ibm.as400.access.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class JacksonFactoryParser {
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
      IFSFileInputStream fis = new IFSFileInputStream(file);

      JsonFactory factory = new JsonFactory();
      JsonParser parser = factory.createParser(fis);
      while (parser.nextToken() != JsonToken.END_ARRAY) {
          if (parser.currentName() != null && parser.currentName().equals("employees")) {
              parser.nextToken(); // Move to array start
              while (parser.nextToken() != JsonToken.END_ARRAY) {
                  // Process each employee object
                  JSONObject employee = new JSONObject(parser.readValueAsTree().toString());
                  String logEntry = new Date() + " JsonParser - Processed: " + employee.getString("firstName") + " " + employee.getString("lastName");

                  // Optionally, log to database
                  pstmt.setString(1, logEntry);
                  pstmt.executeUpdate();
              }
          }
      }
      parser.close();

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
