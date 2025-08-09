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
    //Statemente showSourcePf = null;

    ResultSet rsshowSourcePf = null;

    //TODO: Add the source pf as another dir
    // Base dir
    String ifsOutputDir = null; 
    String library = null;
    String sourcePf = null;
    // TODO: Fix this to show the actual name instead of LOCALHOST
    String systemName = system.getSystemName().toUpperCase();

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

      // Validates if library exists
      if (!conn.createStatement().executeQuery(
          "Select 1 As Exists " +
          "From QSYS2.SYSPARTITIONSTAT " + 
          "Where SYSTEM_TABLE_SCHEMA = '" + library + "' limit 1 ")
          .next()) {
        System.out.println("Library does not exists in your system");
        return;
      }

      //TODO: Move this to a method

      //TODO: Validate if this only shows source pf
      rsshowSourcePf = conn.createStatement().executeQuery(
        "Select SYSTEM_TABLE_NAME As SourcePf, Count(*) As Members " +
        "From QSYS2.SYSPARTITIONSTAT " +
        "Where SYSTEM_TABLE_SCHEMA = '" + library + "' " +
        "And Trim(SOURCE_TYPE) <> ''" +
        "Group by SYSTEM_TABLE_NAME"
      );

      System.out.println("\nList of available Source PFs in the library: ");
      System.out.println("    SourcePf      | Number of Members"); // Header with aligned spacing
      System.out.println("    ------------- | -----------------"); // Separator line for clarity
      while(rsshowSourcePf.next()){
        String rsSourcePf = rsshowSourcePf.getString("SourcePf").trim();
        String membersCount = rsshowSourcePf.getString("Members").trim();
        System.out.println(String.format("    %-13s | %17s", rsSourcePf, membersCount));
      }
      
      System.out.println("\nSpecify the name of a source PF or press 'Enter' to migrate all the source PFs: ");
      sourcePf = inputStream.readLine().trim().toUpperCase();

      if (sourcePf != "") {
        // Validates if SourcePF exists
        if (!conn.createStatement().executeQuery(
            "Select 1 As Exist From QSYS2.SYSPARTITIONSTAT " + 
            "Where SYSTEM_TABLE_SCHEMA = '" + library + "' " + 
            "AND SYSTEM_TABLE_NAME = '" + sourcePf + "' limit 1")
            .next()) {
          System.out.println("Source PF does not exists in library " + library);
          return;
        }
      }

      iterateThroughMembers(conn, library, ifsOutputDir, system);

      System.out.println("Specify the name of a source member or press enter to migrate all the source members: ");

    } catch (Exception e){
      e.printStackTrace();
    } finally {
      try {
        // Clean up resources
        if (memberStmt != null) {
            memberStmt.close();
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

  private static void iterateThroughMembers(Connection conn, String library, String ifsOutputDir, AS400 system) 
        throws SQLException, IOException, AS400SecurityException, 
                ErrorCompletingRequestException, InterruptedException, PropertyVetoException{
    
    ResultSet rsMembers = conn.createStatement().executeQuery(
      "SELECT SYSTEM_TABLE_MEMBER, SOURCE_TYPE " +
      "FROM QSYS2.SYSPARTITIONSTAT " +
      "WHERE SYSTEM_TABLE_SCHEMA = '" + library + "' " +
      "AND SYSTEM_TABLE_NAME = 'QRPGLESRC'"
    );
    // Iterate through each member
    while (rsMembers.next()) {
      String memberName = rsMembers.getString("SYSTEM_TABLE_MEMBER").trim();
      String sourceType = rsMembers.getString("SOURCE_TYPE").trim();
      System.out.println("\n=== Processing Member: " + memberName + " ===");

      //useAliases(conn, memberName, ifsOutputDir);
      useCommand("ROBKRAUDY2", "QRPGLESRC", memberName, sourceType, system, ifsOutputDir);      
    }

    rsMembers.close();

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

  private static void useAliases(Connection conn, String memberName, String ifsOutputDir)
      throws SQLException, IOException{
    // Create alias for the current member
    conn.createStatement().execute(
      "CREATE OR REPLACE ALIAS QTEMP.SourceCode " +
      "FOR ROBKRAUDY2.QRPGLESRC(" + memberName + ")"
    );

    // Get source code for the current member
    ResultSet rsSource = conn.createStatement().executeQuery(
      "SELECT SRCDTA FROM QTEMP.SourceCode"
    );

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
    conn.createStatement().execute(
      "DROP ALIAS QTEMP.SourceCode"
    );
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
