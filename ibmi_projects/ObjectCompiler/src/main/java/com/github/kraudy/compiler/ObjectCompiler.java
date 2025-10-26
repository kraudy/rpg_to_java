package com.github.kraudy.compiler;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;
import com.github.kraudy.migrator.SourceMigrator;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
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

    this.odes = new ObjectDescription(
          migrator,
          connection,
          debug,
          verbose,
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
    ParamMap ParamCmdSequence = new ParamMap(false); //Set this as false to not duplicated output.

    /* Parameters values, if provided, overwrite retrieved values */
    if (!text.isEmpty()) ParamCmdSequence.put(ParamCmd.TEXT, text);
    if (!actGrp.isEmpty()) ParamCmdSequence.put(ParamCmd.ACTGRP, actGrp);
    if (!modules.isEmpty()) {
      //TODO: Change these to *LIBL and set object library as curlib then DSPOBJ or something
      // can be used to resolve the actual library if needed
      StringBuilder sb = new StringBuilder(); 
      for (String mod: modules){
        sb.append(targetKey.library + "/" + mod);
        sb.append(" ");
      }
      ParamCmdSequence.put(ParamCmd.MODULE, sb.toString().trim());

      //TODO: Add this in object description and validate in comp pattern to remove it if srstmf is present
      //if (sourceStmf.isEmpty()) ParamCmdSequence.put(ParamCmd.EXPORT, CompilationPattern.ValCmd.ALL.toString());
    }
    if (!this.sourceStmf.isEmpty()) {
      ParamCmdSequence.put(ParamCmd.SRCSTMF, "'" + this.sourceStmf + "'");
      ParamCmdSequence.put(ParamCmd.TGTCCSID, ValCmd.JOB);
    }

    odes.setParamsSequence(ParamCmdSequence);

    if (odes.getSourceType() == null) {
      throw new IllegalArgumentException("Source type is required for new or unresolvable objects.");
    }
    if (debug) System.err.println("Source type: " + odes.getSourceType());

    cpat = new CompilationPattern(odes);

    if (debug) System.out.println("Compilation command: " + cpat.getCompilationCommand().name());

    String commandStr = cpat.buildCommand();
    
    if (debug) System.out.println("Full command: " + commandStr);

    /* 
    For OPM, create temp members if source is IFS (reverse migration).
    ParamCmdSequence.put(ParamCmd.SRCSTMF, stmfPath);
    migrator.IfsToMember(ParamCmdSequence.get(ParamCmd.SRCSTMF), Library);
    ParamCmdSequence.remove(ParamCmd.SRCFILE);  // Switch to stream file
    ParamCmdSequence.put(ParamCmd.SRCMBR, member);
    */

    // TODO: CHKOBJ OBJ(ROBKRAUDY2/CUSTOMER) OBJTYPE(*FILE)
    // DLTOBJ OBJ(ROBKRAUDY2/CUSTOMER) OBJTYPE(*FILE)
    // Maybe i can put these in another parameter, like, a pre or post pattern of commands using a map   

    compile(cpat.getCompilationCommand(), commandStr);

    cleanup();
  }

  

  private void cleanLibraryList(){
    executeCommand("CHGLIBL LIBL()"); 

  }

  private void setCurLib(String library){
    executeCommand("CHGCURLIB CURLIB(" + library + ")"); 
  }

  private void compile(CompCmd compilationCmd,String commandStr){
    try {
      executeCommand(compilationCmd, commandStr); 
    } catch (IllegalArgumentException e) {
      if (verbose) System.err.println("Compilation failed.");
      if (debug) e.printStackTrace();
    } finally {
      cleanup();
    }
  }

  private void executeCommand(CompCmd compilationCommand, String command){
    // Escape single quotes in commandStr for QCMDEXC
    String escapedCommand = command.replace("'", "''");

    if (debug) System.out.println("Sacaped command: " + escapedCommand);

    executeCommand(escapedCommand);
  }

  //TODO: Overwrite this with SysCmd and CompCmd, use another map for SysCmd to store the string.
  // to just call it with the Enum.
  private void executeCommand(String command){
    Timestamp commandTime = null;
    try (Statement stmt = connection.createStatement();
        ResultSet rsTime = stmt.executeQuery("SELECT CURRENT_TIMESTAMP AS Command_Time FROM sysibm.sysdummy1")) {
      if (rsTime.next()) {
        commandTime = rsTime.getTimestamp("Command_Time");
      }
    } catch (SQLException e) {
      if (verbose) System.err.println("Could not get command time.");
      if (debug) e.printStackTrace();
      throw new IllegalArgumentException("Could not get command time.");
    }

    try (Statement cmdStmt = connection.createStatement()) {
      cmdStmt.execute("CALL QSYS2.QCMDEXC('" + command + "')");
    } catch (SQLException e) {
      System.out.println("Command failed.");
      e.printStackTrace();
      getJoblogMessages(commandTime);
      throw new IllegalArgumentException("Could not execute command: " + command); //TODO: Catch this and throw the appropiate message
    }

    System.out.println("Command successful: " + command);
    getJoblogMessages(commandTime);
  }

  private void getJoblogMessages(Timestamp commandTime){
    // SQL0601 : Object already exists
    // CPF5813 : File CUSTOMER in library ROBKRAUDY2 already exists
    try (Statement stmt = connection.createStatement();
        ResultSet rsMessages = stmt.executeQuery(
          "SELECT MESSAGE_TIMESTAMP, MESSAGE_ID, SEVERITY, MESSAGE_TEXT, COALESCE(MESSAGE_SECOND_LEVEL_TEXT, '') As MESSAGE_SECOND_LEVEL_TEXT " +
          "FROM TABLE(QSYS2.JOBLOG_INFO('*')) " + 
          "WHERE FROM_USER = USER " +
          "AND MESSAGE_TIMESTAMP > '" + commandTime + "' " +
          "AND MESSAGE_ID NOT IN ('SQL0443', 'CPC0904', 'CPF2407') " +
          "ORDER BY MESSAGE_TIMESTAMP DESC "
        )) {
      while (rsMessages.next()) {
        Timestamp messageTime = rsMessages.getTimestamp("MESSAGE_TIMESTAMP");
        String messageId = rsMessages.getString("MESSAGE_ID").trim();
        String severity = rsMessages.getString("SEVERITY").trim();
        String message = rsMessages.getString("MESSAGE_TEXT").trim();
        String messageSecondLevel = rsMessages.getString("MESSAGE_SECOND_LEVEL_TEXT").trim();
        // Format the timestamp as a string
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formattedTime = sdf.format(messageTime);
        
        // Print in a formatted table-like structure
        System.out.printf("%-20s | %-10s | %-4s | %s%n", formattedTime, messageId, severity, message);
      } 
    } catch (SQLException e) {
      System.out.println("Could not get messages.");
      e.printStackTrace();
    }
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
