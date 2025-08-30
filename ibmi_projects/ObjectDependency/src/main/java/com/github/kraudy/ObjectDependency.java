package com.github.kraudy;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;
import com.github.kraudy.Nodes;

public class ObjectDependency {
  private static final String UTF8_CCSID = "1208"; // UTF-8 for stream files
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;
  private Map<String, Set<String>> graph = new HashMap<>(); // objectKey -> dependsOn
  //private List<Nodes> nodes;

  // user
  public ObjectDependency(AS400 system) throws Exception {
    this(system, new AS400JDBCDataSource(system).getConnection());
  }

  public ObjectDependency(AS400 system, Connection connection) throws Exception {
    this.system = system;

    // Database
    this.connection = connection;
    this.connection.setAutoCommit(true);

    // User
    this.currentUser = new User(system, system.getUserId());
    this.currentUser.loadUserInformation();
  }
  private void dependencies(){
    String library = "ROBKRAUDY2";
    try {
      getObjects(library);
      // After building graph, toposort it
      List<String> ordered = topologicalSort(graph);
      System.out.println("Topological Order: " + ordered);

    } catch (Exception e) {
      e.printStackTrace();
    } finally{
      cleanup();
    }
  }
  //TODO: I think this should be recursive too
  private void getObjects(String library) throws SQLException {
    try(Statement objsStmt = connection.createStatement();
        ResultSet rsobjs = objsStmt.executeQuery(
              "WITH SourcePf (SourcePf) AS ( " + 
                  "SELECT TABLE_NAME AS SourcePf " +
                  "FROM QSYS2.SYSTABLES " + 
                  "WHERE TABLE_SCHEMA = '" + library + "' " +
                  "AND FILE_TYPE = 'S' " +
              ") " +
              "SELECT " +
                  "CAST(OBJNAME AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS object_name, " +
                  "CAST(OBJTYPE AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS object_type, " +
                  "CAST(OBJTEXT AS VARCHAR(50) CCSID " + INVARIANT_CCSID + ") AS text_description, " + 
                  "CAST((CASE TRIM(OBJATTRIBUTE) WHEN '' THEN SQL_OBJECT_TYPE ELSE OBJATTRIBUTE END) AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") As attribute " +
              "FROM TABLE(QSYS2.OBJECT_STATISTICS('" + library + "', '*ALL')) " +
              "EXCEPTION JOIN SourcePf " +
              "ON (SourcePf.SourcePf = OBJNAME AND " +
                  "OBJTYPE = '*FILE')")){
      //TODO: Maybe move this out to just use return in the recursion
      while(rsobjs.next()){
        String objName = rsobjs.getString("object_name");
        String objType = rsobjs.getString("object_type");
        String objDesc = rsobjs.getString("text_description");
        String objAttr = rsobjs.getString("attribute");
        String objKey = library + "/" + objName + "/" + objType;
        graph.putIfAbsent(objKey, new HashSet<>());

        System.out.println("Object: " + objName + ", Type: " + objType + ", Attribute: " + objAttr);
        
        getDepencies(library, objName, objType, objAttr, objKey);

      }
    }
  }
  private void getDepencies(String library, String objName, String objType, String objAttr , String objKey){
    String object = "PAYROLL";   // Object name (e.g., program name)
    String outfileLib = "QTEMP";   // Use QTEMP for temporary storage
    String outfileName = "PGMREFS";  // Could make unique if parallel

    try {
      // Switch based on type/attr (TODO: expand for files/SQL)
      if (objType.equals("*PGM") || objType.equals("*SRVPGM") || objType.equals("*MODULE") || objType.equals("*SQLPKG")) {
        //TODO: SELECT * FROM TABLE(PGMREFS(OBJECT_NAME=>?))
        String commandStr = "DSPPGMREF PGM(" + library + "/" + objName + ") " +
                "OUTPUT(*OUTFILE) OBJTYPE(" + objType + ") " +
                "OUTFILE(" + outfileLib + "/" + outfileName + ") OUTMBR(*FIRST *REPLACE)";

        try (Statement cmdStmt = connection.createStatement()) { //TODO: Use this to create the UDF function in QTEMP
          cmdStmt.execute("CALL QSYS2.QCMDEXC('" + commandStr + "')");
        } catch (SQLException e) {
          System.out.println("Could not get dependencies for " + objName + ": Failed");
          e.printStackTrace();
          return;
        }

        //CommandCall cmd = new CommandCall(system);
        //if (!cmd.run(commandStr)) {
        //    System.out.println("Could not get dependencies for " + objName + ": Failed");
        //    return;
        //}
        System.out.println("Dependencies for " + objName + ": OK");

        // Query outfile immediately (model QWHDRPGM)
        try (Statement depStmt = connection.createStatement();
          ResultSet rsDeps = depStmt.executeQuery(
                  "SELECT " + 
                  "CAST(WHFNAM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS WHFNAM, " +
                  "CAST(WHFLIB AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS WHFLIB, " +
                  "CAST(WHOTYP AS VARCHAR(8) CCSID " + INVARIANT_CCSID + ") AS WHOTYP " +
                  "FROM " + outfileLib + "." + outfileName)) { //TODO: Do CAST 37
          while (rsDeps.next()) {
            String depName = rsDeps.getString("WHFNAM").trim();
            String depLib = rsDeps.getString("WHFLIB").trim();
            String depType = rsDeps.getString("WHOTYP").trim();
            if (!depName.isEmpty()) {
              String depKey = depLib + "/" + depName + "/" + depType;
              graph.get(objKey).add(depKey);  // Add edge: obj depends on dep
              graph.putIfAbsent(depKey, new HashSet<>());  // Ensure dep node exists
            }
          }
        }
      } else if (objType.equals("*FILE") && objAttr.equals("LF")) {
          // TODO: Use DSPFD to outfile or SQL SYSTABLEDEP for based-on PFs
          /* SELECT BASE_SCHEMA, BASE_TABLE FROM QSYS2.SYSTABLEDEP WHERE DEPENDENT_SCHEMA = ? AND DEPENDENT_TABLE = ? */
      } else {
          // Skip or handle other types (e.g., SQL views with SYSVIEWDEP)
      }
    } catch (Exception e) {
        System.out.println("Could not get dependencies for " + objName + ": Failed");
        e.printStackTrace();
    }
  }

  // Kahn's algorithm (assumes no cycles; add detection if needed)
  private List<String> topologicalSort(Map<String, Set<String>> graph) {
    Map<String, Integer> indegree = new HashMap<>();
    for (String node : graph.keySet()) {
      indegree.put(node, 0);
    }
    for (Set<String> deps : graph.values()) {
      for (String dep : deps) {
        indegree.put(dep, indegree.getOrDefault(dep, 0) + 1);
      }
    }

    Queue<String> queue = new LinkedList<>();
    for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    List<String> order = new ArrayList<>();
    while (!queue.isEmpty()) {
      String node = queue.poll();
      order.add(node);
      for (String dep : graph.getOrDefault(node, new HashSet<>())) {
        indegree.put(dep, indegree.get(dep) - 1);
        if (indegree.get(dep) == 0) {
          queue.add(dep);
        }
      }
    }

    if (order.size() != graph.size()) {
      throw new RuntimeException("Cycle detected in dependencies");
    }
    return order;  // Dependencies first
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