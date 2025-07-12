package com.example;

import com.ibm.as400.access.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PureJacksonParser {
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
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(fis);

      // Access the "employees" array
      JsonNode employees = rootNode.get("employees");
      if (employees != null && employees.isArray()) {
        for (JsonNode employee : employees) {
            String firstName = employee.get("firstName").asText();
            String lastName = employee.get("lastName").asText();
            String logEntry = new Date() + " JsonParser - Processed: " + firstName + " " + lastName;

            System.out.println(logEntry);
            // Log to database
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
