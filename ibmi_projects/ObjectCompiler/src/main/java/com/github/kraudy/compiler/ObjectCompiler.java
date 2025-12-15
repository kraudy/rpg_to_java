package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import com.github.kraudy.migrator.SourceMigrator;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command (name = "compiler", description = "OPM/ILE Object Compiler", mixinStandardHelpOptions = true, version = "ObjectCompiler 0.0.1")
public class ObjectCompiler implements Runnable{
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;
  //private SourceMigrator migrator;
  private CommandExecutor commandExec;

  /* yaml */
  @Option(names = {"-f", "--file"}, description = "YAML build file")
  private String yamlFile;

  @Option(names = {"--dry-run"}, description = "Show commands without executing")
  private boolean dryRun = false;

  /* Options */
  @Option(names = "-x", description = "Debug")
  private boolean debug = false;

  @Option(names = "-v", description = "Verbose output")
  private boolean verbose = false;


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

  public void run() {

    /* Init command executor */
    commandExec = new CommandExecutor(connection, debug, verbose, dryRun);

    /* Get build globalSpec from yaml file */
    BuildSpec globalSpec = Utilities.deserializeYaml(yamlFile);

    /* Global before */
    if(!globalSpec.before.isEmpty()){
      commandExec.executeCommand(globalSpec.before);
    }

    if(verbose) showLibraryList();

    /* Build each target */
    buildTargets(globalSpec);

    /* Execute global after */
    if(!globalSpec.after.isEmpty()){
      commandExec.executeCommand(globalSpec.after);
    }
    
    /* Show chain of commands */
    if(verbose) System.out.println(commandExec.getExecutionChain());

    cleanup();
  }

  private void buildTargets(BuildSpec globalSpec){
    /* This is intended for a YAML file with multiple objects in a toposort order */
    //TODO: Here, to compile only object with changes, just use a diff and filter the keys. Coming soon.
    for (Map.Entry<TargetKey, BuildSpec.TargetSpec> entry : globalSpec.targets.entrySet()) {
      TargetKey key = entry.getKey();
      BuildSpec.TargetSpec targetSpec = entry.getValue();

      try{
        /* Per target before */
        if(!targetSpec.before.isEmpty()){
          commandExec.executeCommand(targetSpec.before);
        }

        /* Init object descriptor */
        ObjectDescription odes = new ObjectDescription(connection, debug, verbose, key);

        /* Set default compilation params */
        odes.SetCompilationParams();

        /* If the object exists, we try to extract its compilation params */
        try {
          odes.getObjectInfo();
        } catch (Exception e) {
          //TODO: Change logging for SLF4J or java.util.logging. This is also needed for pipeline integration.
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Object not found; only default or provided values will be used.");
        }

        /* Set global defaults params per target */
        key.putAll(globalSpec.defaults);

        /* Set specific target params */
        key.putAll(targetSpec.params);

        /* Migrate source file */
        // Re-create migrator fresh for every target, fix this.
        try {
          SourceMigrator migrator = new SourceMigrator(this.system, this.connection, true, true);
          odes.migrateSource(migrator);
        } catch (Exception e){
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Could not initialize migrator");
          throw new RuntimeException("Failed to initialize migrator: " + e.getMessage(), e);
        }

        /* Execute compilation command */
        commandExec.executeCommand(key.getCommandString());

        /* Per target success */
        if(!targetSpec.success.isEmpty()){
          commandExec.executeCommand(targetSpec.success);
        } 

        /* Per target after */
        if(!targetSpec.after.isEmpty()){
          commandExec.executeCommand(targetSpec.after);
        } 

      } catch (Exception e){
        if (debug) e.printStackTrace();
        if (verbose) System.err.println("Target failed: " + key.toString());

        /* Per target failure */
        if(!targetSpec.failure.isEmpty()){
          commandExec.executeCommand(targetSpec.failure);
        } 

        /* Global failure */
        if(!globalSpec.failure.isEmpty()){
          commandExec.executeCommand(globalSpec.failure);
        }

        return; // Stop compilation

      } finally {
        //TODO: What should i add here? Logging or something
      }
    }

    /* Execute global success */
    if(!globalSpec.success.isEmpty()){
      commandExec.executeCommand(globalSpec.success);
    }
  }

  private void showLibraryList(){
    System.out.println("Library list: ");
    try(Statement stmt = connection.createStatement();
        ResultSet rsLibList = stmt.executeQuery(
          "SELECT DISTINCT(SCHEMA_NAME) As Libraries FROM QSYS2.LIBRARY_LIST_INFO " + 
          "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400')"
          
        )){
      while (rsLibList.next()) {
        System.out.println(rsLibList.getString("Libraries"));
      }
    } catch (SQLException e){
      System.err.println("Could not get library list.");
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
    ObjectCompiler compiler = null;
    try {
      system = IBMiDotEnv.getNewSystemConnection(true); // Get system
      compiler = new ObjectCompiler(system);
      new CommandLine(compiler).execute(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
