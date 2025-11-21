package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import com.github.kraudy.compiler.CompilationPattern.ObjectType;
import com.github.kraudy.compiler.CompilationPattern.SourceType;

// Core struct for capturing compilation specs (JSON-friendly via Jackson)
public class ObjectDescription {
  public final Connection connection;
  private final boolean debug;
  private final boolean verbose;
  //TODO: Make this private, add set method and move to another file
  //public String targetLibrary;
  // public String objectName;
  //public ObjectType objectType;
  // public String sourceLibrary;
  public String sourceFile;
  public String sourceName;
  public SourceType sourceType;
  Utilities.ParsedKey targetKey;
  CompCmd compilationCommand;

  

  @JsonCreator
  public ObjectDescription(
        Connection connection,
        boolean debug,
        boolean verbose,
        CompCmd compilationCommand,
        @JsonProperty("targetKey") Utilities.ParsedKey targetKey,
        @JsonProperty("sourceFile") String sourceFile,
        @JsonProperty("sourceName") String sourceName) {

    this.connection = connection;
    this.debug = debug;
    this.verbose = verbose;

    this.targetKey = targetKey;

    this.sourceFile = sourceFile;
    this.sourceName = sourceName;

    this.compilationCommand = compilationCommand;


  }

  public ParamMap SetCompilationParams(ParamMap ParamCmdSequence) {

    /* Generate compilation params values from object description */

    //TODO: Add something like [DEFAULT] for default value of params

    //TODO: Set another switch specifically for SRCSTMF
    switch (this.compilationCommand) {
      case CRTSQLRPGI:
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTRPGPGM:
      case CRTCLPGM:
      case CRTDSPF:
      case CRTPF:
      case CRTLF:
      case CRTSRVPGM:
      case CRTRPGMOD:
      case CRTCLMOD:
      case RUNSQLSTM:
        //TODO: SRCFILE could be set to *LIBL,etc until the migrators works with the library list
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCFILE, this.targetKey.library + "/" + this.sourceFile);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCMBR, this.sourceName);
        break;
    }

    /* Set default values */
    switch (this.compilationCommand) {
      case CRTSQLRPGI:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.OBJ, this.targetKey.library + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.OBJ, ValCmd.CURLIB.toString() + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.OBJTYPE, this.targetKey.objectType.toParam());
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.COMMIT, ValCmd.NONE);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.DBGVIEW, ValCmd.SOURCE);
        break;
    
      case CRTBNDRPG:
      case CRTBNDCL:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.DBGVIEW, ValCmd.ALL);
      case CRTRPGPGM:
      case CRTCLPGM:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.PGM, this.targetKey.library + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.PGM, ValCmd.CURLIB.toString() + "/" + this.targetKey.objectName);
        break;

      case CRTDSPF:
      case CRTPF:
      case CRTLF:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.FILE, this.targetKey.library + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.FILE, ValCmd.CURLIB.toString() + "/" + this.targetKey.objectName);
        break;
      
      case CRTSRVPGM:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRVPGM, this.targetKey.library + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRVPGM, ValCmd.CURLIB.toString() + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.MODULE, this.targetKey.library + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.MODULE, ValCmd.LIBL.toString() + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.BNDSRVPGM, ValCmd.NONE);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.EXPORT, ValCmd.ALL);
        break; //TODO: I had these two together, check if it is needed or simply add 

      case CRTRPGMOD:
      case CRTCLMOD:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.DBGVIEW, ValCmd.ALL);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.MODULE, this.targetKey.library + "/" + this.targetKey.objectName);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.MODULE, ValCmd.CURLIB.toString() + "/" + this.targetKey.objectName);
        break;

      case RUNSQLSTM:
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.COMMIT, ValCmd.NONE);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.DBGVIEW, ValCmd.SOURCE);
        ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTION, ValCmd.LIST);
        break;

      default:
        break;
    }

    //TODO: Changed it to same target library, could be overwritten later if a param is provided
    //ParamCmdSequence.put(this.compilationCommand, ParamCmd.REPLACE, ValCmd.YES.toString());
    return ParamCmdSequence;

  }

  // Getters for Jackson serialization
  //public String getTargetLibrary() { return targetLibrary; }
  //public String getObjectName() { return objectName; }
  //public ObjectType getObjectType() { return objectType; }
  // public String getSourceLibrary() { return sourceLibrary; }
  public String getSourceFile() { return sourceFile; }
  public String getSourceName() { return sourceName; }
  public SourceType getSourceType() { return this.targetKey.sourceType; }
  public ObjectType getObjectType() { return this.targetKey.objectType; }
  //public Map<CompilationPattern.ParamCmd, String> getParamCmdSequence() { return ParamCmdSequence; }
  //public ParamMap getParamCmdSequence() { return ParamCmdSequence; }

  //public void setParamsSequence(Map<CompilationPattern.ParamCmd, String> ParamCmdSequence) {
  //TODO: Maybe add a method to ParamMap to just send the map
  //TODO: This should be inside ParamMap
  //TODO: overrideParams()
  //public void setParamsSequence(ParamMap ParamCmdSequence) {
    // TODO: Check this
    // this.ParamCmdSequence.putAll(odes.getParamCmdSequence());  // Copy from odes (triggers put for each entry)

    //this.ParamCmdSequence.overrideParams(this.compilationCommand, ParamCmdSequence)
    //for (CompilationPattern.ParamCmd paramCmd : ParamCmdSequence.keySet(this.compilationCommand)){
    //  this.ParamCmdSequence.put(this.compilationCommand, paramCmd, ParamCmdSequence.get(this.compilationCommand, paramCmd));
    //}
  //}

  public ParamMap getObjectInfo (ParamMap ParamCmdSequence, CompCmd compilationCommand) throws SQLException {
    //TODO: Check if the object exists.

    /* Get PGM info */
    switch (compilationCommand) {
      case CRTSQLRPGI: //TODO: This could be pgm or module
      case CRTBNDRPG :
      case CRTBNDCL :
      case CRTRPGPGM :
      case CRTCLPGM :
      case CRTSRVPGM : //TODO: Should this be here?
        ParamCmdSequence = getPgmInfo(ParamCmdSequence, this.targetKey.library, this.targetKey.objectName, this.targetKey.objectType);
        break;
     
      case RUNSQLSTM :

      case CRTDSPF : 
      case CRTPF :
      case CRTLF :

      case CRTPRTF :
        break;

      case CRTCMD :
        ParamCmdSequence = getCmdInfo(ParamCmdSequence, this.targetKey.library, this.targetKey.objectName);
        break;
    
      default:
        break;
    }

    /* Get module info */
    switch (compilationCommand) {
      case CRTSRVPGM : //TODO: Do I need to get the module info?
        ParamCmdSequence = getModuleInfo(ParamCmdSequence, this.targetKey.library, this.targetKey.objectName); 
        break;

      case CRTSQLRPGI: //TODO: This could be pgm or module
      case CRTRPGMOD :
      case CRTCLMOD :
        ParamCmdSequence = getModuleInfo(ParamCmdSequence, this.targetKey.library, this.targetKey.objectName); 
        break;
    
      default:
        break;
    }

    return ParamCmdSequence;
  }

  private ParamMap getPgmInfo(ParamMap ParamCmdSequence, String library, String objectName, ObjectType objectType) throws SQLException {
    
    try (Statement stmt = connection.createStatement();
        ResultSet rsObj = stmt.executeQuery(
          "With " +
          "Libs (Libraries) As ( " +
              "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
              "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
          ") " +
          "SELECT PROGRAM_LIBRARY, " + // programLibrary
              "PROGRAM_NAME, " + // programName
              "COALESCE(PROGRAM_TYPE,'') As PROGRAM_TYPE, " +  // [ILE, OPM] 
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
              "COALESCE(PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY, '') As PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY, " +
              "COALESCE(PROGRAM_ENTRY_PROCEDURE_MODULE, '') As PROGRAM_ENTRY_PROCEDURE_MODULE, " +
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
            "INNER JOIN Libs " +
            "ON (PROGRAM_LIBRARY = Libs.Libraries) " +
            "WHERE " + 
                "PROGRAM_NAME = '" + objectName + "' " +
                "AND OBJECT_TYPE = '" + objectType.toParam() + "' "
          )) {
      if (!rsObj.next()) {
        // TODO: Maybe this should be optional for new objects. Just throw a warning
        System.err.println(("Could not get object '" + objectName + "' from library '" + library + "' type" + "'" + objectType.toString() + "'"));
        return ParamCmdSequence;
        //throw new IllegalArgumentException("Could not get object '" + objectName + "' from library '" + library + "' type" + "'" + objectType.toString() + "'");
      }

      //TODO: Show method being executed

      if (verbose) System.out.println("Found object '" + objectName + "' from library '" + library + "' type " + "'" + objectType.toParam() + "'");

      //TODO: Could i change this to objectType for less casses?
      switch (this.compilationCommand) {
        case CRTSRVPGM:
        case CRTBNDRPG:
        case CRTBNDCL:
          String actgrp = rsObj.getString("ACTIVATION_GROUP").trim();
          if (!actgrp.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.ACTGRP, actgrp);
          if ("QILE".equals(actgrp)) ParamCmdSequence.put(this.compilationCommand, ParamCmd.DFTACTGRP, ValCmd.NO);
        case CRTRPGMOD:
          String stgMdl = rsObj.getString("STORAGE_MODEL").trim();
          if (!stgMdl.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.STGMDL, stgMdl);
        case CRTCLMOD:
        case CRTSQLRPGI:
        case CRTRPGPGM:
        case CRTCLPGM:
          String tgtRls = rsObj.getString("TARGET_RELEASE").trim();
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.TGTRLS, ValCmd.CURRENT);
          if (!tgtRls.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.TGTRLS, tgtRls);
        case CRTDSPF:
        case CRTPF:
        case CRTLF:
          String text = rsObj.getString("TEXT_DESCRIPTION").trim();
          if (!text.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.TEXT, "'" + text + "'");
          break;
      }

      switch (this.compilationCommand) {
        case CRTSRVPGM:
        case CRTBNDRPG:
        case CRTBNDCL:
        case CRTSQLRPGI:
        case CRTRPGPGM:
        case CRTCLPGM:
        case RUNSQLSTM:
          String tgtRls = rsObj.getString("TARGET_RELEASE").trim();
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.TGTRLS, ValCmd.CURRENT);
          if (!tgtRls.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.TGTRLS, tgtRls);

          String usrPrf = rsObj.getString("USER_PROFILE").trim();
          if (!usrPrf.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.USRPRF, usrPrf);
          break;
      }

      switch (this.compilationCommand) {
        case CRTBNDRPG:
        case CRTBNDCL:
        case CRTRPGMOD:
        case CRTCLMOD:
          String optimize = rsObj.getString("OPTIMIZATION").trim();
          if (!optimize.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTIMIZE, optimize);
        case CRTSQLRPGI:
        case CRTRPGPGM:
        case CRTCLPGM:
        case RUNSQLSTM:
        case CRTDSPF:
        case CRTPF:
        case CRTLF:
          String srtLib = rsObj.getString("SORT_SEQUENCE_LIBRARY").trim();
          String srtSeq = rsObj.getString("SORT_SEQUENCE").trim();
          if (!srtSeq.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRTSEQ, srtSeq + (srtLib.isEmpty() ? "" : " " + srtLib));

          String langId = rsObj.getString("LANGUAGE_ID").trim();
          if (!langId.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.LANGID, langId);
          break;
      }
      
      switch (this.compilationCommand) {
        case CRTBNDRPG:
        case CRTRPGMOD:
          String fixNbr = rsObj.getString("FIX_DECIMAL_DATA").trim();
          if (!fixNbr.isEmpty()){
            ParamCmdSequence.put(this.compilationCommand, ParamCmd.FIXNBR, fixNbr.equals("1") ? ValCmd.YES : ValCmd.NO);
          }

          String prfDta = rsObj.getString("PROFILING_DATA").trim();
          if (!prfDta.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.PRFDTA, prfDta);
          break;
      }

      switch (this.compilationCommand) {
        case CRTBNDCL:
        case CRTCLMOD:
        case CRTCLPGM:
          String logCmds = rsObj.getString("LOG_COMMANDS").trim();
          if (!logCmds.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.LOG, logCmds.equals("1") ? ValCmd.YES : ValCmd.NO);

          String alwRtvSrc = rsObj.getString("ALLOW_RTVCLSRC").trim();
          if (!alwRtvSrc.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.ALWRTVSRC, alwRtvSrc.equals("1") ? ValCmd.YES : ValCmd.NO);
          break;
      }

      switch (this.compilationCommand) {
        case CRTSRVPGM:
        case CRTBNDRPG:
        case CRTBNDCL:
        case CRTRPGMOD:
        case CRTCLMOD:
        case CRTSQLRPGI:
        case RUNSQLSTM:
        case CRTDSPF:
        case CRTPF:
        case CRTLF:
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTION, ValCmd.EVENTF);
          break;
      }

      switch (this.compilationCommand) {
        case CRTRPGPGM:
        case CRTCLPGM:
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTION, ValCmd.LSTDBG);
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.GENOPT, ValCmd.LIST);
          break;
      }

      /* Specific program type */
      String programType = rsObj.getString("PROGRAM_TYPE").trim();
      if (debug) System.out.println("PROGRAM_TYPE " + programType );

      //TODO: Remove this
      switch (programType) {
        case "ILE":
          try {
            ParamCmdSequence = getModuleInfo(ParamCmdSequence, rsObj.getString("PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY").trim(), 
                                          rsObj.getString("PROGRAM_ENTRY_PROCEDURE_MODULE").trim());
          } catch (IllegalArgumentException e) {
            if (verbose) System.err.println("Warning: Could not retrieve bound module info: " + e.getMessage() + ". Using defaults.");
            if (debug) e.printStackTrace();
          }
          break;
      
        case "OPM":
          break;
      }

      return ParamCmdSequence;

    }
  }

  // NEW: Query BOUND_MODULE_INFO for module-specific fields (e.g., GENLVL, OPTION)
  private ParamMap getModuleInfo(ParamMap ParamCmdSequence, String entryModuleLib, String entryModule) throws SQLException {
    if (entryModuleLib.isEmpty() || entryModule.isEmpty()) {
      System.err.println("Entry module or lib are empty");  // Skip if no entry module
      return ParamCmdSequence;
    } 

    try (Statement stmt = connection.createStatement();
        ResultSet rsMod = stmt.executeQuery(
          "With " +
          "Libs (Libraries) As ( " +
              "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
              "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
          ") " +
          "SELECT PROGRAM_LIBRARY, " +
                "PROGRAM_NAME, " +
                "OBJECT_TYPE, " +
                "BOUND_MODULE_LIBRARY, " +
                "BOUND_MODULE, " +
                "MODULE_ATTRIBUTE, " +
                "MODULE_CREATE_TIMESTAMP, " +
                "COALESCE(SOURCE_FILE_LIBRARY, '') As SOURCE_FILE_LIBRARY, " +
                "COALESCE(SOURCE_FILE, '') As SOURCE_FILE, " +
                "COALESCE(SOURCE_FILE_MEMBER, '') As SOURCE_FILE_MEMBER, " +
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
          "INNER JOIN Libs " +
            "ON (PROGRAM_LIBRARY = Libs.Libraries " +
            "AND BOUND_MODULE_LIBRARY = Libs.Libraries) " +
          "WHERE " +
            "PROGRAM_NAME = '" + this.targetKey.objectName + "' " +
            "AND BOUND_MODULE = '" + entryModule + "' "
        )) {
      if (!rsMod.next()) {
        System.err.println("Could not found module '" + entryModule + "' in library list");
        return ParamCmdSequence;
        //throw new IllegalArgumentException("Could not found module '" + entryModule + "' in library list");
      }

        //TODO: Validate the compilation command here
        //String dbgData = rsMod.getString("DEBUG_DATA").trim();
        //if ("*YES".equals(dbgData)) ParamCmdSequence.put(this.compilationCommand, ParamCmd.DBGVIEW, ValCmd.ALL);
        

        // Override OPTIMIZE if more specific here
        // This gives 10 but the param  OPTIMIZE only accepts: *NONE, *BASIC, *FULL   
        String modOptimize = rsMod.getString("OPTIMIZATION_LEVEL").trim();
        if (!modOptimize.isEmpty()) {
          switch (modOptimize) {
            case "10": ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTIMIZE, ValCmd.NONE);   break;
            case "20": ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTIMIZE, ValCmd.BASIC);  break;
            case "30": ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTIMIZE, ValCmd.BASIC);  break;
            case "40": ParamCmdSequence.put(this.compilationCommand, ParamCmd.OPTIMIZE, ValCmd.FULL);   break;
          }
        }

        // Update source if more accurate
        String modSrcLib = rsMod.getString("SOURCE_FILE_LIBRARY").trim();
        if (!modSrcLib.isEmpty()) {
          //TODO: I'm not sure if this will work the first time.
          String sourceLibrary = modSrcLib.toUpperCase();
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCFILE, sourceLibrary + "/" + sourceFile);  // Update
        }
        String modSrcFil = rsMod.getString("SOURCE_FILE").trim();
        String modSrcMbr = rsMod.getString("SOURCE_FILE_MEMBER").trim();
        String modSteamFile = rsMod.getString("SOURCE_STREAM_FILE_PATH").trim();
        //TODO: Here, if (modSteamFile.isEmpty()) do the migration to /temp or another route. and do the compilation
        String modCCSID = rsMod.getString("MODULE_CCSID").trim();

        // Add more mappings (e.g., DEFINE, INCDIR, PPGENOPT)
        return ParamCmdSequence;
      
    }
  }

  private ParamMap getCmdInfo(ParamMap ParamCmdSequence, String library, String objectName) throws SQLException {
    
    try (Statement stmt = connection.createStatement();
        ResultSet rsCmdInfo = stmt.executeQuery(
          //TODO: I could make this cursor filter params and just iter over it like a Key/Value pair to automatically PUT the compilation params
          // but for that i would need something like a pivot so i don't know if it is worth it.
          "With " + //TODO: Put this lib CTE in a String
          "Libs (Libraries) As ( " +
              "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
              "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
          ") " +
          "SELECT " +
            "(TRIM(COMMAND_LIBRARY) || '/' || TRIM(COMMAND_NAME)) As CMD, " +
            "TEXT_DESCRIPTION, " +
            "(TRIM(COMMAND_PROCESSING_PROGRAM_LIBRARY) || '/' || TRIM(COMMAND_PROCESSING_PROGRAM)) As PGM, " +
            "(TRIM(SOURCE_FILE_LIBRARY) || '/' || TRIM(SOURCE_FILE)) As SRCFILE, " +
            "SOURCE_FILE_MEMBER As SRCMBR, " +
            "THREADSAFE As THDSAFE " +
            "FROM QSYS2.COMMAND_INFO " +
            "INNER JOIN Libs " +
            "ON (COMMAND_LIBRARY = Libs.Libraries) " +
            "WHERE " + 
                "COMMAND_NAME = '" + objectName + "' "
          )) {
      if (!rsCmdInfo.next()) {
        //TODO: Check if object exist before getting the info.
        System.err.println(("Could not get command '" + objectName + "' from library list"));
        return ParamCmdSequence;
        //throw new IllegalArgumentException("Could not get object '" + objectName + "' from library '" + library + "' type" + "'" + objectType.toString() + "'");
      }

      if (verbose) System.out.println("Found command '" + objectName + "' in library list");
      
      // -- Missing: REXSRCFILE, REXSRCMBR, REXCMDENV, REXEXITPGM
      String cmd = rsCmdInfo.getString("CMD").trim();
      if(!cmd.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.CMD, cmd); 

      String pgm = rsCmdInfo.getString("PGM").trim();
      if(!pgm.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.PGM, pgm); 

      String srcfile = rsCmdInfo.getString("SRCFILE").trim();
      if(!srcfile.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCFILE, srcfile); 

      String srcmbr = rsCmdInfo.getString("SRCMBR").trim();
      if(!srcmbr.isEmpty()) ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCMBR, srcmbr); 

      ValCmd threadsafe = ValCmd.fromString(rsCmdInfo.getString("THDSAFE").trim());
      ParamCmdSequence.put(this.compilationCommand, ParamCmd.THDSAFE, threadsafe); 

      

      return ParamCmdSequence;

      }
  }

  private void getSrvpgmInfo(){

  }

}