package com.github.kraudy.compiler;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

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
  private ObjectDescription odes;
  private SourceMigrator migrator;
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
    /* Try to get compilation params from object. If it exists. */

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    BuildSpec spec = null;

    /* Init command executor */
    commandExec = new CommandExecutor(connection, debug, verbose, dryRun);

    //TODO: Leave only the yaml here, i think i'll remove picocli and just use a scanner or something.
    if (yamlFile == null) {
      throw new RuntimeException("YAML build file must be provided");
    }
    
    File f = new File(yamlFile);
    if (!f.exists()) throw new RuntimeException("YAML file not found: " + yamlFile);

    try{
      /* Diserialize yaml file */
      spec = mapper.readValue(f, BuildSpec.class);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Could not map build file to spec");
    }

    /* Global before */
    if(!spec.before.isEmpty()){
      commandExec.executeCommand(spec.before);
    }

    if(debug || verbose) showLibraryList();

    /* This is intended for a YAML file with multiple objects in a toposort order */
    //TODO: Here, to compile only object with changes, just use a diff and filter the keys.
    for (Map.Entry<Utilities.TargetKey, BuildSpec.TargetSpec> entry : spec.targets.entrySet()) {
      Utilities.TargetKey key = entry.getKey();
      BuildSpec.TargetSpec target = entry.getValue();

      try{
        // Re-create migrator fresh for every target, fix this.
        try {
          this.migrator = new SourceMigrator(this.system, this.connection, true, true);
        } catch (Exception e){
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Could not initialize migrator");
          throw new RuntimeException("Failed to initialize migrator: " + e.getMessage(), e);
        }

        /* Per target before */
        if(!target.before.isEmpty()){
          commandExec.executeCommand(target.before);
        }

        //TODO: Override source file and sourcename directly in the key with the yaml params

        this.odes = new ObjectDescription(connection, debug, verbose, key);

        this.odes.SetCompilationParams(key.getParamMap());

        try {
          odes.getObjectInfo();
        } catch (Exception e) {
          //TODO: Change logging for SLF4J or java.util.logging 
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Object not found; using defaults.");
        }

        /* Set global defaults params per target */
        key.getParamMap().putAll(key.getCompilationCommand(), spec.defaults);

        /* Set specific target params */
        key.getParamMap().putAll(key.getCompilationCommand(), target.params);

        //TODO: Move this to objectDescriptor or something.
        switch (key.getCompilationCommand()){
          case CRTCLMOD:
            break;

          case CRTRPGMOD:
          case CRTBNDRPG:
          case CRTBNDCL:
          case CRTSQLRPGI:
          case CRTSRVPGM:
          case RUNSQLSTM:
            if (!key.getParamMap().containsKey(key.getCompilationCommand(), ParamCmd.SRCSTMF)) {
              System.out.println("SRCFILE data: " + key.getParamMap().get(key.getCompilationCommand(), ParamCmd.SRCFILE));
              //TODO: This could be done directly in ObjectCompiler
              this.migrator.setParams(key.getParamMap().get(key.getCompilationCommand(), ParamCmd.SRCFILE), key.objectName, "sources");
              this.migrator.api(); // Try to migrate this thing
              
              key.getParamMap().put(key.getCompilationCommand(), ParamCmd.SRCSTMF, this.migrator.getFirstPath());
            }
            break;

          case CRTCLPGM:
          case CRTRPGPGM:
            /* 
            For OPM, create temp members if source is IFS (reverse migration).
            key.getParamMap().put(key.getCompilationCommand(), ParamCmd.SRCSTMF, stmfPath);
            migrator.IfsToMember(key.getParamMap().get(ParamCmd.SRCSTMF), Library);
            key.getParamMap().remove(ParamCmd.SRCFILE);  // Switch to stream file
            key.getParamMap().put(key.getCompilationCommand(), ParamCmd.SRCMBR, member);
            */
            break;

          case CRTDSPF:
          case CRTPF:
          case CRTLF:
          case CRTPRTF:
          case CRTMNU:
          case CRTQMQRY:
              break;
        }

        /* Execute compilation command */
        commandExec.executeCommand(key.getParamMap().getCommandString(key.getCompilationCommand()));

      } catch (Exception e){
        System.err.println("Target failed: " + key.toString());
        e.printStackTrace();

        /* Per target failure */
        //if(!target.onError.isEmpty()){
        //  commandExec.executeCommand(target.onError);
        //} 

        break;

      } finally {
        /* Per target after */
        if(!target.after.isEmpty()){
          commandExec.executeCommand(target.after);
        } 
      }
    }

    /* Execute global after */
    if(!spec.after.isEmpty()){
      commandExec.executeCommand(spec.after);
    }
    
    System.out.println(commandExec.getExecutionChain());

    cleanup();
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
