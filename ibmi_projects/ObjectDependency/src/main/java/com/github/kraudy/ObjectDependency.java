package com.github.kraudy;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR, DSPFD, DSPPGM, SYSTABLEDEP, SYSVIEWDEP, SYSIXDEP, SYSROUTINEDEP, PROGRAM_INFO } //TODO: I think DSPFD and DSPPGM can be done by sql
  //enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR, DSPFD, DSPPGM } 
  //enum Db2Cmd {SYSTABLEDEP, SYSVIEWDEP, SYSIXDEP, SYSROUTINEDEP, PROGRAM_INFO} 

  enum SourceType { RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL}

  enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION } // Add more as needed

  // Similar to typeToCmdMap in ObjectCompiler: Map ObjectType to SysCmd for dependency fetching
  private static final Map<ObjectType, SysCmd> objTypeToDepCmdMap = new EnumMap<>(ObjectType.class);

  // Lambda mapping like Resolver's valueSuppliers: Map ObjectType to a function that resolves deps
  // Function inputs: String[] {library, objName, objAttr} -> Set<depKey>
  private final Map<ObjectType, Function<String[], Set<String>>> depResolvers = new EnumMap<>(ObjectType.class);

  static {
    // Populate similar to typeToCmdMap in ObjectCompiler
    objTypeToDepCmdMap.put(ObjectType.PGM, SysCmd.DSPPGMREF);
    objTypeToDepCmdMap.put(ObjectType.SRVPGM, SysCmd.DSPPGMREF);
    objTypeToDepCmdMap.put(ObjectType.MODULE, SysCmd.DSPPGMREF);
    objTypeToDepCmdMap.put(ObjectType.TABLE, SysCmd.SYSTABLEDEP);
    objTypeToDepCmdMap.put(ObjectType.LF, SysCmd.SYSTABLEDEP);
    objTypeToDepCmdMap.put(ObjectType.VIEW, SysCmd.SYSVIEWDEP);
    objTypeToDepCmdMap.put(ObjectType.ALIAS, SysCmd.SYSIXDEP);  // Or SYSTABLEDEP if alias over table. Could make it a list.
    objTypeToDepCmdMap.put(ObjectType.PROCEDURE, SysCmd.SYSROUTINEDEP);
    objTypeToDepCmdMap.put(ObjectType.FUNCTION, SysCmd.SYSROUTINEDEP);
  }

  static class ObjectTypeConverter implements CommandLine.ITypeConverter<ObjectType> {
    @Override
    public ObjectType convert(String type) throws Exception {
      try {
        return ObjectType.valueOf(type.toUpperCase().trim()); //TODO: Validate * for *pgm, *module, etc
      } catch (IllegalArgumentException e) {
        throw new CommandLine.TypeConversionException("Invalid object type: '" + type + "'. Must be one of: " + Arrays.toString(ObjectType.values()));
      }
    }
  }

  static class objectNameConverter implements CommandLine.ITypeConverter<String> {
    @Override
    public String convert(String name) throws Exception {
      try {
        return name.toUpperCase().trim();
      } catch (IllegalArgumentException e) {
        throw new CommandLine.TypeConversionException("Invalid object name: '" + name);
      }
    }
  }

  static class SourceTypeConverter implements CommandLine.ITypeConverter<SourceType> {
    @Override
    public SourceType convert(String value) throws Exception {
      try {
        return SourceType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid source type: " + value);
      }
    }
  }

  @Option(names = { "-l", "--libs" }, required = true, arity = "1..*", description = "Library list (first is primary)")
  private List<String> libraryList = new ArrayList<>();

  @Option(names = "--obj", description = "Object name", converter = objectNameConverter.class)
  private String objectName;

  @Option(names = "--type", description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectType objectType;

  @Option(names = "--source-type", description = "Source type (e.g., RPGLE, CLLE)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  @Option(names = "-o", description = "Output")
  private String outLibrary = "QTEMP";

  @Option(names = "-x", description = "Debug")
  private boolean debug = false;

  @Option(names = "-v", description = "Verbose output")
  private boolean verbose = false;

  @Option(names = "--json", description = "Output as JSON")
  private boolean jsonOutput = false;

  @Option(names = { "-h", "--help" }, usageHelp = true, description = "Builds dependency graph for IBM i objects")
  private boolean helpRequested = false;

  public void run() {
    //TODO: Validate if library and objects exists
    if (objectName != null && objectType == null || objectName == null && objectType != null) {
      throw new IllegalArgumentException("--object and --type must both be provided or omitted.");
    }
    libraryList = libraryList.stream().map(String::trim).map(String::toUpperCase).distinct().collect(Collectors.toList());
    //TODO: Add lib validation
    if (debug) System.out.println("Library list: " + String.join(" ", libraryList));

    String commandStr = "CHGLIBL LIBL(" + String.join(" ", libraryList) + ")";
    try (Statement cmdStmt = connection.createStatement()) { //TODO: Use this to create the UDF function in QTEMP
      cmdStmt.execute("CALL QSYS2.QCMDEXC('" + commandStr + "')");
    } catch (SQLException e) {
      System.out.println("Could not change library list to " + libraryList + ": Failed");
      e.printStackTrace();
      return;
    }
    
    this.dependencies(libraryList.get(0)); // First library for scan
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

    initDepResolvers();  // Initialize lambdas like initSuppliers in Resolver
  }

  private void initDepResolvers() {
    // Lambdas/Method refs like suppliers in Resolver
    depResolvers.put(ObjectType.PGM, this::getProgramDeps);
    depResolvers.put(ObjectType.SRVPGM, this::getProgramDeps);  // Same as PGM (DSPPGMREF works)
    depResolvers.put(ObjectType.MODULE, this::getProgramDeps);
    depResolvers.put(ObjectType.TABLE, this::getTableDeps);
    depResolvers.put(ObjectType.LF, this::getTableDeps);  // Logical depends on physical
    depResolvers.put(ObjectType.VIEW, this::getViewDeps);
    depResolvers.put(ObjectType.ALIAS, this::getTableDeps);  // Alias depends on base
    depResolvers.put(ObjectType.PROCEDURE, this::getRoutineDeps);
    depResolvers.put(ObjectType.FUNCTION, this::getRoutineDeps);
  }

  private void dependencies(String library){
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

  //TODO: Should this be recursive?
  private void getObjects(String library) throws SQLException {
    StringBuilder whereClause = new StringBuilder("");
    // Build whereClause similar to your code, but use shared enums
    // ...
    // (Omit for brevity; keep your existing logic, but reference ObjectType enums)
    if (objectName != null && objectType != null) {
      whereClause.append(" WHERE OBJNAME = '" + objectName + "' AND ");
      // Already sanitized in run() TODO: Use only objectName
      switch (objectType) {
        case PGM:
            whereClause.append(" OBJTYPE = '*PGM'");
            break;
        case SRVPGM:
            whereClause.append(" OBJTYPE = '*SRVPGM'");
            break;
        case MODULE:
            whereClause.append(" OBJTYPE = '*MODULE'");
            break;
        case TABLE:
            whereClause.append(" OBJTYPE = '*FILE' AND SQL_OBJECT_TYPE = 'TABLE'");
            break;
        case LF:
            whereClause.append(" OBJTYPE = '*FILE' AND OBJATTRIBUTE = 'LF'");
            break;
        case VIEW:
            whereClause.append(" OBJTYPE = '*FILE' AND SQL_OBJECT_TYPE = 'VIEW'");
            break;
        case ALIAS:
            whereClause.append(" OBJTYPE = '*FILE' AND SQL_OBJECT_TYPE = 'ALIAS'");
            break;
        case PROCEDURE:
            // Procedures are special: Primarily identified by SQL_OBJECT_TYPE; OBJTYPE often '*PGM' or '*SRVPGM' but not always enforced
            whereClause.append(" SQL_OBJECT_TYPE = 'PROCEDURE'");
            break;
        case FUNCTION:
            // Functions typically '*PGM' with SQL_OBJECT_TYPE
            whereClause.append(" OBJTYPE = '*PGM' AND SQL_OBJECT_TYPE = 'FUNCTION'");
            break;
        default:
            throw new IllegalArgumentException("Unsupported object type: " + objectType);
      }
    }
    try(Statement objsStmt = connection.createStatement();
        ResultSet rs = objsStmt.executeQuery(
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
                  "CAST((CASE WHEN SQL_OBJECT_TYPE IS NOT NULL THEN SQL_OBJECT_TYPE ELSE OBJATTRIBUTE END) AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") As attribute " +
              "FROM TABLE(QSYS2.OBJECT_STATISTICS('" + library + "', '*ALL')) " +
              "EXCEPTION JOIN SourcePf " +
              "ON (SourcePf.SourcePf = OBJNAME AND " +
                  "OBJTYPE = '*FILE') " +
              whereClause.toString())){
      //TODO: Maybe move this out to just use return in the recursion
      while(rs.next()){
        String objName = rs.getString("object_name");
        String objTypeStr = rs.getString("object_type");
        String objAttr = rs.getString("attribute");
        String objDesc = rs.getString("text_description");

        // Log for debugging
        if (verbose) {
            System.out.println("Processing: Library=" + library + ", Object=" + objName + ", Type=" + objTypeStr + ", Attribute=" + (objAttr != null ? objAttr : "NULL"));
        }

        // Determine ObjectType based on objTypeStr and objAttr
        ObjectType objTypeEnum;
        if (objTypeStr.equals("*FILE")) {
          if (objAttr == null) {
            if (verbose) {
              System.out.println("Skipping *FILE with null attribute: " + objName);
            }
            continue;
          }
          // For files, use objAttr (which is OBJATTRIBUTE or SQL_OBJECT_TYPE)
          switch (objAttr) {
            case "TABLE":
              objTypeEnum = ObjectType.TABLE;
              break;
            case "LF":
              objTypeEnum = ObjectType.LF;
              break;
            case "VIEW":
              objTypeEnum = ObjectType.VIEW;
              break;
            case "ALIAS":
              objTypeEnum = ObjectType.ALIAS;
              break;
            default:
              // Skip or handle unsupported file subtypes (e.g., log "Unsupported file attribute: " + objAttr)
              if (verbose) {
                System.out.println("Skipping unsupported *FILE with attribute: " + objAttr);
              }
              continue;  // Or throw if you want strict enforcement
          }
        } else if (objAttr.equals("PROCEDURE") || objAttr.equals("FUNCTION")) {
          // Override for SQL routines (may appear as *PGM/*SRVPGM)
          objTypeEnum = objAttr.equals("PROCEDURE") ? ObjectType.PROCEDURE : ObjectType.FUNCTION;
        } else {
          // For non-files (e.g., *PGM, *SRVPGM, *MODULE), use objTypeStr
          try {
            objTypeEnum = ObjectType.valueOf(objTypeStr.replace("*", "").trim());
          } catch (IllegalArgumentException e) {
            if (verbose) {
              System.out.println("Skipping unsupported object type: " + objTypeStr);
            }
            continue;
          }
        }


        //ObjectType objTypeEnum = ObjectType.valueOf(objTypeStr.replace("*", "").trim());  // Map "*PGM" to PGM
        String objKey = library + "/" + objName + "/" + objTypeStr;
        graph.putIfAbsent(objKey, new HashSet<>());

        System.out.println("Library: " + library + ", Object: " + objName + ", Type: " + objTypeStr + ", Attribute: " + objAttr);
        
        // Use the resolver map/lambda
        Function<String[], Set<String>> resolver = depResolvers.getOrDefault(objTypeEnum, params -> new HashSet<>());
        Set<String> deps = resolver.apply(new String[]{library, objName, objAttr});
        graph.get(objKey).addAll(deps);
        deps.forEach(depKey -> graph.putIfAbsent(depKey, new HashSet<>()));  // Ensure nodes exist
      }
    }
  }

  // Example resolver method (used via lambda)
  private Set<String> getProgramDeps(String[] args) {
    String library = args[0], objName = args[1], objAttr = args[2];
    Set<String> deps = new HashSet<>();
    String outfileName = "PGMREFS";
    String commandStr = "DSPPGMREF PGM(" + library + "/" + objName + ") OUTPUT(*OUTFILE) OBJTYPE(*ALL) " +
                        "OUTFILE(" + outLibrary + "/" + outfileName + ") OUTMBR(*FIRST *REPLACE)";
    try (Statement cmdStmt = connection.createStatement()) {
      cmdStmt.execute("CALL QSYS2.QCMDEXC('" + commandStr + "')");
      try (ResultSet rsDeps = cmdStmt.executeQuery(
          "SELECT CAST(WHFNAM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS WHFNAM, " +
          "CAST(WHLNAM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS WHLNAM, " +
          "CAST(WHOTYP AS VARCHAR(8) CCSID " + INVARIANT_CCSID + ") AS WHOTYP " +
          "FROM " + outLibrary + "." + outfileName)) {
        while (rsDeps.next()) {
          String depName = rsDeps.getString("WHFNAM").trim();
          String depLib = rsDeps.getString("WHLNAM").trim();
          String depType = rsDeps.getString("WHOTYP").trim();
          if (depName.isEmpty() || depName.equals("*EXPR") || depLib.equals("*EXPR") || depLib.startsWith("Q")) {
              continue;
          }
          depLib = resolveLibL(depLib, depName, depType);
          String depKey = depLib + "/" + depName + "/" + depType;
          deps.add(depKey);
        }
      }
    } catch (SQLException e) {
        if (verbose) e.printStackTrace();
    }
    return deps;
  }

  // Resolver for table/LF/alias (using SQL instead of command)
  private Set<String> getTableDeps(String[] args) {
    String library = args[0], objName = args[1];
    Set<String> deps = new HashSet<>();
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
              "SELECT CAST(BASE_SCHEMA AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depLib, " +
              "CAST(BASE_TABLE AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depName " +
              "FROM QSYS2.SYSTABLEDEP WHERE DEPENDENT_SCHEMA = '" + library + "' AND DEPENDENT_TABLE = '" + objName + "'")) {
      while (rs.next()) {
        String depLib = rs.getString("depLib").trim();
        String depName = rs.getString("depName").trim();
        String depKey = depLib + "/" + depName + "/*FILE";
        deps.add(depKey);
      }
    } catch (SQLException e) {
      if (verbose) e.printStackTrace();
    }
    return deps;
  }

  // Similar for views
  private Set<String> getViewDeps(String[] args) {
    String library = args[0], objName = args[1];
    Set<String> deps = new HashSet<>();
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT CAST(VIEW_SCHEMA AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depLib, " +
                "CAST(VIEW_NAME AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depName " +
                "FROM QSYS2.SYSVIEWDEP WHERE BASE_SCHEMA = '" + library + "' AND BASE_TABLE = '" + objName + "'")) {  // Note: Inverted for dependents, adjust if needed for bases
      while (rs.next()) {
          String depLib = rs.getString("depLib").trim();
          String depName = rs.getString("depName").trim();
          String depKey = depLib + "/" + depName + "/*FILE";
          deps.add(depKey);
      }
    } catch (SQLException e) {
      if (verbose) e.printStackTrace();
    }
    return deps;
  }

  // For procedures/functions
  private Set<String> getRoutineDeps(String[] args) {
    String library = args[0], objName = args[1];
    Set<String> deps = new HashSet<>();
    try (Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT CAST(DEPENDENT_SCHEMA AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depLib, " +
            "CAST(DEPENDENT_ROUTINE AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depName, " +
            "CAST(DEPENDENT_ROUTINE_TYPE AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS depType " +
            "FROM QSYS2.SYSROUTINEDEP WHERE ROUTINE_SCHEMA = '" + library + "' AND ROUTINE_NAME = '" + objName + "'")) {
      while (rs.next()) {
        String depLib = rs.getString("depLib").trim();
        String depName = rs.getString("depName").trim();
        String depType = rs.getString("depType").trim();  // e.g., PROCEDURE or FUNCTION
        String depKey = depLib + "/" + depName + "/" + depType;
        deps.add(depKey);
      }
    } catch (SQLException e) {
        if (verbose) e.printStackTrace();
    }
    return deps;
  }

  // Helper for *LIBL resolution (used in resolvers)
  private String resolveLibL(String depLib, String depName, String depType) {
    if (!depLib.equals("*LIBL")) return depLib;
    String objdOutfile = "OBJD";
    String resolveCmd = "DSPOBJD OBJ(*LIBL/" + depName + ") OBJTYPE(" + depType + ") " +
                        "OUTPUT(*OUTFILE) OUTFILE(" + outLibrary + "/" + objdOutfile + ") OUTMBR(*FIRST *REPLACE)";
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CALL QSYS2.QCMDEXC('" + resolveCmd + "')");
      try (ResultSet rs = stmt.executeQuery(
          "SELECT CAST(ODLBNM AS VARCHAR(10) CCSID " + INVARIANT_CCSID + ") AS ODLBNM " +
          "FROM " + outLibrary + "." + objdOutfile)) {
        if (rs.next()) {
          return rs.getString("ODLBNM").trim();
        }
      }
    } catch (SQLException e) {
        if (verbose) e.printStackTrace();
    }
    return depLib;  // Fallback
  }

  // Kahn's algorithm (assumes no cycles; add detection if needed)
  // TODO:  Use a more formal graph lib like JGraphT 
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


