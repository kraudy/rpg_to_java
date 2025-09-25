package com.github.kraudy.api;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.HashMap;
import java.util.Map;

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

@Command (name = "apiCaller", description = "System API Caller", mixinStandardHelpOptions = true, version = "ApiCaller 0.0.1")
public class ApiCaller {
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;

  public enum SourceType { 
    RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL;

    public static SourceType fromString(String value) {
      try {
        return SourceType.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get source type from object attribute '" + value + "'");
      }
    } 
  }

  public enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION;
    public String toParam(){
      return "*" + this.name();
    }
  }

  public enum ValCmd { 
    FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, MODULE, OBJ, SRVPGM, CURLIB, ALL, CURRENT,
    NONE, BASIC, FULL,

    YES, NO, STMT, SOURCE, LIST, HEX, JOBRUN, USER, LIBCRTAUT, PEP, NOCOL, PRINT, SNGLVL; 

    public static ValCmd fromString(String value) {
      try {
          return ValCmd.valueOf(value.substring(1)); // Remove the leading "*" and convert to enum
      } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Could not convert '" + value + "' to ValCmd. Unknown value: '" + value + "'");
      }
    }

    @Override
    public String toString() {
        return "*" + name();
    }  
  }

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

  static class ObjectTypeConverter implements CommandLine.ITypeConverter<ObjectType> {
    @Override
    public ObjectType convert(String value) throws Exception {
      try {
        return ObjectType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid object type: " + value);
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

  /* Object attributes. Required params */
  @Option(names = { "-l", "--lib" }, required = true, description = "Target library for object", converter = LibraryConverter.class)
  private String library;

  //TODO: --obj, -t and -st could be made in one: hello.pgm.rpgle and just parse it. Maybe the last could be optional if the object is found.
  @Option(names = "--obj", required = true, description = "Object name", converter = ObjectNameConverter.class)
  private String objectName;

  @Option(names = {"-t","--type"}, required = true, description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectType objectType;

  /* Source-related params. Good to have */
  @Option(names = { "-sl", "--source-lib" }, description = "Source library (defaults to *LIBL or retrieved from object)", converter = LibraryConverter.class)
  private String sourceLib = ValCmd.LIBL.toString(); //"*LIBL"

  @Option(names = { "-sf", "--source-file" }, description = "Source physical file (defaults based on source type or retrieved from object)")
  private String sourceFile = "";

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name, command-specific *SPECIAL, or retrieved from object)")
  private String sourceName = "";

  @Option(names = {"-st","--source-type"}, description = "Source type (e.g., RPGLE, CLLE) (defaults to retrieved from object if possible)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  /* Constructors */
  public ApiCaller(AS400 system) throws Exception {
    this(system, new AS400JDBCDataSource(system).getConnection());
  }

  public ApiCaller(AS400 system, Connection connection) throws Exception {
    this.system = system;

    // Database
    this.connection = connection;
    this.connection.setAutoCommit(true);

    // User
    this.currentUser = new User(system, system.getUserId());
    this.currentUser.loadUserInformation();

  }

  public void run(){
    // odes.retrieveObjectInfo();
    //String dbgView = getDbgViewFromBoundModule(targetLibrary, objectName, rsMod.getString("BOUND_MODULE").trim());
  }

  private String getDbgViewFromBoundModule(String pgmLib, String pgmName, String entryModule) throws Exception {
    // Use QBNLPGMI PGML0110 to get bound module creation command
    String usName = "PGMLIST";
    String usLib = "QTEMP";
    UserSpace us = new UserSpace(system, "/QSYS.LIB/" + usLib + ".LIB/" + usName + ".USRSPC");
    us.setMustUseProgramCall(true);
    us.setMustUseSockets(true);
    int initialSize = 4096 * 10; // Larger for PGML0110 variable entries and command data
    us.create(initialSize, true, " ", (byte) 0x00, "Temp user space for QBNLPGMI", "*USE");

    String qualUs = String.format("%-10s%-10s", usName, usLib);
    String qualPgm = String.format("%-10s%-10s", pgmName, pgmLib);

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/QBNLPGMI.PGM");

    ProgramParameter[] parms = new ProgramParameter[4];
    parms[0] = new ProgramParameter(new AS400Text(20, system).toBytes(qualUs)); // Qualified user space
    parms[1] = new ProgramParameter(new AS400Text(8, system).toBytes("PGML0110")); // Format
    parms[2] = new ProgramParameter(new AS400Text(20, system).toBytes(qualPgm)); // Qualified program name
    byte[] errorCode = new byte[32];
    new AS400Bin4().toBytes(0, errorCode, 0);
    parms[3] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    if (!pc.run()) {
        AS400Message[] msgs = pc.getMessageList();
        throw new Exception("QBNLPGMI API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    // Read user space
    byte[] data = new byte[initialSize];
    us.read(data, 0);

    AS400Bin4 bin4 = new AS400Bin4();

    int listOffset = bin4.toInt(data, 116);
    int numEntries = bin4.toInt(data, 120);
    int genEntrySize = bin4.toInt(data, 124);  // Expect 0 for PGML0110

    if (numEntries == 0) {
        us.delete();
        return ValCmd.NONE.toString();
    }

    String command = "";
    int currentOffset = listOffset;
    AS400Text text10 = new AS400Text(10, system);

    for (int i = 0; i < numEntries; i++) {
        int entrySize = bin4.toInt(data, currentOffset); // Size of this entry at offset 0
        String modName = text10.toObject(data, currentOffset + 24).toString().trim(); // Bound module name at +24

        if (modName.equals(entryModule)) { // Match entry module
            System.out.println("Found module: " + modName);
            int cmdOffset = bin4.toInt(data, currentOffset + 346); // Offset to creation data
            int cmdLength = bin4.toInt(data, currentOffset + 350); // Length of creation data
            //int cmdLength = bin4.toInt(data, 0); // Length of creation data
            if (cmdLength > 0) {
                System.out.println("Command length: " + String.valueOf(cmdLength));
                //cmdLength = 30000; //Increasing this gives the same information
                AS400Text text = new AS400Text(cmdLength, system);
                command = text.toObject(data, cmdOffset).toString().trim().toUpperCase();
                System.out.println("Full command: " + command);
                break;
            }
        }

        currentOffset += entrySize; // Advance by variable entry size
    }

    us.delete();

    if (command.isEmpty()) {
        return ValCmd.NONE.toString();
    }

    // Parse DBGVIEW as before
    int dbgIndex = command.indexOf("DBGVIEW(");
    if (dbgIndex == -1) {
        return ValCmd.NONE.toString();
    }
    int start = dbgIndex + "DBGVIEW(".length();
    int end = command.indexOf(")", start);
    if (end == -1) {
        return ValCmd.NONE.toString();
    }
    String value = command.substring(start, end).trim().replace("'", "");
    return "*" + value;
  }

  //Module info cannot be get directly from module for ile objects
  private String getDbgViewFromModule(String moduleLib, String moduleName) throws Exception {
    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/QBNRMODI.PGM");

    int recvLen = 4096; // Large enough for the command string
    ProgramParameter[] parms = new ProgramParameter[5];
    parms[0] = new ProgramParameter(recvLen); // Receiver
    parms[1] = new ProgramParameter(new AS400Bin4().toBytes(recvLen)); // Length
    parms[2] = new ProgramParameter(new AS400Text(8, system).toBytes("MODI0200")); // Format
    String qualName = String.format("%-10s%-10s", moduleName, moduleLib);
    parms[3] = new ProgramParameter(new AS400Text(20, system).toBytes(qualName)); // Qualified module name
    byte[] errorCode = new byte[16];
    new AS400Bin4().toBytes(0, errorCode, 0); // Bytes provided = 0 (throw exceptions)
    parms[4] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    if (!pc.run()) {
        AS400Message[] msgs = pc.getMessageList();
        throw new Exception("QBNRMODI API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    byte[] data = parms[0].getOutputData();
    AS400Bin4 bin4 = new AS400Bin4();
    int cmdOffset = bin4.toInt(data, 8);
    int cmdLength = bin4.toInt(data, 12);
    if (cmdLength == 0) {
        return ValCmd.NONE.toString(); // No command data; default to *NONE
    }

    AS400Text text = new AS400Text(cmdLength, system);
    String command = text.toObject(data, cmdOffset).toString().trim().toUpperCase();

    System.out.println("Full command: " + command);

    // Parse for DBGVIEW(value)
    int dbgIndex = command.indexOf("DBGVIEW(");
    if (dbgIndex == -1) {
        return ValCmd.NONE.toString(); // Not found; default to *NONE
    }
    int start = dbgIndex + "DBGVIEW(".length();
    int end = command.indexOf(")", start);
    if (end == -1) {
        return ValCmd.NONE.toString();
    }
    String value = command.substring(start, end).trim();
    // Clean up any quotes or extras if needed
    value = value.replace("'", "");
    return "*" + value; // Map back to *STMT, *SOURCE, etc.
}

  public void retrieveObjectInfo() throws Exception {
    String apiPgm;
    String format;
    switch (this.objectType) {
      //TODO: Add these to maps
      case PGM:
        /* QBNRPGMI => Gives error: "Could not retrieve compilation params from object: /QSYS.LIB/QBNRPGMI.PGM: Object does not exist"
        if (sourceType == SourceType.RPG || sourceType == SourceType.RPGLE || sourceType == SourceType.SQLRPGLE) {
          apiPgm = "QBNRPGMI";
        } else if (sourceType == SourceType.CLP || sourceType == SourceType.CLLE) {
          apiPgm = "QCLRPGMI";
        } else {
          throw new Exception("Unsupported source type " + (sourceType != null ? sourceType.name() : "null") + " for retrieving compilation params.");
        }
        */
        apiPgm = "QCLRPGMI";
        format = "PGMI0100";
        break;
      case SRVPGM:
        apiPgm = "QBNRSPGM";
        format = "SPGI0100";
        break;
      case MODULE:
        apiPgm = "QBNRMODI";
        format = "MODI0100";
        break;
      default:
        throw new Exception("Object type " + this.objectType.name() + " not supported for retrieving compilation params.");
    }

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/" + apiPgm + ".PGM");

    int recvLen = 2048; // Sufficient for PGMI0100/SPGI0100/MODI0100
    ProgramParameter[] parms = new ProgramParameter[5];
    parms[0] = new ProgramParameter(recvLen); // Receiver
    parms[1] = new ProgramParameter(new AS400Bin4().toBytes(recvLen)); // Length of receiver
    parms[2] = new ProgramParameter(new AS400Text(8, system).toBytes(format)); // Format
    String qualName = String.format("%-10s%-10s", this.objectName, this.targetLibrary);
    parms[3] = new ProgramParameter(new AS400Text(20, this.system).toBytes(qualName)); // Qualified object name
    byte[] errorCode = new byte[16];
    new AS400Bin4().toBytes(0, errorCode, 0); // Bytes provided = 0 to throw exceptions
    parms[4] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    if (!pc.run()) {
      AS400Message[] msgs = pc.getMessageList();
      throw new Exception("API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    byte[] data = parms[0].getOutputData();
    //Map<String, Object> info = new HashMap<>();

    AS400Bin4 bin4 = new AS400Bin4();
    AS400Text text10 = new AS400Text(10, system);
    AS400Text text13 = new AS400Text(13, system);
    AS400Text text1 = new AS400Text(1, system);
    AS400Text text50 = new AS400Text(50, system);
    AS400Text text30 = new AS400Text(30, system);

    int offset = 0;
    this.objInfo.put("bytesReturned", bin4.toInt(data, offset)); offset += 4;
    this.objInfo.put("bytesAvailable", bin4.toInt(data, offset)); offset += 4;
    this.objInfo.put("programName", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("programLibrary", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("owner", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("attribute", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("creationDateTime", text13.toObject(data, offset).toString().trim()); offset += 13;
    this.objInfo.put("sourceFile", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("sourceLibrary", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("sourceName", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("sourceUpdatedDateTime", text13.toObject(data, offset).toString().trim()); offset += 13;
    this.objInfo.put("observable", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("userProfileOption", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("useAdoptedAuthority", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("logCommands", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("allowRTVCLSRC", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("fixDecimalData", text1.toObject(data, offset).toString().trim()); offset += 1;


    this.objInfo.put("textDescription", text50.toObject(data, offset).toString().trim()); offset += 50;
    this.objInfo.put("typeOfProgram", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("teraspaceEnabled", text1.toObject(data, offset).toString().trim()); offset += 1;
    offset += 58; // Reserved
    this.objInfo.put("minParameters", bin4.toInt(data, offset)); offset += 4;
    this.objInfo.put("maxParameters", bin4.toInt(data, offset)); offset += 4;
    offset = 368; // Jump to activation group (adjust if needed for SPGI/MODI differences)
    if (this.objectType != ObjectDescription.ObjectType.MODULE) { // Modules don't have ACTGRP
      this.objInfo.put("activationGroupAttribute", text30.toObject(data, offset).toString().trim());
    }
    // TODO: Parse additional fields as needed

    // If this is an ILE program ('B'), retrieve module information for source details
    String typeOfProgram = (String) this.objInfo.get("typeOfProgram");
    System.out.println("typeOfProgram: " + typeOfProgram);

    if ("B".equals(typeOfProgram) && this.objectType == ObjectType.PGM) {
      System.out.println("Doing retrieveModuleInfo");
      retrieveModuleInfo(qualName);
    }

    System.out.println("All data: ");
    for (String ob : this.objInfo.keySet()){
      System.out.println(ob + ": " + this.objInfo.get(ob));
    }

  }

  //TODO:  handle/select from multiple entries and choose the main module
  private void retrieveModuleInfo(String qualName) throws Exception {
    // Import required: import com.ibm.as400.access.UserSpace;
    String usName = "PGMLIST";
    String usLib = "QTEMP";
    UserSpace us = new UserSpace(system, "/QSYS.LIB/" + usLib + ".LIB/" + usName + ".USRSPC");
    us.setMustUseProgramCall(true);  // Ensure operations in same job for QTEMP
    us.setMustUseSockets(true);      // Force sockets to avoid native issues
    int initialSize = 2048 * 10; // Sufficient buffer, adjust if needed for multi-module
    //us.create(initialSize, true, "", (byte) 0x00, "Temp user space for QBNLPGMI", "*USE");
    us.create(initialSize, true, " ", (byte) 0x00, "Temp user space for QBNLPGMI", "*USE");  // Added space for extended attribute to enable native read support

    String qualUs = String.format("%-10s%-10s", usName, usLib);

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/QBNLPGMI.PGM");

    byte[] errorCode = new byte[32];
    new AS400Bin4().toBytes(0, errorCode, 0);

    System.out.println("Inside retrieveModuleInfo");

    ProgramParameter[] parms = new ProgramParameter[4];
    parms[0] = new ProgramParameter(new AS400Text(20, system).toBytes(qualUs)); // Qualified user space name
    parms[1] = new ProgramParameter(new AS400Text(8, system).toBytes("PGML0100")); // Format
    parms[2] = new ProgramParameter(new AS400Text(20, system).toBytes(qualName)); // Qualified program name
    parms[3] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    System.out.println("Before pc.run()");

    if (!pc.run()) {
      AS400Message[] msgs = pc.getMessageList();
      throw new Exception("QBNLPGMI API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    System.out.println("After pc.run()");

    // Read the entire user space
    byte[] data = new byte[initialSize];
    us.read(data, 0);

    System.out.println("us.read()");

    AS400Bin4 bin4 = new AS400Bin4();
    int listOffset = bin4.toInt(data, 122); // Offset to list data section
    int numEntries = bin4.toInt(data, 126); // Number of list entries
    int entrySize = bin4.toInt(data, 130); // Size of each entry

    System.out.println("entrySize: " + entrySize);

    if (numEntries > 0) {
      // For simplicity, take the first module (common for single-module bound programs)
      int entryOffset = listOffset;
      AS400Text text10 = new AS400Text(10, system);

      // Update objInfo with module source info
      this.objInfo.put("sourceFile", text10.toObject(data, entryOffset + 56).toString().trim());
      this.objInfo.put("sourceLibrary", text10.toObject(data, entryOffset + 66).toString().trim());
      this.objInfo.put("sourceName", text10.toObject(data, entryOffset + 76).toString().trim());

      // TODO: If multi-module, you may need logic to select the appropriate one or aggregate
    } else {
      throw new Exception("No modules found in ILE program.");
    }

    // Optional: Delete the user space after use
    us.delete();    
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
    ApiCaller apiCaller = null;
    try {
      system = IBMiDotEnv.getNewSystemConnection(true); // Get system
      apiCaller = new ApiCaller(system);
      new CommandLine(apiCaller).execute(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
