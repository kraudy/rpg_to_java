package com.github.kraudy.compiler;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.HashMap;
import java.util.Map;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
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

  /* Object attributes. Required params */
  //TODO: Change to target library
  @Option(names = { "-l", "--lib" }, required = true, description = "Target library for object", converter = LibraryConverter.class)
  private String library;

  //TODO: --obj, -t and -st could be made in one: hello.pgm.rpgle and just parse it. Maybe the last could be optional if the object is found.
  // format: objLibrary.objectName.objectType.sourceType?, This will match the toposort keys.
  @Option(names = "--obj", required = true, description = "Object name", converter = ObjectNameConverter.class)
  private String objectName;

  @Option(names = {"-t","--type"}, required = true, description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectDescription.ObjectType objectType;

  /* Source-related params. Good to have */
  @Option(names = { "-sl", "--source-lib" }, description = "Source library (defaults to *LIBL or retrieved from object)", converter = LibraryConverter.class)
  private String sourceLib = CompilationPattern.ValCmd.LIBL.toString(); //"*LIBL"

  @Option(names = { "-sf", "--source-file" }, description = "Source physical file (defaults based on source type or retrieved from object)")
  private String sourceFile = "";

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name, command-specific *SPECIAL, or retrieved from object)")
  private String sourceName = "";

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

  @Option(names = "--stgmdl", description = "Storage model (*SNGLVL, *TERASPACE, *INHERIT). Defaults to *SNGLVL.")
  private String stgMdl = CompilationPattern.ValCmd.SNGLVL.toString(); // "*SNGLVL"

  // Add more: --define (for macros), --inline, --sysifcopt, --teraspace, etc.

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

    Map<CompilationPattern.ParamCmd, String> ParamCmdSequence = new HashMap<>();
    
    /* Try to get compilation params from object. If it exists. */
    this.odes = new ObjectDescription(
          connection,
          debug,
          //TODO: Maybe i should pass these as the topo key. The Key should uniquely indentify an object
          library,
          objectName,
          objectType,
          sourceLib, // Default to *LIBL
          sourceFile,
          sourceName,
          sourceType // Specified or inferred
    );

    try {
      odes.getObjectInfo();
    } catch (Exception e) {
      //if (verbose) System.err.println("Warning: Could not retrieve compilation params from object: " + e.getMessage() + ". Using defaults.");
      e.printStackTrace();
    }

    /* Parameters values, if provided, overwrite retrieved values */
    if (!text.isEmpty()) ParamCmdSequence.put(ParamCmd.TEXT, "'" + text +"'");
    if (!actGrp.isEmpty()) ParamCmdSequence.put(ParamCmd.ACTGRP, actGrp);
    odes.setParamsSequence(ParamCmdSequence);

    if (odes.getSourceType() == null) {
      System.err.println("Source type is required if not retrievable from object.");
      return;
    }
    if (debug) System.err.println("Source type: " + odes.getSourceType());

    cpat = new CompilationPattern(odes);

    if (debug) System.out.println("Compilation command: " + cpat.getCompilationCommand().name());

    String commandStr = cpat.buildCommand();
    
    if (debug) System.out.println("Full command: " + commandStr);

    // TODO: Integrate with SourceMigrator if source is in member; migrate to IFS and compile from there
    // For OPM, create temp member if needed

    compile(commandStr);
  }



  private void compile(String commandStr) {
    CommandCall cc = new CommandCall(system);
    try {
      if (debug) System.out.println("Executing: " + commandStr);
      Timestamp compilationTime = null;
      try (Statement stmt = connection.createStatement();
          ResultSet rsTime = stmt.executeQuery("SELECT CURRENT_TIMESTAMP AS Compilation_Time FROM sysibm.sysdummy1")) {
        if (rsTime.next()) {
          compilationTime = rsTime.getTimestamp("Compilation_Time");
        }
      }
      boolean success = cc.run(commandStr);
      AS400Message[] messages = cc.getMessageList();
      if (success) {
        System.out.println("Compilation successful.");
      } else {
        System.out.println("Compilation failed.");
        showCompilationSpool(compilationTime, system.getUserId().trim().toUpperCase(), objectName);
      }
      for (AS400Message msg : messages) {
        System.out.println(msg.getID() + ": " + msg.getText());
      }
    } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException | PropertyVetoException | SQLException e) {
      e.printStackTrace();
    }
    cleanup();
    }

  //TODO: This is kinda slow.
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
