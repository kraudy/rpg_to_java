package com.example;

import com.ibm.as400.access.*;
import java.sql.*;

public class GetSourcePf {
  public static void main( String[] args ){
    AS400 system = new AS400();
    Connection conn = null;
    Statement memberStmt = null;
    Statement sourceStmt = null;
    ResultSet rsMembers = null;

    try{
      //CommandCall cmd = new CommandCall(system);

      // Establish JDBC connection using AS400JDBCConnection
      AS400JDBCDataSource dataSource = new AS400JDBCDataSource(system);
      conn = dataSource.getConnection();
      conn.setAutoCommit(true); // We don't want transaction control

      // Create separate Statement objects
      memberStmt = conn.createStatement();
      sourceStmt = conn.createStatement();


       // Query SYSPARTITIONSTAT to get all members of the source file
      String sql = "SELECT SYSTEM_TABLE_MEMBER " +
                  "FROM QSYS2.SYSPARTITIONSTAT " +
                  "WHERE SYSTEM_TABLE_SCHEMA = 'ROBKRAUDY2' " +
                  "AND SYSTEM_TABLE_NAME = 'QRPGLESRC'";
      rsMembers = memberStmt.executeQuery(sql);
      
      // Iterate through each member
      while (rsMembers.next()) {
        String memberName = rsMembers.getString("SYSTEM_TABLE_MEMBER").trim();
        System.out.println("\n=== Processing Member: " + memberName + " ===");

        // Create alias for the current member
        String aliasSql = "CREATE OR REPLACE ALIAS QTEMP.SourceCode " +
                        "FOR ROBKRAUDY2.QRPGLESRC(" + memberName + ")";
        sourceStmt.execute(aliasSql);

        // Get source code for the current member
        ResultSet rsSource = sourceStmt.executeQuery("SELECT SRCDTA FROM QTEMP.SourceCode");

        // Process and print source lines
        //printSourceWithRegex(rsSource);
        printSourceRemoveLastChar(rsSource);

        // Close source ResultSet
        rsSource.close();

        // Drop the alias
        sourceStmt.execute("DROP ALIAS QTEMP.SourceCode");
      }

    } catch (Exception e){
      e.printStackTrace();
    } finally {
      try {
        // Clean up resources
        if (rsMembers != null) {
            rsMembers.close();
        }
        if (memberStmt != null) {
            memberStmt.close();
        }
        if (sourceStmt != null) {
            sourceStmt.close();
        }
        if (conn != null) {
            conn.close();
        }
        if (system != null) {
            system.disconnectAllServices();
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
    }
  }

  /* Regex can be slow for large code bases */
  private static void printSourceWithRegex(ResultSet rsSource) throws SQLException{
    while (rsSource.next()) {
      String sourceData = rsSource.getString("SRCDTA");
      if (sourceData != null) {
        //sourceData = sourceData.trim(); // Remove trailing and leading whitespace
        sourceData = sourceData.replaceAll("\\n$", "");
        //if (!sourceData.isEmpty()) { // Skip empty lines
        System.out.println(sourceData);
        //}
      }
    }
  }
  
  

}
