package com.github.kraudy.compiler;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import com.github.kraudy.compiler.CompilationPattern.SourceType;
import com.github.kraudy.compiler.CompilationPattern.SysCmd;

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
  public ParamMap ParamCmdSequence;
  private CommandExecutor commandExec;


  static class LibraryConverter implements CommandLine.ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
      try{
        value = value.trim().toUpperCase();
        if (value.length() > 10 || value.isEmpty()) {
          throw new Exception("Invalid library name: must be 1-10 characters");
        }
        return value;

      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid library name: " + value);
      }
    }
  }  

  static class ObjectNameConverter implements CommandLine.ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
      try{
        value = value.trim().toUpperCase();
        if (value.length() > 10 || value.isEmpty()) {
          throw new Exception("Invalid object name: must be 1-10 characters");
        }
        return value;

      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid object name: " + value);
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

  static class TargetKeyConverter implements CommandLine.ITypeConverter<Utilities.ParsedKey> {
    @Override
    public Utilities.ParsedKey convert(String value) throws Exception {
      try {
        return new Utilities.ParsedKey(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid target key: " + e.getMessage());
      }
    }
  }

  /* Object attributes. Required params */
  @Option(names = {"-tk","--target-key"}, description = "Target key: library.objectName.objectType[.sourceType] (e.g., MYLIB.HELLO.PGM.RPGLE)", converter = TargetKeyConverter.class)
  private Utilities.ParsedKey targetKey;

  /* Source-related params. Good to have */
  @Option(names = { "-sf", "--source-file" }, description = "Source physical file (defaults based on source type or retrieved from object)")
  private String sourceFile = "";

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name)")
  private String sourceName = "";

  @Option(names = { "-stmf", "--source-stmf" }, description = "Source stream file path in IFS (e.g., /home/sources/hello.rpgle). Overrides lib/sourceFile/name if provided.")
  private String sourceStmf = "";

  @Option(names = { "-mods","--modules"}, arity = "0..*", description = "Space-separated list of modules for SRVPGM (e.g., *CURLIB/HELLO2NENT *CURLIB/HELLO2BYE). Defaults to retrieved from existing object.")
  private List<String> modules = new ArrayList<>();

  /* Compilation flags. Optionals */
  @Option(names = { "--text" }, description = "Object text description (defaults to retrieved from object if possible)")
  private String text = "";

  @Option(names = { "--actgrp" }, description = "Activation group (defaults to retrieved from object if possible)")
  private String actGrp = "";

  @Option(names = "--dbgview", description = "Debug view (e.g., *ALL, *SOURCE, *LIST, *NONE). Defaults to *ALL.")
  private String dbgView = CompilationPattern.ValCmd.ALL.toString(); //"*ALL"

  @Option(names = "--bnddir", description = "Binding directories (space-separated). Defaults to *NONE.")
  private String bndDir = CompilationPattern.ValCmd.NONE.toString(); // "*NONE"

  /* yaml */
  @Option(names = {"-f", "--file"}, description = "YAML build file (instead of --target-key)")
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

    commandExec = new CommandExecutor(connection, debug, verbose, dryRun);
    //BuildSpec spec = new BuildSpec(this.debug, this.verbose, this.connection, this.dryRun);

    if (yamlFile != null) {
      File f = new File(yamlFile);
      if (!f.exists()) throw new RuntimeException("YAML file not found: " + yamlFile);
      try{
        /* Diserialize yaml file */
        spec = mapper.readValue(f, BuildSpec.class);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else if (targetKey != null) {
      // backward compatibility: single object mode
      //TODO: Maybe i should add a param here to override values or just create a new
      // paramMap for input ci values
      spec = new BuildSpec(targetKey);
    }

    /* Global before */
    //Make this better
    this.ParamCmdSequence = new ParamMap();

    if(!spec.before.isEmpty()){
      commandExec.executeCommand(spec.before);
    }

    showLibraryList();

    /* This is intended for a YAML file with multiple objects in a toposort order */
    //TODO: Here, to compile only object with changes, just use a diff and filter the keys.
    for (Map.Entry<Utilities.ParsedKey, BuildSpec.TargetSpec> entry : spec.targets.entrySet()) {
      Utilities.ParsedKey key = entry.getKey();
      BuildSpec.TargetSpec target = entry.getValue();

      try{
        // Per-target before (optional)

        //Utilities.ParsedKey key = new Utilities.ParsedKey(keyStr);
        
        // *** Reset per-target fields ***
        this.sourceFile = "";
        this.sourceName = "";
        this.sourceStmf = "";

        // Re-create migrator fresh for every target (safest)
        try {
          this.migrator = new SourceMigrator(this.system, this.connection, true, true);
        } catch (Exception e){
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Could not initialize migrator");
          throw new RuntimeException("Failed to initialize migrator: " + e.getMessage(), e);
        }

        this.ParamCmdSequence = new ParamMap();

        /* Per target before */
        if(!target.before.isEmpty()){
          commandExec.executeCommand(target.before);
        }


        CompCmd compilationCommand = CompilationPattern.getCompilationCommand(key.sourceType, key.objectType);

        sourceFile = sourceFile.isEmpty() ? SourceType.defaultSourcePf(key.sourceType, key.objectType) : sourceFile;
        sourceName = sourceName.isEmpty() ? key.objectName : sourceName;

        this.odes = new ObjectDescription(
              connection, debug, verbose, compilationCommand, key,
              //TODO: Remove these and change it in the key
              sourceFile, sourceName
        );

        this.odes.SetCompilationParams(this.ParamCmdSequence);

        try {
          odes.getObjectInfo(this.ParamCmdSequence, compilationCommand);
        } catch (Exception e) {
          //TODO: Change logging for SLF4J or java.util.logging 
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Object not found; using defaults.");
        }

        /* Parameters values, if provided, overwrite retrieved values */
        if (!text.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.TEXT, text);
        if (!actGrp.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.ACTGRP, actGrp);
        if (!modules.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.MODULE, modules);

        /* Set global defaults params per target */
        ParamCmdSequence.putAll(compilationCommand, spec.defaults);

        /* Set specific target params */
        ParamCmdSequence.putAll(compilationCommand, target.params);

        //if (!this.sourceStmf.isEmpty()) {
        //  ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, this.sourceStmf);
        //  ParamCmdSequence.put(compilationCommand, ParamCmd.TGTCCSID, ValCmd.JOB);
        //}


        switch (compilationCommand){
          case CRTCLMOD:
            break;

          case CRTRPGMOD:
          case CRTBNDRPG:
          case CRTBNDCL:
          case CRTSQLRPGI:
          case CRTSRVPGM:
          case RUNSQLSTM:
            if (!ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCSTMF)) {
              System.out.println("SRCFILE data: " + ParamCmdSequence.get(compilationCommand, ParamCmd.SRCFILE));
              //TODO: This could be done directly in ObjectCompiler
              this.migrator.setParams(ParamCmdSequence.get(compilationCommand, ParamCmd.SRCFILE), key.objectName, "sources");
              this.migrator.api(); // Try to migrate this thing
              
              ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, this.migrator.getFirstPath());
            }
            break;

          case CRTCLPGM:
          case CRTRPGPGM:
            /* 
            For OPM, create temp members if source is IFS (reverse migration).
            ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, stmfPath);
            migrator.IfsToMember(ParamCmdSequence.get(ParamCmd.SRCSTMF), Library);
            ParamCmdSequence.remove(ParamCmd.SRCFILE);  // Switch to stream file
            ParamCmdSequence.put(compilationCommand, ParamCmd.SRCMBR, member);
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
        commandExec.executeCommand(ParamCmdSequence.getCommandString(compilationCommand));

      } catch (Exception e){
        System.err.println("Target failed: " + targetKey.toString());
        e.printStackTrace();

        /* Per target failure */
        //if(!target.onError.isEmpty()){
        //  commandExec.executeCommand(target.onError);
        //} 

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
