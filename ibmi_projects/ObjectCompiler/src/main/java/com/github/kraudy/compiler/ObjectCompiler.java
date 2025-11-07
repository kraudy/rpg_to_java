package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;
import com.github.kraudy.compiler.ObjectDescription.SysCmd;
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
  private CompilationPattern cpat;
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

  static class ObjectTypeConverter implements CommandLine.ITypeConverter<ObjectDescription.ObjectType> {
    @Override
    public ObjectDescription.ObjectType convert(String value) throws Exception {
      try {
        return ObjectDescription.ObjectType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid object type: " + value);
      }
    }
  }  

  static class SourceTypeConverter implements CommandLine.ITypeConverter<ObjectDescription.SourceType> {
    @Override
    public ObjectDescription.SourceType convert(String value) throws Exception {
      try {
        return ObjectDescription.SourceType.valueOf(value.trim().toUpperCase());
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

  @Option(names = {"-tk","--target-key"}, required = true, description = "Target key: library.objectName.objectType[.sourceType] (e.g., MYLIB.HELLO.PGM.RPGLE)", converter = TargetKeyConverter.class)
  private Utilities.ParsedKey targetKey;

  /* Object attributes. Required params */
  /*
  //TODO: Change to target library
  @Option(names = { "-l", "--lib" }, required = true, description = "Target library for object", converter = LibraryConverter.class)
  private String library;

  //TODO: --obj, -t and -st could be made in one: hello.pgm.rpgle and just parse it. Maybe the last could be optional if the object is found.
  // format: objLibrary.objectName.objectType.sourceType?, This will match the toposort keys.
  @Option(names = "--obj", required = true, description = "Object name", converter = ObjectNameConverter.class)
  private String objectName;

  @Option(names = {"-t","--type"}, required = true, description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectDescription.ObjectType objectType;
   */

  /* Source-related params. Good to have */
  @Option(names = { "-sl", "--source-lib" }, description = "Source library (defaults to *LIBL or retrieved from object)", converter = LibraryConverter.class)
  private String sourceLib = CompilationPattern.ValCmd.LIBL.toString(); //"*LIBL"

  @Option(names = { "-sf", "--source-file" }, description = "Source physical file (defaults based on source type or retrieved from object)")
  private String sourceFile = "";

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name, command-specific *SPECIAL, or retrieved from object)")
  private String sourceName = "";

  @Option(names = { "-stmf", "--source-stmf" }, description = "Source stream file path in IFS (e.g., /home/sources/hello.rpgle). Overrides source-lib/file/name if provided.")
  private String sourceStmf = "";

  @Option(names = { "-mods","--modules"}, arity = "0..*", description = "Space-separated list of modules for SRVPGM (e.g., *CURLIB/HELLO2NENT *CURLIB/HELLO2BYE). Defaults to retrieved from existing object.")
  private List<String> modules = new ArrayList<>();

  //TODO: Should this be part of the key?
  @Option(names = {"-st","--source-type"}, description = "Source type (e.g., RPGLE, CLLE) (defaults to retrieved from object if possible)", converter = SourceTypeConverter.class)
  private ObjectDescription.SourceType sourceType;

  /* Compilation flags. Optionals */
  @Option(names = { "--text" }, description = "Object text description (defaults to retrieved from object if possible)")
  private String text = "";

  @Option(names = { "--actgrp" }, description = "Activation group (defaults to retrieved from object if possible)")
  private String actGrp = "";

  @Option(names = "--dbgview", description = "Debug view (e.g., *ALL, *SOURCE, *LIST, *NONE). Defaults to *ALL.")
  private String dbgView = CompilationPattern.ValCmd.ALL.toString(); //"*ALL"

  @Option(names = "--optimize", description = "Optimization level (e.g., *NONE, 10, 20, 30, 40). Defaults to 10.")
  private String optimize = "10";

  @Option(names = "--tgtrls", description = "Target release (e.g., *CURRENT, V7R4M0). Defaults to retrieved or *CURRENT.")
  private String tgtRls = CompilationPattern.ValCmd.CURRENT.toString();  //"*CURRENT"

  @Option(names = "--option", description = "Compile options (e.g., *EVENTF, *SRCDBG). Defaults based on type.")
  private String option = "";

  @Option(names = "--bnddir", description = "Binding directories (space-separated). Defaults to *NONE.")
  private String bndDir = CompilationPattern.ValCmd.NONE.toString(); // "*NONE"

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

    //TODO: Add --dry-run to just run without executing. Just to generate the command string
    this.ParamCmdSequence = new ParamMap(this.debug, this.verbose, this.connection);
    cleanLibraryList();

    // Migrator
    try {
      this.migrator = new SourceMigrator(this.system, this.connection, true, true);
    } catch (Exception e){
      if (debug) e.printStackTrace();
      if (verbose) System.err.println("Could not initialize migrator");
      throw new RuntimeException("Failed to initialize migrator: " + e.getMessage(), e);
    }

    setCurLib(targetKey.library);

    showLibraryList();

    //this.targetKey = this.targetKey.withLibrary(ValCmd.LIBL.toString());

    this.odes = new ObjectDescription(
          connection,
          debug,
          verbose,
          ParamCmdSequence,
          //TODO: Maybe i should pass these as the topo key. The Key should uniquely indentify an object
          targetKey,
          sourceLib, // Default to *LIBL
          sourceFile,
          sourceName
    );

    //TODO: Set library as curlib and change value to CURLIB, maybe use put for chain tracking

    try {
      odes.getObjectInfo();
    } catch (Exception e) {
      //TODO: Change logging for SLF4J or java.util.logging 
      if (debug) e.printStackTrace();
      if (verbose) System.err.println("Object not found; using defaults.");
    }

    //Map<CompilationPattern.ParamCmd, String> ParamCmdSequence = new HashMap<>();
    CompCmd compilationCommand = CompilationPattern.getCompilationCommand(this.targetKey.sourceType, this.targetKey.objectType);
    //ParamMap ParamCmdSequence = new ParamMap(false); //Set this as false to not duplicated output.
    ParamMap ParamCmdSequence = odes.getParamCmdSequence();

    /* Parameters values, if provided, overwrite retrieved values */
    if (!text.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.TEXT, text);
    if (!actGrp.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.ACTGRP, actGrp);
    if (!modules.isEmpty()) {
      //TODO: Change these to *LIBL and set object library as curlib then DSPOBJ or something
      // can be used to resolve the actual library if needed
      StringBuilder sb = new StringBuilder(); 
      for (String mod: modules){
        sb.append(targetKey.library + "/" + mod);
        sb.append(" ");
      }
      ParamCmdSequence.put(compilationCommand, ParamCmd.MODULE, sb.toString().trim());

      //TODO: Add this in object description and validate in comp pattern to remove it if srstmf is present
      //if (sourceStmf.isEmpty()) ParamCmdSequence.put(compilationCommand, ParamCmd.EXPORT, CompilationPattern.ValCmd.ALL.toString());
    }
    if (!this.sourceStmf.isEmpty()) {
      ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, "'" + this.sourceStmf + "'");
      ParamCmdSequence.put(compilationCommand, ParamCmd.TGTCCSID, ValCmd.JOB);
    }

    if (odes.getSourceType() == null) {
      throw new IllegalArgumentException("Source type is required for new or unresolvable objects.");
    }
    if (debug) System.err.println("Source type: " + odes.getSourceType());

    cpat = new CompilationPattern(this.migrator, ParamCmdSequence, compilationCommand, targetKey.objectName);

    ParamCmdSequence = cpat.getParamCmdSequence();

    ParamCmdSequence.executeCommand(compilationCommand);    

    //TODO: Idea: Crete a cursor of library list and iter over it to execute this command. Sound interesting, i don't know if it is useful.
    // TODO: CHKOBJ OBJ(ROBKRAUDY2/CUSTOMER) OBJTYPE(*FILE)
    // DLTOBJ OBJ(ROBKRAUDY2/CUSTOMER) OBJTYPE(*FILE)
    // Maybe i can put these in another parameter, like, a pre or post pattern of commands using a map   

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
