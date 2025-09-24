package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Bin4;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.ProgramCall;
import com.ibm.as400.access.ProgramParameter;
import com.ibm.as400.access.UserSpace;

// Core struct for capturing compilation specs (JSON-friendly via Jackson)
public class ObjectDescription {
  private final AS400 system;
  private final Connection connection;
  private final boolean debug;
  //TODO: Make this private, add set method and move to another file
  public String targetLibrary;
  public String objectName;
  public ObjectType objectType;
  public String sourceLibrary;
  public String sourceFile;
  public String sourceName;
  public SourceType sourceType;

  public Map<CompilationPattern.ParamCmd, String> ParamCmdSequence = new HashMap<>();
  public Map<String, Object> objInfo = new HashMap<>();


  public enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR }

  public enum SourceType { 
    RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL;

    public static SourceType fromString(String value) {
      try {
        return SourceType.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get source type from object attribute '" + value + "'");
      }
    } 

    public static String defaultSourcePf (SourceType sourceType){
      switch (sourceType){
        case RPG:
          return DftSrc.QRPGSRC.name(); //TODO: Add .name()? and return string?
        case RPGLE:
        case SQLRPGLE:
          return DftSrc.QRPGLESRC.name();
        case CLP:
        case CLLE:
          return DftSrc.QCLSRC.name();
        case SQL:
          return DftSrc.QSQLSRC.name();
        default:
          throw new IllegalArgumentException("Could not get default sourcePf for '" + sourceType + "'");
      }
    }
  }

  public enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION;
    //public static String toParam(ObjectType objectType){
    public String toParam(){
      // return "*" + objectType.name();
      return "*" + this.name();
    }
  } // Add more as needed

  public enum PostCmpCmd { CHGOBJD }

  public enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC } // TODO: Expand

  //TODO: Move this class to its own file and remove static
  // Constructor for Jackson deserialization
  @JsonCreator
  public ObjectDescription(
        AS400 system,
        Connection connection,
        boolean debug,
        @JsonProperty("targetLibrary") String targetLibrary,
        @JsonProperty("objectName") String objectName,
        @JsonProperty("objectType") ObjectType objectType,
        @JsonProperty("sourceLibrary") String sourceLibrary,
        @JsonProperty("sourceFile") String sourceFile,
        @JsonProperty("sourceName") String sourceName,
        @JsonProperty("sourceType") SourceType sourceType,
        //@JsonProperty("text") String text,
        //@JsonProperty("actGrp") String actGrp ,//) {//,
        @JsonProperty("ParamCmdSequence") Map<CompilationPattern.ParamCmd, String> ParamCmdSequence) {
    // @JsonProperty("ParamCmdSequence") Map<CompilationPattern.ParamCmd, String> ParamCmdSequence
    // String json = objectDescription.writeValueAsString(example);
    //TODO: If validtion like toUpperCase().trim() is needed, add it when passing the params to keep this clean

    this.system = system;
    this.connection = connection;
    this.debug = debug;

    if (objectName == null || objectName.isEmpty()) throw new IllegalArgumentException("Object name is required.");

    this.targetLibrary = targetLibrary;
    this.objectName = objectName;
    this.objectType = objectType;
    this.sourceLibrary = sourceLibrary;
    this.sourceFile = (sourceFile.isEmpty()) ? SourceType.defaultSourcePf(sourceType) : sourceFile; // TODO: Add logic for sourcePF or directory
    this.sourceName = (sourceName.isEmpty() ? objectName : sourceName); //TODO: Add logic for stream files / members / default
    this.sourceType = sourceType;

    //TODO: Maybe this should be done in the object compiler.
    /* Add compilation params values */
    //this.ParamCmdSequence.put(ParamCmd.TEXT, (text.isEmpty()) ? "" : text);
    //this.ParamCmdSequence.put(ParamCmd.ACTGRP, (actGrp.isEmpty()) ? "" : actGrp);
    this.ParamCmdSequence = ParamCmdSequence;

  }

  // Getters for Jackson serialization
  public String getTargetLibrary() { return targetLibrary; }
  public String getObjectName() { return objectName; }
  public ObjectType getObjectType() { return objectType; }
  public String getSourceLibrary() { return sourceLibrary; }
  public String getSourceFile() { return sourceFile; }
  public String getSourceName() { return sourceName; }
  public SourceType getSourceType() { return sourceType; }
  public Map<CompilationPattern.ParamCmd, String> getParamCmdSequence() { return ParamCmdSequence; }

  // TODO: This logic encapsulation is nice. It will be helpfull in the future
  // Key method for use in graphs (matches ObjectDependency format)
  public String toGraphKey() {
    // TODO: Make the key like objectName.ObjectType.SourceType
    // this is the key that the compilator API will receive as a Json along with other files
    return targetLibrary + "/" + objectName + "/" + objectType.name();
  }

  public boolean isPGM(){
    return (this.objectType == ObjectType.PGM) ? true: false;
  }

  public boolean isSRVPGM(){
    return (this.objectType == ObjectType.SRVPGM) ? true: false;
  }

  public boolean isMODULE(){
    return (this.objectType == ObjectType.MODULE) ? true: false;
  }

  public void retrieveSQLObjectInfo() throws SQLException {
    try (Statement stmt = connection.createStatement();
        ResultSet rsObj = stmt.executeQuery(
          "SELECT PROGRAM_LIBRARY, " + // programLibrary
              "PROGRAM_NAME, " + // programName
              "PROGRAM_TYPE, " +  // [ILE, OPM] 
              "OBJECT_TYPE, " +   // typeOfProgram
              "CREATE_TIMESTAMP, " + // creationDateTime
              "COALESCE(TEXT_DESCRIPTION, '') As TEXT_DESCRIPTION, " + // textDescription
              "PROGRAM_OWNER, " + // owner
              "PROGRAM_ATTRIBUTE, " + // attribute
              "USER_PROFILE, " +
              "USE_ADOPTED_AUTHORITY, " +
              "RELEASE_CREATED_ON, " +
              "COALESCE(TARGET_RELEASE, '') As TARGET_RELEASE, " +
              "MINIMUM_NUMBER_PARMS, " + // minParameters
              "MAXIMUM_NUMBER_PARMS, " + // maxParameters
              "PAGING_POOL, " +
              "PAGING_AMOUNT, " +
              "COALESCE(ALLOW_RTVCLSRC, '') As ALLOW_RTVCLSRC, " + // allowRTVCLSRC
              "CONVERSION_REQUIRED, " +
              "CONVERSION_DETAIL, " +
              //-- These seem to be for ILE objects
              "PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY, " +
              "PROGRAM_ENTRY_PROCEDURE_MODULE, " +
              "COALESCE(ACTIVATION_GROUP, '') AS ACTIVATION_GROUP, " + // activationGroupAttribute
              "SHARED_ACTIVATION_GROUP, " +
              "OBSERVABLE_INFO_COMPRESSED, " +
              "RUNTIME_INFO_COMPRESSED, " +
              "ALLOW_UPDATE, " +
              "ALLOW_BOUND_SRVPGM_LIBRARY_UPDATE, " +
              "ALL_CREATION_DATA, " +
              "COALESCE(PROFILING_DATA, '') As PROFILING_DATA, " +
              "TERASPACE_STORAGE_ENABLED_MODULES, " +
              "TERASPACE_STORAGE_ENABLED_PEP, " +
              "COALESCE(STORAGE_MODEL , '') As STORAGE_MODEL, " +
              "ARGUMENT_OPTIMIZATION, " +
              "NUMBER_OF_UNRESOLVED_REFERENCES, " +
              "ALLOW_STATIC_STORAGE_REINIT, " +
              "MINIMUM_STATIC_STORAGE_SIZE, " +
              "MAXIMUM_STATIC_STORAGE_SIZE, " +
              "AUXILIARY_STORAGE_SEGMENTS, " +
              "MAXIMUM_AUXILIARY_STORAGE_SEGMENTS, " +
              "PROGRAM_SIZE, " +
              "MAXIMUM_PROGRAM_SIZE, " +
              // Module related data
              "MODULES, " + 
              "MAXIMUM_MODULES, " +
              "SERVICE_PROGRAMS, " +
              "MAXIMUM_SERVICE_PROGRAMS, " +
              "STRING_DIRECTORY_SIZE, " +
              "MAXIMUM_STRING_DIRECTORY_SIZE, " +
              "COPYRIGHTS, " +
              "COPYRIGHT_STRINGS, " +
              "COPYRIGHT_STRING_SIZE, " +
              "MAXIMUM_COPYRIGHT_STRING_SIZE, " +
              "EXPORT_SOURCE_LIBRARY, " +
              "EXPORT_SOURCE_FILE, " +
              "EXPORT_SOURCE_FILE_MEMBER, " +
              "EXPORT_SOURCE_STREAM_FILE, " +
              "PROCEDURE_EXPORTS, " + // Symbols info
              "MAXIMUM_PROCEDURE_EXPORTS, " +
              "DATA_EXPORTS, " +
              "MAXIMUM_DATA_EXPORTS, " +
              "SIGNATURES, " +
              "EXPORT_SIGNATURES, " +
              "MAXIMUM_SIGNATURES, " +
              // Source file related data
              "SOURCE_FILE_LIBRARY, " + // sourceLibrary
              "SOURCE_FILE, " + // sourceFile
              "SOURCE_FILE_MEMBER, " +
              "SOURCE_FILE_CHANGE_TIMESTAMP, " + // sourceUpdatedDateTime
              "COALESCE(SORT_SEQUENCE_LIBRARY, '') As SORT_SEQUENCE_LIBRARY, " +
              "COALESCE(SORT_SEQUENCE, '') As SORT_SEQUENCE, " +
              "COALESCE(LANGUAGE_ID, '') As LANGUAGE_ID, " +
              "OBSERVABLE, " + // observable
              "COALESCE(OPTIMIZATION, '') As OPTIMIZATION, " +
              "COALESCE(LOG_COMMANDS, '' ) As LOG_COMMANDS, " +
              "COALESCE(FIX_DECIMAL_DATA, '') As FIX_DECIMAL_DATA, " + // fixDecimalData
              "UPDATE_PASA, " +
              "CLEAR_PASA, " +
              "COMPILER_ID, " +
              "TERASPACE_STORAGE_ENABLED_PROGRAM, " + // teraspaceEnabled
              "OPM_PROGRAM_SIZE, " +
              "STATIC_STORAGE_SIZE, " +
              "AUTOMATIC_STORAGE_SIZE, " +
              "NUMBER_MI_INSTRUCTIONS, " +
              "NUMBER_MI_ODT_ENTRIES, " +
              //-- Sql related info
              "SQL_STATEMENT_COUNT, " +
              "SQL_RELATIONAL_DATABASE, " +
              "SQL_COMMITMENT_CONTROL, " +
              "SQL_NAMING, " +
              "SQL_DATE_FORMAT, " +
              "SQL_DATE_SEPARATOR, " +
              "SQL_TIME_FORMAT, " +
              "SQL_TIME_SEPARATOR, " +
              "SQL_SORT_SEQUENCE_LIBRARY, " +
              "SQL_SORT_SEQUENCE, " +
              "SQL_LANGUAGE_ID, " +
              "SQL_DEFAULT_SCHEMA, " +
              "SQL_PATH, " +
              "SQL_DYNAMIC_USER_PROFILE, " +
              "SQL_ALLOW_COPY_DATA, " +
              "SQL_CLOSE_SQL_CURSOR, " +
              "SQL_DELAY_PREPARE, " +
              "SQL_ALLOW_BLOCK, " +
              "SQL_PACKAGE_LIBRARY, " +
              "SQL_PACKAGE, " +
              "SQL_RDB_CONNECTION_METHOD " +
            "FROM QSYS2.PROGRAM_INFO " +
            "WHERE PROGRAM_LIBRARY = '" + targetLibrary + "' " +
                "AND PROGRAM_NAME = '" + objectName + "' " +
                "AND OBJECT_TYPE = '" + objectType.toParam() + "' "
          )) {
      if (!rsObj.next()) {
        // TODO: Maybe this should be optional for new objects. Just throw a warning
        throw new IllegalArgumentException("Could not get object '" + objectName + "' from library '" + targetLibrary + "' type" + "'" + objectType.toString() + "'");
      }

      System.out.println("Found object '" + objectName + "' from library '" + targetLibrary + "' type " + "'" + objectType.toParam() + "'");

      String text = rsObj.getString("TEXT_DESCRIPTION").trim();
      if (!text.isEmpty()) ParamCmdSequence.put(ParamCmd.TEXT, "'" + text +"'");

      String tgtRls = rsObj.getString("TARGET_RELEASE").trim();
      if (!tgtRls.isEmpty()) ParamCmdSequence.put(ParamCmd.TGTRLS, tgtRls); //TODO: Consider ValCmd.CURRENT.toString()

      String usrPrf = rsObj.getString("USER_PROFILE").trim();
      if (!usrPrf.isEmpty()) ParamCmdSequence.put(ParamCmd.USRPRF, usrPrf);

      String srtLib = rsObj.getString("SORT_SEQUENCE_LIBRARY").trim();
      if (!srtLib.isEmpty()) ParamCmdSequence.put(ParamCmd.SORTSEQ_LIB, srtLib);  // Custom key if needed
      String srtSeq = rsObj.getString("SORT_SEQUENCE").trim();
      if (!srtSeq.isEmpty()) ParamCmdSequence.put(ParamCmd.SRTSEQ, srtSeq + (srtLib.isEmpty() ? "" : " " + srtLib));

      String langId = rsObj.getString("LANGUAGE_ID").trim();
      if (!langId.isEmpty()) ParamCmdSequence.put(ParamCmd.LANGID, langId);

      String optimize = rsObj.getString("OPTIMIZATION").trim();
      if (!optimize.isEmpty()) ParamCmdSequence.put(ParamCmd.OPTIMIZE, optimize);

      String fixNbr = rsObj.getString("FIX_DECIMAL_DATA").trim();
      if (!fixNbr.isEmpty()){
        fixNbr = fixNbr.equals("1") ? ValCmd.YES.toString() : ValCmd.NO.toString();  // Map boolean-ish
        ParamCmdSequence.put(ParamCmd.FIXNBR, fixNbr);
      }

      String prfDta = rsObj.getString("PROFILING_DATA").trim();
      if (!prfDta.isEmpty()) ParamCmdSequence.put(ParamCmd.PRFDTA, prfDta);

      String stgMdl = rsObj.getString("STORAGE_MODEL").trim();
      if (!stgMdl.isEmpty()) ParamCmdSequence.put(ParamCmd.STGMDL, stgMdl);

      String logCmds = rsObj.getString("LOG_COMMANDS").trim();
      if (!logCmds.isEmpty()) ParamCmdSequence.put(ParamCmd.LOG, logCmds.equals("1") ? ValCmd.YES.toString() : ValCmd.NO.toString());

      String alwRtvSrc = rsObj.getString("ALLOW_RTVCLSRC").trim();
      if (!alwRtvSrc.isEmpty()) ParamCmdSequence.put(ParamCmd.ALWRTVSRC, alwRtvSrc.equals("1") ? ValCmd.YES.toString() : ValCmd.NO.toString());

      /* Specific program type */
      String programType = rsObj.getString("PROGRAM_TYPE").trim();
      System.out.println("PROGRAM_TYPE " + programType );

      //TODO: Here i could try to migrate the sources for ILE to stream files and adjust the command
      if ("ILE".equals(programType)) {
        String actgrp = rsObj.getString("ACTIVATION_GROUP").trim();
        if (!actgrp.isEmpty()) ParamCmdSequence.put(ParamCmd.ACTGRP, actgrp);

         retrieveBoundModuleInfo(rsObj.getString("PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY").trim(), 
                                         rsObj.getString("PROGRAM_ENTRY_PROCEDURE_MODULE").trim());
      }

      // For OPM-specific (e.g., SAAFLAG, if in view)
      if ("OPM".equals(programType)) {
        // Map OPM fields similarly if present in query
      }

      //if (debug)
      System.out.println("All data: ");
      for (CompilationPattern.ParamCmd paramCmd : this.ParamCmdSequence.keySet()){
        System.out.println(paramCmd.name() + ": " + this.ParamCmdSequence.get(paramCmd));
      }
      
    }
  }

  // NEW: Query BOUND_MODULE_INFO for module-specific fields (e.g., GENLVL, OPTION)
  public void retrieveBoundModuleInfo(String entryModuleLib, String entryModule) throws SQLException {
    if (entryModuleLib == null || entryModule == null) return;  // Skip if no entry module

    try (Statement stmt = connection.createStatement();
        ResultSet rsMod = stmt.executeQuery(
          "SELECT PROGRAM_LIBRARY, " +
                "PROGRAM_NAME, " +
                "OBJECT_TYPE, " +
                "BOUND_MODULE_LIBRARY, " +
                "BOUND_MODULE, " +
                "MODULE_ATTRIBUTE, " +
                "MODULE_CREATE_TIMESTAMP, " +
                "SOURCE_FILE_LIBRARY, " +
                "SOURCE_FILE, " +
                "SOURCE_FILE_MEMBER, " +
                "COALESCE(SOURCE_STREAM_FILE_PATH, '') As SOURCE_STREAM_FILE_PATH, " +
                "SOURCE_CHANGE_TIMESTAMP, " +
                "MODULE_CCSID, " +
                "SORT_SEQUENCE_LIBRARY, " +
                "SORT_SEQUENCE, " +
                "LANGUAGE_ID, " +
                "DEBUG_DATA, " +
                "OPTIMIZATION_LEVEL, " +
                "MAX_OPTIMIZATION_LEVEL, " +
                "OBJECT_CONTROL_LEVEL, " +
                "RELEASE_CREATED_ON, " +
                "TARGET_RELEASE, " +
                "CREATION_DATA, " +
                "TERASPACE_STORAGE_ENABLED, " +
                "STORAGE_MODEL, " +
                "NUMBER_PROCEDURES, " +
                "NUMBER_PROCEDURES_BLOCK_REORDERED, " +
                "NUMBER_PROCEDURES_BLOCK_ORDER_MEASURED, " +
                "PROFILING_DATA, " +
                "ALLOW_RTVCLSRC, " +
                "USER_MODIFIED, " +
                "LIC_OPTIONS, " +
                "LICENSED_PROGRAM, " +
                "PTF_NUMBER, " +
                "APAR_ID, " +
                "SQL_STATEMENT_COUNT, " +
                "SQL_RELATIONAL_DATABASE, " +
                "SQL_COMMITMENT_CONTROL, " +
                "SQL_NAMING, " +
                "SQL_DATE_FORMAT, " +
                "SQL_DATE_SEPARATOR, " +
                "SQL_TIME_FORMAT, " +
                "SQL_TIME_SEPARATOR, " +
                "SQL_SORT_SEQUENCE_LIBRARY, " +
                "SQL_SORT_SEQUENCE, " +
                "SQL_LANGUAGE_ID, " +
                "SQL_DEFAULT_SCHEMA, " +
                "SQL_PATH, " +
                "SQL_DYNAMIC_USER_PROFILE, " +
                "SQL_ALLOW_COPY_DATA, " +
                "SQL_CLOSE_SQL_CURSOR, " +
                "SQL_DELAY_PREPARE, " +
                "SQL_ALLOW_BLOCK, " +
                "SQL_PACKAGE_LIBRARY, " +
                "SQL_PACKAGE, " +
                "SQL_RDB_CONNECTION_METHOD " + 
          "FROM QSYS2.BOUND_MODULE_INFO " +
          "WHERE PROGRAM_LIBRARY = '" + targetLibrary + "' " +
            "AND PROGRAM_NAME = '" + objectName + "' " +
            "AND BOUND_MODULE_LIBRARY = '" + entryModuleLib + "' " +
            "AND BOUND_MODULE = '" + entryModule + "' "
        )) {
      if (!rsMod.next()) {
        // TODO: Maybe this should be optional for new objects. Just throw a warning
        throw new IllegalArgumentException("Could not get module '" + entryModule + "' from library '" + entryModuleLib + "'");
      }

        /* These are not found in the view 
        String genLvl = rsMod.getString("GEN_SEVERITY_LEVEL").trim();
        if (!genLvl.isEmpty()) ParamCmdSequence.put(ParamCmd.GENLVL, genLvl);

        String option = rsMod.getString("COMPILER_OPTIONS").trim();
        if (!option.isEmpty()) ParamCmdSequence.put(ParamCmd.OPTION, option);

        String dbgView = rsMod.getString("DEBUG_VIEWS").trim();
        if (!dbgView.isEmpty()) ParamCmdSequence.put(ParamCmd.DBGVIEW, dbgView);
        */


        // Override OPTIMIZE if more specific here
        // This gives 10 but the param  OPTIMIZE only accepts: *NONE, *BASIC, *FULL   
        String modOptimize = rsMod.getString("OPTIMIZATION_LEVEL").trim();
        if (!modOptimize.isEmpty()) {
          switch (modOptimize) {
            case "10": ParamCmdSequence.put(ParamCmd.OPTIMIZE, ValCmd.NONE.toString());   break;
            case "30": ParamCmdSequence.put(ParamCmd.OPTIMIZE, ValCmd.BASIC.toString());  break;
            case "40": ParamCmdSequence.put(ParamCmd.OPTIMIZE, ValCmd.FULL.toString());   break;
          }
        }

        // Update source if more accurate
        String modSrcLib = rsMod.getString("SOURCE_FILE_LIBRARY").trim();
        if (!modSrcLib.isEmpty()) {
            sourceLibrary = modSrcLib.toUpperCase();
            ParamCmdSequence.put(ParamCmd.SRCFILE, sourceLibrary + "/" + sourceFile);  // Update
        }
        String modSrcFil = rsMod.getString("SOURCE_FILE").trim();
        String modSrcMbr = rsMod.getString("SOURCE_FILE_MEMBER").trim();
        String modSteamFile = rsMod.getString("SOURCE_STREAM_FILE_PATH").trim();
        //TODO: Here, if (modSteamFile.isEmpty()) do the migration to /temp or another route. and do the compilation
        String modCCSID = rsMod.getString("MODULE_CCSID").trim();

        // Fallback to API for missing fields like DBGVIEW
        // Fallback to API for missing fields like DBGVIEW
        try {
          String dbgView = getDbgViewFromBoundModule(targetLibrary, objectName, rsMod.getString("BOUND_MODULE").trim());
          if (!dbgView.isEmpty()) {
              ParamCmdSequence.put(ParamCmd.DBGVIEW, dbgView);
          }
        } catch (Exception e) {
          if (debug) System.err.println("Failed to retrieve DBGVIEW via API: " + e.getMessage());
          // Fallback to default or user-provided --dbgview
        }

        // Add more mappings (e.g., DEFINE, INCDIR, PPGENOPT)
      
      /* 
      else {
        // FALLBACK: Call to retrieveModuleInfo() API for not found fields
        try {
          retrieveModuleInfo(targetLibrary + "/" + objectName);  // Qual name
        } catch (Exception e) {
          if (debug) System.err.println("Fallback API failed: " + e.getMessage());
        }
      }
      */
    }
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
    /*  Previous, this worked to get the module but caused the memory leak
    int listOffset = bin4.toInt(data, 124); // Standard offset to list data section (note: decimal 124 for V5R2+)
    int numEntries = bin4.toInt(data, 132); // Number of entries
    int genEntrySize = bin4.toInt(data, 136); // Generic entry size (ignore for PGML0110; use per-entry size)
    */
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




  public void fillSpecFromObjInfo() {
    if (this.objInfo == null) return;

    if (debug) System.out.println("Found object info");

    /* Check for filed with default value to subsitute */
    if (this.sourceType == null) {
      String attr = (String) objInfo.get("attribute");
      if (debug) System.out.println("attr: " + attr);
      if (attr != null && !attr.trim().isEmpty()) {
        this.sourceType = SourceType.fromString(attr.trim().toUpperCase());
      }
    }

    String retrievedLib = (String) objInfo.get("sourceLibrary");
    if (debug) System.out.println("retrievedLib: " + retrievedLib);
    if (retrievedLib != null && !retrievedLib.trim().isEmpty()) {
      this.sourceLibrary = retrievedLib.trim().toUpperCase();
    }

    String retrievedFile = (String) objInfo.get("sourceFile");
    if (debug) System.out.println("retrievedFile: " + retrievedFile);
    if (retrievedFile != null && !retrievedFile.trim().isEmpty()) {
      this.sourceFile = retrievedFile.trim().toUpperCase();
    } 

    String retrievedMbr = (String) objInfo.get("sourceName");
    if (debug) System.out.println("retrievedMbr: " + retrievedMbr);
    if (retrievedMbr != null && !retrievedMbr.trim().isEmpty()) {
      this.sourceName = retrievedMbr.trim().toUpperCase();
    }

    String retrievedText = (String) objInfo.get("textDescription");
    if (debug) System.out.println("retrievedText: " + retrievedText);
    if (retrievedText != null && !retrievedText.trim().isEmpty()) {
      this.ParamCmdSequence.put(ParamCmd.TEXT, retrievedText.trim());
    }
      
      // TODO: Add more params like --usrprf, --useadpaut, etc., and map from objInfo
    // Similarly fill other fields (sourceLib, sourceFile, etc.)
    // To make odes mutable or use a builder for this.
  }

}