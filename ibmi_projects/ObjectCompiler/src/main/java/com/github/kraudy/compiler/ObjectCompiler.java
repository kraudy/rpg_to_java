package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Cool object compiler.
 */
public class ObjectCompiler{
  private static final Logger logger = LoggerFactory.getLogger(ObjectCompiler.class); // Per-class logger

  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  public static final String UTF8_CCSID = "1208";
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;
  private CommandExecutor commandExec;
  private Migrator migrator;
  private ObjectDescription odes;
  private SourceDescriptor sourceDes;

  private BuildSpec globalSpec;     // global build spec
  private boolean dryRun = false;   // Compile commands without executing 
  private boolean debug = false;    // Debug flag
  private boolean verbose = false;  // Verbose output flag
  private boolean diff = false;     // Diff build flag


  public ObjectCompiler(AS400 system) throws Exception {
    this(system, new AS400JDBCDataSource(system).getConnection());
  }

  public ObjectCompiler(AS400 system, Connection connection) throws Exception {
    this.system = system;

    // Database
    this.connection = connection;
    this.connection.setAutoCommit(true);

    // User
    this.currentUser = new User(system, system.getUserId());
    this.currentUser.loadUserInformation();

  }

  public ObjectCompiler(AS400 system, BuildSpec globalSpec, boolean dryRun, boolean debug, boolean verbose, boolean diff) throws Exception {
    this(system, new AS400JDBCDataSource(system).getConnection());

    /* Set params */
    this.globalSpec = globalSpec;
    this.dryRun = dryRun;
    this.debug = debug;
    this.verbose = verbose;
    this.diff = diff;
  }

  public void build() {

    /* Init command executor */
    commandExec = new CommandExecutor(connection, debug, verbose, dryRun);

    /* Init migrator */
    migrator = new Migrator(connection, debug, verbose, currentUser, commandExec);

    /* Init source descriptor */
    sourceDes = new SourceDescriptor(connection, debug, verbose);

    /* Init object descriptor */
    odes = new ObjectDescription(connection, debug, verbose);

    try {
      /* Global before */
      if(!globalSpec.before.isEmpty()){
        commandExec.executeCommand(globalSpec.before);
      }

      //if(verbose) System.err.println(showLibraryList());
      if(verbose) logger.info(showLibraryList());

      /* Build each target */
      buildTargets(globalSpec.targets);

      /* Execute global after */
      if(!globalSpec.after.isEmpty()){
        commandExec.executeCommand(globalSpec.after);
      }

      /* Execute global success */
      if(!globalSpec.success.isEmpty()){
        commandExec.executeCommand(globalSpec.success);
      }
      
    } catch (CompilerException e){
      //if (verbose) System.err.println("Compilation failed");
      if (verbose) logger.error("Compilation failed");

      /* Global compiler failure */
      try{
        if(!globalSpec.failure.isEmpty()){
          commandExec.executeCommand(globalSpec.failure);
        }
      } catch (Exception failureErr) {
          throw new CompilerException("Target failure hook also failed", failureErr);
          //logger.error(new CompilerException("Target failure hook also failed", failureErr).getFullContext());
      }

      /* Get full compiler exception context */
      //System.err.println(e.getFullContext());
      logger.error(e.getFullContext());

    } catch (Exception e) {
      /* Unhandled Exception. Fail loudly */
      //e.printStackTrace();
      logger.error("Unhandled Exception. Fail loudly", e);

    } finally {
      /* Show chain of commands */
      //if (verbose) System.err.println("Chain of commands: " + commandExec.getExecutionChain());
      if (verbose) logger.info("Chain of commands: {}", commandExec.getExecutionChain());

      cleanup();
    }

  }

  private void buildTargets(LinkedHashMap<TargetKey, BuildSpec.TargetSpec> targets) throws Exception{
    /* This is intended for a YAML file with multiple objects in a toposort order */
    for (Map.Entry<TargetKey, BuildSpec.TargetSpec> entry : targets.entrySet()) {
      TargetKey key = entry.getKey();
      BuildSpec.TargetSpec targetSpec = entry.getValue();

      /* Skip target if diff and no build required */
      if (diff) {
        sourceDes.getObjectTimestamps(key);
        if (!key.needsRebuild()) {
          if (verbose) System.out.println("Skipping unchanged target: " + key.asString() + key.getTimestmaps());
          continue; 
        }
      }

      //if (verbose) System.out.println("Building: " + key.asString());
      if (verbose) logger.info("Building: " + key.asString());

      try{
        /* Per target before */
        if(!targetSpec.before.isEmpty()){
          commandExec.executeCommand(targetSpec.before);
        }

        /* If the object exists, we try to extract its compilation params */
        odes.getObjectInfo(key);

        /* Set global defaults params per target */
        key.putAll(globalSpec.defaults);

        /* Set specific target params */
        key.putAll(targetSpec.params);

        /* Migrate source file */
        migrator.migrateSource(key);

        /* Execute compilation command */
        commandExec.executeCommand(key);

        /* Per target after */
        if(!targetSpec.after.isEmpty()){
          commandExec.executeCommand(targetSpec.after);
        } 

        /* Per target success */
        if(!targetSpec.success.isEmpty()){
          commandExec.executeCommand(targetSpec.success);
        } 

      } catch (CompilerException e){
        //if (verbose) System.err.println("Target compilation failed: " + key.asString());
        if (verbose) logger.error("Target compilation failed: " + key.asString());

        /* Per target failure */
        if(!targetSpec.failure.isEmpty()){
          commandExec.executeCommand(targetSpec.failure);
        } 

        throw e; // Raise

      } catch (Exception e){
        //if (verbose) System.err.println("Unhandled exception in Target: " + key.asString());
        if (verbose) logger.error("Unhandled exception in Target: " + key.asString());

        throw e; // Raise

      } finally {
        //TODO: Here do Logging, Reset or something
      }
    }

  }  

  private String showLibraryList() throws SQLException{
    StringBuilder sb = new StringBuilder();;
    sb.append("Library list: \n");
    //System.out.println("Library list: ");
    try(Statement stmt = connection.createStatement();
        ResultSet rsLibList = stmt.executeQuery(
          "SELECT DISTINCT(SCHEMA_NAME) As Libraries FROM QSYS2.LIBRARY_LIST_INFO " + 
          "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400')"
          
        )){
      while (rsLibList.next()) {
        //System.out.println(rsLibList.getString("Libraries"));
        sb.append(rsLibList.getString("Libraries")).append("\n");
      }
      return sb.toString();

    } catch (SQLException e){
      //System.err.println("Could not get library list.");
      logger.error("Error retrieving library list");
      throw e;
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

  public static void main(String... args ){
    AS400 system = null;
    ObjectCompiler compiler = null;
    try {
      ArgParser parser = new ArgParser(args);
      //TODO: This should be able to run locally in debug mode.
      if (args.length == 0) ArgParser.printUsage();
        
      system = IBMiDotEnv.getNewSystemConnection(true); // Get system
      compiler = new ObjectCompiler(
            system,
            parser.getSpecFromYamlFile(),
            parser.isDryRun(),
            parser.isDebug(),
            parser.isVerbose(),
            parser.isDiff()
        );
      compiler.build();

    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      ArgParser.printUsage();
    } catch (CompilerException e){
      System.err.println(e.getFullContext());

    }catch (Exception e) {
      e.printStackTrace();
    }
  }
}
