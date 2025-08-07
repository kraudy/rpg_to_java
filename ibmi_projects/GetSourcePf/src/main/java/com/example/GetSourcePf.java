package com.example;

import com.ibm.as400.access.*;
import java.sql.*;
import java.io.*;
import java.beans.PropertyVetoException;

public class GetSourcePf {
  public static void main( String... args ){
    BufferedReader inputStream = new BufferedReader(new InputStreamReader(System.in),1);
    AS400 system = new AS400();
    Connection conn = null;
    Statement memberStmt = null;
    Statement sourceStmt = null;
    ResultSet rsMembers = null;

    //TODO: Add the source pf as another dir
    // Base dir
    String ifsOutputDir = null; 
    String library = null;

    try{
      // Get current user
      User user = new User(system, system.getUserId());
      String homeDir = user.getHomeDirectory();

      if (homeDir == null) {
        System.out.println("The current user has no home dir");
        return;
      }
      // Construct exporation directory
      ifsOutputDir = homeDir + "/" + "sources";

      System.out.println("Source files will be migrated to: " + ifsOutputDir);

      // Ensure the IFS output directory exists
      File outputDir = new File(ifsOutputDir);
      if (!outputDir.exists()) {
          System.out.println("Creating dir: " + ifsOutputDir);
          outputDir.mkdirs(); // Create directory if it doesn't exist
      }

      System.out.println("Specify the name of a library or press enter to search in the current library: ");
      library = inputStream.readLine().trim().toUpperCase();

      if (library == "") {
        library = user.getCurrentLibraryName();
        if (library.equals("*CRTDFT")){
          System.out.println("The user does not have a current library");
          return;
        }
      }
      

      // Establish JDBC connection using AS400JDBCConnection
      AS400JDBCDataSource dataSource = new AS400JDBCDataSource(system);
      conn = dataSource.getConnection();
      conn.setAutoCommit(true); // We don't want transaction control

      // Create separate Statement objects
      memberStmt = conn.createStatement();


       // Query SYSPARTITIONSTAT to get all members of the source file
      String sql = "SELECT SYSTEM_TABLE_MEMBER, SOURCE_TYPE " +
                  "FROM QSYS2.SYSPARTITIONSTAT " +
                  "WHERE SYSTEM_TABLE_SCHEMA = '" + library + "' " +
                  "AND SYSTEM_TABLE_NAME = 'QRPGLESRC'";
      rsMembers = memberStmt.executeQuery(sql);

      System.out.println("Specify the name of a source Pf or press enter to migrate all the source Pf: ");
      
      sourceStmt = conn.createStatement();
      iterateThroughMembers(rsMembers, sourceStmt, ifsOutputDir, system);

      System.out.println("Specify the name of a source member or press enter to migrate all the source members: ");

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

  //TODO: Remove sourceStmt and pass the coneection to create the statement here or define it as global
  private static void iterateThroughMembers(ResultSet rsMembers, Statement sourceStmt, String ifsOutputDir, AS400 system) 
        throws SQLException, IOException, AS400SecurityException, 
                ErrorCompletingRequestException, InterruptedException, PropertyVetoException{
    // Iterate through each member
    while (rsMembers.next()) {
      String memberName = rsMembers.getString("SYSTEM_TABLE_MEMBER").trim();
      String sourceType = rsMembers.getString("SOURCE_TYPE").trim();
      System.out.println("\n=== Processing Member: " + memberName + " ===");

      //useAliases(sourceStmt, memberName, ifsOutputDir);
      useCommand("ROBKRAUDY2", "QRPGLESRC", memberName, sourceType, system, ifsOutputDir);
      
    }
  }

  private static void useCommand(String library, String sourcePf, String memberName, String sourceType, 
      AS400 system, String ifsOutputDir) 
      throws IOException, AS400SecurityException, ErrorCompletingRequestException, InterruptedException, PropertyVetoException{

    String ccsid = "1208";

    String commandStr = "CPYTOSTMF FROMMBR('/QSYS.lib/" 
                   + library + ".lib/" 
                   + sourcePf + ".file/" 
                   + memberName + ".mbr') "
                   + "TOSTMF('" 
                   + ifsOutputDir + "/" 
                   + memberName + "." + sourceType + "') "
                   + "STMFOPT(*REPLACE) STMFCCSID(" + ccsid 
                   + ") ENDLINFMT(*LF)";

    CommandCall cmd = new CommandCall(system);
    if(!cmd.run(commandStr)){
      System.out.println("Could not migrate " + memberName + " ***");
      return;
    }

    System.out.println("Migrateed " + memberName + " successfully");

  }

  /* 
    To get the source out of the PF we actually don't need to do it line by line.
    We could just do this
    

    We only need to get the source out of Source PFs for the ones that are not already on the git repo.
    So we don't really care about much else other thant the code.
  */

  private static void useAliases(Statement sourceStmt, String memberName, String ifsOutputDir)
      throws SQLException, IOException{
    // Create alias for the current member
    String aliasSql = "CREATE OR REPLACE ALIAS QTEMP.SourceCode " +
                    "FOR ROBKRAUDY2.QRPGLESRC(" + memberName + ")";

    sourceStmt.execute(aliasSql);

    // Get source code for the current member
    ResultSet rsSource = sourceStmt.executeQuery("SELECT SRCDTA FROM QTEMP.SourceCode");

    // Print with regex
    //printSourceWithRegex(rsSource);
    // Print with string replacemente
    //printSourceRemoveLastChar(rsSource);

    // Write source code to a file in the IFS
    String outputFilePath = "/" + ifsOutputDir + "/" + memberName + ".rpgle";
    writeSourceToIFS(rsSource, outputFilePath);

    // Close source ResultSet
    rsSource.close();

    // Drop the alias
    sourceStmt.execute("DROP ALIAS QTEMP.SourceCode");
  }

   // Method to write source code to a file in the IFS
  private static void writeSourceToIFS(ResultSet rsSource, String outputFilePath) throws SQLException, IOException {
    //TODO: Check if file exists
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
      while (rsSource.next()) {
        String sourceData = rsSource.getString("SRCDTA");
        if (sourceData != null) {
          if (sourceData.endsWith("\n")) {
            sourceData = sourceData.substring(0, sourceData.length() - 1);
          }
        //TODO: This adds an extra tab for some reason. Writing line by line like this is tedious.
          writer.write(sourceData);
          writer.newLine(); // Add newline after each source line
        }
      }
      System.out.println("Wrote source to: " + outputFilePath);
    }
  }

  /* This is faster than the regex but may ignore lines ended with more than one \n */
  private static void printSourceRemoveLastChar(ResultSet rsSource) throws SQLException{
    while (rsSource.next()) {
      String sourceData = rsSource.getString("SRCDTA");
      if (sourceData != null) {
        if (sourceData.endsWith("\n")) {
          sourceData = sourceData.substring(0, sourceData.length() - 1);
        }
        System.out.println(sourceData);
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
