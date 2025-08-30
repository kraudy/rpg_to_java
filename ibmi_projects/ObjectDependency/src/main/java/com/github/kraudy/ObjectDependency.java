package com.github.kraudy;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public class ObjectDependency {
  private static final String UTF8_CCSID = "1208"; // UTF-8 for stream files
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private List<Nodes> nodes;
  // user
  public ObjectDependency(AS400 system){
  }
  private void dependencies(){
    String library = "ROBKRAUDY2";
    try {
      ResultSet rsobjs = getObjects();
      if (rsobjs = null){
        return;
      }
      getDepencies(rsobjs, library);

    } catch (Exception e) {
      e.printStackTrace();
    } finally{
      cleanup();
    }
  }
  //TODO: I think this should be recursive too
  private ResultSet getObjects(){
    try(Statement objsStmt = connection.createStatement();
        ResultSet rsobjs = objsStmt.executeQuery(
              "WITH SourcePf (SourcePf) AS ( " + 
                  "SELECT TABLE_NAME AS SourcePf " +
                  "FROM QSYS2.SYSTABLES " + 
                  "WHERE TABLE_SCHEMA = 'ROBKRAUDY2' " +
                  "AND FILE_TYPE = 'S' " +
              ") " +
              "SELECT " +
                  "CAST(OBJNAME AS VARCHAR(10) CCSID " + INVARIANT_CCSID + " AS object_name, " +
                  "CAST(OBJTYPE AS VARCHAR(10) CCSID " + INVARIANT_CCSID + " AS object_type, " +
                  "CAST(OBJTEXT AS VARCHAR(20) CCSID " + INVARIANT_CCSID + " AS text_description, " + 
                  "CAST((CASE TRIM(OBJATTRIBUTE) WHEN '' THEN SQL_OBJECT_TYPE ELSE OBJATTRIBUTE END) AS VARCHAR(10) CCSID " + INVARIANT_CCSID + " As attribute + " +
              "FROM TABLE(QSYS2.OBJECT_STATISTICS('ROBKRAUDY2', '*ALL')) " +
              "EXCEPTION JOIN SourcePf " +
              "ON (SourcePf.SourcePf = OBJNAME AND " +
                  "OBJTYPE = '*FILE'")){
     
    }
  }
  private void getDepencies(ResultSet rsobjs, String library){
    String object = "PAYROLL";   // Object name (e.g., program name)
    String outfileLib = "QTEMP";   // Use QTEMP for temporary storage
    String outfileName = "PGMREFS"; // Outfile name

    //TODO: Maybe move this out to just use return in the recursion
    while(rsobjs.next()){
      String objName = rsobjs.getString("object_name");
      String objType = rsobjs.getString("object_type");
      String objAttr = rsobjs.getString("text_description");
      System.out.println("Object: " + objName + ", Type: " + objType + ", Attribute: " + objAttr);
    }

    try {
      String commandStr = "DSPPGMREF PGM(" + library + "/" + objName + ") " + 
          "OUTPUT(*OUTFILE) " +
          "OBJTYPE(*"+ objType +") " + //TODO: For SQL types use db2 services
          "OUTFILE(" + library + "/PGMREF)";

      //TODO: Add switch to use the corresponding function to get the dependencies: dsppgmref or sql services
      //TODO: Remember to do the return so this can be called recursively
      //TODO: Remember to create an idnetifyier for every node to check if it exist and prevent loops
      CommandCall cmd = new CommandCall(system);
      if (!cmd.run(commandStr)) {
        System.out.println("Could not get dependencies for " + objName + ": Failed");
        return;
      } else {
        System.out.println("dependencies for " + objName + ": OK");
      }

    } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException
        | PropertyVetoException e) {

      System.out.println("Could not get dependencies for " + objName + ": Failed");
      e.printStackTrace();
      }

  }
  private void cleanup(){
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
      }
      if (system != null) {
        system.disconnectAllServices();
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
  public static void main( String... args ){
    AS400 system = null;
    ObjectDependency dependencies = null;
    try {
      system = IBMiDotEnv.getNewSystemConnection(true); // Get system
      dependencies = new ObjectDependency(system);
      dependencies.dependencies();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

public class Nodes {
  private int id; //public
}