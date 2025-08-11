package com.example;

import com.ibm.as400.access.*;
import java.sql.*;
import java.io.*;
import java.beans.PropertyVetoException;

public class GetSourcePf {
  private static final String CCSID = "1208";
   private static String ifsOutputDir = "";
   private static int totalMembersMigrated = 0;

  public static void main( String... args ){
    BufferedReader inputStream = new BufferedReader(new InputStreamReader(System.in),1);
    AS400 system = new AS400();
    Connection conn = null;
    Statement memberStmt = null;

    ResultSet rsshowSourcePf = null;

    // TODO: Fix this to show the actual name instead of LOCALHOST
    String systemName = system.getSystemName().toUpperCase();

    try{
      // Get current user
      User user = new User(system, system.getUserId());
      String homeDir = user.getHomeDirectory();
      String sourceDir = "sources";

      //TODO: Move this to the start.
      // Establish JDBC connection
      AS400JDBCDataSource dataSource = new AS400JDBCDataSource(system);
      conn = dataSource.getConnection();
      conn.setAutoCommit(true); // We don't want transaction control

      if (homeDir == null) {
        System.out.println("The current user has no home dir");
        return;
      }
      // Construct default exportation directory
      ifsOutputDir = homeDir + "/" + sourceDir;

      System.out.println("Specify the source dir destination or press 'Enter' to use: " + ifsOutputDir);
      sourceDir = inputStream.readLine().trim();

      if (!sourceDir.isEmpty()) {
        ifsOutputDir = homeDir + "/" + sourceDir;
      }

      // Create root export dir
      createDir(ifsOutputDir);
      
      System.out.println("Source files will be migrated to dir: " + ifsOutputDir);

      //TODO: Refacto this
      String library = "";
      while (library.isEmpty()) {
        System.out.println("\nSpecify the name of a library or press enter to search for Source PFs in the current library: " + user.getCurrentLibraryName());
        library = inputStream.readLine().trim().toUpperCase();

        if (library.isEmpty()) {
          library = user.getCurrentLibraryName();
          if (library.equals("*CRTDFT")){
            System.out.println("The user does not have a current library");
            library = "";
            continue;
          }
          break;
        }
        library = getLibrary(conn, library);
      }  

      // Add library to the export path
      ifsOutputDir = ifsOutputDir + "/" + library;
      createDir(ifsOutputDir);

      // Show list of Source PFs
      showSourcePfs(conn, library);

      // Choose source pf
      ResultSet rsSourcePFs = null;
      String sourcePf = null;
      while (rsSourcePFs == null) {
        System.out.println("\nSpecify the name of a source PF or press 'Enter' to migrate all the source PFs in library: " + library + " to dir: " + ifsOutputDir);
        sourcePf = inputStream.readLine().trim().toUpperCase();

        rsSourcePFs = getSourcePfs(conn, sourcePf, library);
      }

      //TODO: Add member selection?
      //System.out.println("Specify the name of a source member or press enter to migrate all the source members: ");

      // Migration cycle
      long startTime = System.nanoTime();
      while(rsSourcePFs.next()){
        library = rsSourcePFs.getString("Library").trim();
        sourcePf = rsSourcePFs.getString("SourcePf").trim();

        System.out.println("\n\nMigrating Source PF: " + sourcePf + " in library: " + library);
        iterateThroughMembers(conn, library, sourcePf, ifsOutputDir + '/' + sourcePf, system);
      }

      // Output results
      System.out.println("\nMigration completed.");
      System.out.println("Total members migrated: " + totalMembersMigrated);
      System.out.printf("Total time taken: %.2f seconds%n", (System.nanoTime() - startTime) / 1_000_000_000.0);

      rsSourcePFs.close();

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

  private static void iterateThroughMembers(Connection conn, String library, String sourcePf, String ifsOutputDir, AS400 system) 
        throws SQLException, IOException, AS400SecurityException, 
                ErrorCompletingRequestException, InterruptedException, PropertyVetoException{

    // Ensure the SourcePf Dir exists
    createDir(ifsOutputDir);

    ResultSet rsMembers =  conn.createStatement().executeQuery(
      "SELECT SYSTEM_TABLE_MEMBER As Member, SOURCE_TYPE As SourceType " +
      "FROM QSYS2.SYSPARTITIONSTAT " +
      "WHERE SYSTEM_TABLE_SCHEMA = '" + library + "' " +
      "AND SYSTEM_TABLE_NAME = '" + sourcePf + "' " +
      "And Trim(SOURCE_TYPE) <> ''"
    );
    // Iterate through each member
    while (rsMembers.next()) {
      String memberName = rsMembers.getString("Member").trim();
      String sourceType = rsMembers.getString("SourceType").trim();
      System.out.println("\n=== Processing Member: " + memberName + " ===");

      //useAliases(conn, memberName, ifsOutputDir);
      useCommand(library, sourcePf, memberName, sourceType, ifsOutputDir, system);      
    }
    
    rsMembers.close();

  }

  private static void useCommand(String library, String sourcePf, String memberName, String sourceType, 
      String ifsOutputDir, AS400 system) 
      throws IOException, AS400SecurityException, ErrorCompletingRequestException, InterruptedException, PropertyVetoException{

    //TODO: Consider ASP
    String commandStr = "CPYTOSTMF FROMMBR('/QSYS.lib/" 
                   + library + ".lib/" 
                   + sourcePf + ".file/" 
                   + memberName + ".mbr') "
                   + "TOSTMF('" 
                   + ifsOutputDir + "/" 
                   + memberName + "." + sourceType + "') "
                   + "STMFOPT(*REPLACE) STMFCCSID(" + CCSID 
                   + ") ENDLINFMT(*LF)";

    CommandCall cmd = new CommandCall(system);
    if(!cmd.run(commandStr)){
      System.out.println("Could not migrate " + memberName + " ***");
      return;
    }

    // Increment counter
    totalMembersMigrated ++;
    System.out.println("Migrateed " + memberName + " successfully");

  }

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

  private static void createDir(String dirPath){
    File outputDir = new File(dirPath);
    if (!outputDir.exists()) {
        System.out.println("Creating dir: " + dirPath + " ...");
        outputDir.mkdirs(); // Create directory if it doesn't exist
    }
  }

  private static void showSourcePfs(Connection conn, String library)
      throws SQLException{
    ResultSet rsshowSourcePf = conn.createStatement().executeQuery(
      "Select SYSTEM_TABLE_NAME As SourcePf, Count(*) As Members " +
      "From QSYS2.SYSPARTITIONSTAT " +
      "Where SYSTEM_TABLE_SCHEMA = '" + library + "' " +
      "And Trim(SOURCE_TYPE) <> ''" +
      "Group by SYSTEM_TABLE_NAME"
    );

    // Show list of SourcePFs inside a given library
    System.out.println("\nList of available Source PFs in library: " + library);
    System.out.println("    SourcePf      | Number of Members"); // Header with aligned spacing
    System.out.println("    ------------- | -----------------"); // Separator line for clarity
    while(rsshowSourcePf.next()){
      String rsSourcePf = rsshowSourcePf.getString("SourcePf").trim();
      String membersCount = rsshowSourcePf.getString("Members").trim();
      System.out.println(String.format("    %-13s | %17s", rsSourcePf, membersCount));
    }
  }

  private static ResultSet getSourcePfs(Connection conn, String sourcePf, String library)
      throws SQLException{
    if (!sourcePf.isEmpty()) {
      // Validates if SourcePF exists
      if (!conn.createStatement().executeQuery(
          "Select 1 As Exist From QSYS2.SYSPARTITIONSTAT " + 
          "Where SYSTEM_TABLE_SCHEMA = '" + library + "' " + 
          "AND SYSTEM_TABLE_NAME = '" + sourcePf + "' " +
          "And Trim(SOURCE_TYPE) <> '' limit 1")
          .next()) {
        System.out.println(" *Source PF does not exists in library " + library);
        return null;
      }
      // Show all members

      /* Creates result set with members of an specific Source Pf */
      return conn.createStatement().executeQuery(
        "SELECT SYSTEM_TABLE_NAME As SourcePf,  SYSTEM_TABLE_SCHEMA As Library " +
        "FROM QSYS2.SYSPARTITIONSTAT " +
        "WHERE SYSTEM_TABLE_SCHEMA = '" + library + "' " +
        "AND SYSTEM_TABLE_NAME = '" + sourcePf + "' " +
        "And Trim(SOURCE_TYPE) <> ''" +
        "Group by SYSTEM_TABLE_NAME, SYSTEM_TABLE_SCHEMA"
      );
    } 
    
    /* Creates result set with members of all the Source Pf in the chosen library*/
    return conn.createStatement().executeQuery(
      "SELECT SYSTEM_TABLE_NAME As SourcePf,  SYSTEM_TABLE_SCHEMA As Library " +
      "FROM QSYS2.SYSPARTITIONSTAT " +
      "WHERE SYSTEM_TABLE_SCHEMA = '" + library + "' " +
      "And Trim(SOURCE_TYPE) <> ''" +
      "Group by SYSTEM_TABLE_NAME, SYSTEM_TABLE_SCHEMA"
    );
  }

  //TODO: Validate resturning the result set
  private static String getLibrary(Connection conn, String library)
      throws SQLException{
    // Validates if library exists
    if (!conn.createStatement().executeQuery(
        "Select 1 As Exists " +
        "From QSYS2.SYSPARTITIONSTAT " + 
        "Where SYSTEM_TABLE_SCHEMA = '" + library + "' limit 1 ")
        .next()) {
      //TODO: Add validation to show related libraries. If this last !.next() do the return.
      System.out.println("Library " + library  + " does not exists in your system");

      // Show related libraries if they exists
      ResultSet relatedLibraries = conn.createStatement().executeQuery(
        "Select SYSTEM_TABLE_SCHEMA As library " +
        "From QSYS2.SYSPARTITIONSTAT " + 
        "Where SYSTEM_TABLE_SCHEMA like '%" + library + "%'" +
        "Group by SYSTEM_TABLE_SCHEMA limit 10"
      );
      if (relatedLibraries.next()) {
        System.out.println("Did you mean: ");
        System.out.println(relatedLibraries.getString("library").trim());
      }
      while(relatedLibraries.next()){
        System.out.println(relatedLibraries.getString("library").trim());
        //System.out.println(String.format("    %-13s | %17s", rsSourcePf, membersCount));     
      }

      return "";
    }
    return library;
  }


}
