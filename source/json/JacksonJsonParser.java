import com.ibm.as400.access.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class JacksonJsonParser {
  public static void main(String... args){
    AS400 sys = new AS400();
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");
    IFSFile logFile = new IFSFile(sys, "/home/ROBKRAUDY/log.txt"); // Log file path

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
      
      IFSFileInputStream fis = new IFSFileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

      /*  
        Start json parsing, this looks cleaner than using the string build to load the whole
        fiel into memory. Keep in mind that loading the whole file may still be necessary if other
        processes may affect it.
      */
      JsonFactory factory = new JsonFactory();
      JsonParser parser = factory.createParser(fis);
      while (parser.nextToken() != JsonToken.END_ARRAY) {
          if (parser.getCurrentName() != null && parser.getCurrentName().equals("employees")) {
              parser.nextToken(); // Move to array start
              while (parser.nextToken() != JsonToken.END_ARRAY) {
                  // Process each employee object
                  JSONObject employee = new JSONObject(parser.readValueAsTree().toString());
                  // Write to PDF, log, etc.
              }
          }
      }
      parser.close();

    }catch (Exception e){
      e.printStackTrace();
    }

  }
}
