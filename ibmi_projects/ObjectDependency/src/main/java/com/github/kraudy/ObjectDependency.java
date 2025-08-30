package com.github.kraudy;

import java.beans.PropertyVetoException;
import java.io.File;
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
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;
import com.github.kraudy.Nodes;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

@Command(name = "ObjectReferencer", mixinStandardHelpOptions = true, version = "ObjectReferencer 0.0.1")
public class ObjectDependency implements Runnable { // ObjectReferencer
  private static final String UTF8_CCSID = "1208"; // UTF-8 for stream files
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;
  private Map<String, Set<String>> graph = new HashMap<>(); // objectKey -> dependsOn
  //private List<Nodes> nodes;
  @Option(names = "--lib", description = "Primary library to scan") private String library;

  @Option(names = { "-l", "--list" }, arity = "0..*", paramLabel = "LIBRARIES", description = "Library list")
  private List<String> libraryList = new ArrayList<>();
  //List<String> libraryList;
  //String[] libraryList;
  //@Parameters(index = "0..*", paramLabel = "LIBRARIES", description = "Library List") String[] libraryList;

  @Option(names = "-v", description = "Verbose output")
  private boolean verbose = false;

  @Option(names = "--json", description = "Output as JSON")
  private boolean jsonOutput = false;


  @Option(names = { "-h", "--help" }, usageHelp = true, description = "Builds dependency graph for IBM i objects")
  private boolean helpRequested = false;

  public void run() {
    if (library == null){
      System.err.println("Error: A library is required via --lib or positional args.");
      CommandLine.usage(this, System.err);
      return;
    }
    if (!libraryList.contains(library)) {
      libraryList.add(library);
    }
    String commandStr = "CHGLIBL LIBL(" + String.join(" ", libraryList) + ")";
    try {
      CommandCall cmd = new CommandCall(system);
      if (!cmd.run(commandStr)) {
          System.out.println("Could not change library list to " + libraryList   + ": Failed");
          return;
      }
    } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException | PropertyVetoException e){
      e.printStackTrace();
      return;
    }
    
    this.dependencies();
  }
  // TODO: Maybe use json to describe the depencency context
  // TODO: Add librar list as param and change it with CHGLIBL, then revet it.
  // TODO: This should be the same library send to the ObjectCompiler which should use the SourceMigrator to get the data or use IFS
  /*
   * -l : List of libraries
   * -v : Verbose
   */
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
    //String library = "ROBKRAUDY1";
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

        System.out.println("Library: " + library + ", Object: " + objName + ", Type: " + objType + ", Attribute: " + objAttr);
        
        getDependencies(library, objName, objType, objAttr, objKey);

      }
    }
  }
  private void getDependencies(String library, String objName, String objType, String objAttr , String objKey){
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

        // Query outfile immediately (model QADSPPGM/QWHDRPPR)
        try (Statement depStmt = connection.createStatement();
          ResultSet rsDeps = depStmt.executeQuery(
                  "SELECT " + 
                  "CAST(WHFNAM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS WHFNAM, " +
                  "CAST(WHLNAM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS WHLNAM, " +
                  "CAST(WHOTYP AS VARCHAR(8) CCSID " + INVARIANT_CCSID + ") AS WHOTYP " +
                  "FROM " + outfileLib + "." + outfileName)) { //TODO: Do CAST 37
          while (rsDeps.next()) {
            String depName = rsDeps.getString("WHFNAM").trim();
            String depLib = rsDeps.getString("WHLNAM").trim();
            String depType = rsDeps.getString("WHOTYP").trim();
            if (depName.isEmpty() || depName.equals("*EXPR") || depLib.equals("*EXPR")) {
              continue;  // Skip expressions/unresolved
            }
            if (depLib.equals("QSYS") || depLib.equals("QSYS2")) {
              continue;
            }
            //TODO: Add suggestion if not found
            // Resolve *LIBL to actual library using current lib list
            if (depLib.equals("*LIBL")) {
              String objdOutfile = "OBJD";  // Temp outfile for DSPOBJD
              String resolveCmd = "DSPOBJD OBJ(*LIBL/" + depName + ") OBJTYPE(" + depType + ") " +
                      "OUTPUT(*OUTFILE) OUTFILE(" + outfileLib + "/" + objdOutfile + ") OUTMBR(*FIRST *REPLACE)";
              try (Statement resolveStmt = connection.createStatement()) { //TODO: Move to SQL
                resolveStmt.execute("CALL QSYS2.QCMDEXC('" + resolveCmd + "')");
              } catch (SQLException e) {
                System.out.println("Could not resolve *LIBL for " + depName + ": Failed");
                e.printStackTrace();
                continue;  // Skip if resolution fails
              }

              // Query DSPOBJD outfile for resolved lib (model QADSPOBJ, field ODLBNM)
              try (Statement objdStmt = connection.createStatement();
                    ResultSet rsObjd = objdStmt.executeQuery(
                            "SELECT CAST(ODLBNM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS ODLBNM " +
                            "FROM " + outfileLib + "." + objdOutfile)) {
                if (rsObjd.next()) {
                  depLib = rsObjd.getString("ODLBNM").trim();
                } else {
                  System.out.println("*LIBL resolution for " + depName + " found no object.");
                  continue;
                }
              }
            }
            String depKey = depLib + "/" + depName + "/" + depType;
            graph.get(objKey).add(depKey);  // Add edge: obj depends on dep
            graph.putIfAbsent(depKey, new HashSet<>());  // Ensure dep node exists
          }
        }
      } else if (objType.equals("*FILE") && objAttr.equals("LF")) {
          // TODO: Use DSPFD to outfile or SQL SYSTABLEDEP for based-on PFs (already gives exact schemas/libs)
          /* SELECT BASE_SCHEMA, BASE_TABLE FROM QSYS2.SYSTABLEDEP WHERE DEPENDENT_SCHEMA = ? AND DEPENDENT_TABLE = ? */
          /* SELECT BASE_SCHEMA AS depLib, BASE_TABLE AS depName 
          FROM QSYS2.SYSTABLEDEP WHERE DEPENDENT_SCHEMA = '" + library + "' AND DEPENDENT_TABLE = '" + objName + "' */
          // depType = "*FILE"
          // QSYS2.PROGRAM_INFO
          // QSYS2.SYSROUTINEDEP
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
      new CommandLine(dependencies).execute(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}