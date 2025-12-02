package com.github.kraudy.compiler;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
      spec = new BuildSpec();
      spec.targets.put(targetKey.asString(), new BuildSpec.TargetSpec());
    }

    /* This is intended for a YAML file with multiple objects in toposort order */
    for (Map.Entry<String, BuildSpec.TargetSpec> entry : spec.targets.entrySet()) {
      String keyStr = entry.getKey();
      BuildSpec.TargetSpec target = entry.getValue();

      try{
        // Per-target before (optional)

        Utilities.ParsedKey key = new Utilities.ParsedKey(keyStr);
        
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

        //TODO: Add --dry-run to just run without executing. Just to generate the command string
        this.ParamCmdSequence = new ParamMap(this.debug, this.verbose, this.connection, this.dryRun);

        //TODO: Only add them using .put and then call the execution command
        //ParamCmdSequence.executeRawCommands(target.before, "before (target: " + keyStr + ")");
        //ParamCmdSequence.put(spec.before);

        cleanLibraryList();
        setCurLib(key.library);

        showLibraryList();

        CompCmd compilationCommand = CompilationPattern.getCompilationCommand(key.sourceType, key.objectType);

        sourceFile = sourceFile.isEmpty() ? SourceType.defaultSourcePf(key.sourceType, key.objectType) : sourceFile;
        sourceName = sourceName.isEmpty() ? key.objectName : sourceName;

        this.odes = new ObjectDescription(
              connection, debug, verbose, compilationCommand, key,
              //TODO: Remove these and change it in the key
              sourceFile, sourceName
        );

        this.ParamCmdSequence = this.odes.SetCompilationParams(this.ParamCmdSequence);

        try {
          this.ParamCmdSequence = odes.getObjectInfo(this.ParamCmdSequence, compilationCommand);
        } catch (Exception e) {
          //TODO: Change logging for SLF4J or java.util.logging 
          if (debug) e.printStackTrace();
          if (verbose) System.err.println("Object not found; using defaults.");
        }

        /* Parameters values, if provided, overwrite retrieved values */
        if (!text.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.TEXT, text);
        if (!actGrp.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.ACTGRP, actGrp);
        if (!modules.isEmpty()) {
          StringBuilder sb = new StringBuilder(); 
          for (String mod: modules){
            sb.append(ValCmd.LIBL.toString() + "/" + mod);
            sb.append(" ");
          }
          ParamCmdSequence.put(compilationCommand, ParamCmd.MODULE, sb.toString().trim());

        }

        /* Set global defaults params */
        ParamCmdSequence.put(compilationCommand, spec.defaults);
        /* Set specific target params */
        ParamCmdSequence.put(compilationCommand, target.params);

        if (!this.sourceStmf.isEmpty()) {
          ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, "'" + this.sourceStmf + "'");
          ParamCmdSequence.put(compilationCommand, ParamCmd.TGTCCSID, ValCmd.JOB);
        }


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
              System.out.println("After calling migration api");
              
              ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, this.migrator.getFirstPath());
              ParamCmdSequence.put(compilationCommand, ParamCmd.TGTCCSID, ValCmd.JOB); // Needed to compile from stream files
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

        ParamCmdSequence.executeCommand(compilationCommand);    
        
        // Per-target success hook
        ParamCmdSequence.executeRawCommands(target.onSuccess, "onSuccess (target: " + keyStr + ")");

        System.out.println(ParamCmdSequence.getExecutionChain());

      } catch (Exception e){
        System.err.println("Target failed: " + keyStr);
        e.printStackTrace();

        // Run failure hooks
        ParamCmdSequence.executeRawCommands(target.onFailure, "onFailure (target: " + keyStr + ")");
      } finally {
        // Always run per-target after
        ParamCmdSequence.executeRawCommands(target.after, "after (target: " + keyStr + ")");
      }
    }
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

  private void cleanLibraryList(){
    this.ParamCmdSequence.put(SysCmd.CHGLIBL, ParamCmd.LIBL, "");
    this.ParamCmdSequence.executeCommand(SysCmd.CHGLIBL);

  }

  //TODO: Should i do ADDLIBLE library *FIRST too?
  private void setCurLib(String library){
    this.ParamCmdSequence.put(SysCmd.CHGCURLIB, ParamCmd.CURLIB, library);
    this.ParamCmdSequence.executeCommand(SysCmd.CHGCURLIB);
  }


  //TODO: This is kinda slow.
  // String cpysplfCmd = "CPYSPLF FILE(" + objectName + ") TOFILE(QTEMP/SPLFCPY) JOB(*) SPLNBR(*LAST)";
  // Or send it to a stream file
  // Try to use CPYSPLF to a stream file or db2 table
  /*  https://gist.github.com/BirgittaHauser/f28e3527f1cc4c422a05eea865b455bb */
  private void showCompilationSpool(Timestamp compilationTime, String user, String objectName) throws SQLException{

    System.out.println("Compiler error messages: \n");

    try(Statement stmt = connection.createStatement();
      ResultSet rsCompilationSpool = stmt.executeQuery(
        "With " +
        "Spool as ( " +
          "Select b.ordinal_position, Spooled_Data " + 
          "from  qsys2.OutPut_Queue_Entries a Cross Join " +
              "Lateral(Select * " +
                        "From Table(SysTools.Spooled_File_Data( " +
                                                "Job_Name            => a.Job_Name, " +
                                                "Spooled_File_Name   => a.Spooled_File_Name, " +
                                                "Spooled_File_Number => File_Number))) b " +
          "Where     Output_Queue_Name = '" + user + "' " +
                "and USER_NAME = '" + user + "' " + 
                "and SPOOLED_FILE_NAME = '" + objectName + "' " +
                "and OUTPUT_QUEUE_LIBRARY_NAME = 'QGPL' " +
                "and CREATE_TIMESTAMP > '" + compilationTime + "' " +
        "), " +
        "Message As ( " +
          "Select ordinal_position From Spool Where Spooled_Data like '%M e s s a g e   S u m m a r y%' " +
        ") " +
        "Select RTrim(Cast(Spooled_Data As Varchar(132) CCSID " + INVARIANT_CCSID +" )) As  Spooled_Data " + 
        "from Spool Where ordinal_position >= (Select ordinal_position From Message) "
      )){
        while (rsCompilationSpool.next()) {
          System.out.println(rsCompilationSpool.getString("Spooled_Data"));
        }
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
