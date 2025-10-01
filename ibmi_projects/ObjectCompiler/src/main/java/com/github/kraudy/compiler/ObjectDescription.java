package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

// Core struct for capturing compilation specs (JSON-friendly via Jackson)
public class ObjectDescription {
  private final Connection connection;
  private final boolean debug;
  //TODO: Make this private, add set method and move to another file
  //public String targetLibrary;
  // public String objectName;
  //public ObjectType objectType;
  public String sourceLibrary;
  public String sourceFile;
  public String sourceName;
  public SourceType sourceType;
  Utilities.ParsedKey targetKey;

  public Map<CompilationPattern.ParamCmd, String> ParamCmdSequence = new HashMap<>();


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

  @JsonCreator
  public ObjectDescription(
        Connection connection,
        boolean debug,
        @JsonProperty("targetKey") Utilities.ParsedKey targetKey,
        @JsonProperty("sourceLibrary") String sourceLibrary,
        @JsonProperty("sourceFile") String sourceFile,
        @JsonProperty("sourceName") String sourceName) {

    this.connection = connection;
    this.debug = debug;

    this.targetKey = targetKey;
    this.sourceLibrary = sourceLibrary;
    this.sourceFile = (sourceFile.isEmpty()) ? SourceType.defaultSourcePf(this.targetKey.sourceType) : sourceFile; // TODO: Add logic for sourcePF or directory
    this.sourceName = (sourceName.isEmpty() ? this.targetKey.objectName : sourceName); //TODO: Add logic for stream files / members / default

    /* Generate compilation params values from object description */
    //TODO: I'm not sure if these are needed now or maybe add them to the input validation in Utilities
    //if (this.targetLibrary.isEmpty()) this.targetLibrary = ValCmd.LIBL.toString();
    //if (this.sourceName.isEmpty())    this.sourceName = CompCmd.compilationSourceName(compilationCommand);//ValCmd.PGM.toString();

    /* Set default values */
    //TODO: Condition wich of these are used to not put them all
    // maybe based on object type?
    ParamCmdSequence.put(ParamCmd.OBJ, this.targetKey.library + "/" + this.targetKey.objectName);
    ParamCmdSequence.put(ParamCmd.PGM, this.targetKey.library + "/" + this.targetKey.objectName);
    ParamCmdSequence.put(ParamCmd.SRVPGM, this.targetKey.library + "/" + this.targetKey.objectName);
    ParamCmdSequence.put(ParamCmd.MODULE, this.targetKey.library + "/" + this.targetKey.objectName);

    ParamCmdSequence.put(ParamCmd.OBJTYPE, this.targetKey.objectType.toParam());

    ParamCmdSequence.put(ParamCmd.SRCFILE, this.sourceLibrary + "/" + this.sourceFile);

    ParamCmdSequence.put(ParamCmd.SRCMBR, this.sourceName);

    
    ParamCmdSequence.put(ParamCmd.BNDSRVPGM, ValCmd.NONE.toString());
    ParamCmdSequence.put(ParamCmd.COMMIT, ValCmd.NONE.toString());

  }

  // Getters for Jackson serialization
  //public String getTargetLibrary() { return targetLibrary; }
  //public String getObjectName() { return objectName; }
  //public ObjectType getObjectType() { return objectType; }
  public String getSourceLibrary() { return sourceLibrary; }
  public String getSourceFile() { return sourceFile; }
  public String getSourceName() { return sourceName; }
  public SourceType getSourceType() { return this.targetKey.sourceType; }
  public Map<CompilationPattern.ParamCmd, String> getParamCmdSequence() { return ParamCmdSequence; }

  public void setParamsSequence(Map<CompilationPattern.ParamCmd, String> ParamCmdSequence) {
    for (CompilationPattern.ParamCmd paramCmd : ParamCmdSequence.keySet()){
      this.ParamCmdSequence.put(paramCmd, ParamCmdSequence.get(paramCmd));
    }
  }

  public void getObjectInfo() throws SQLException {
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
            "WHERE PROGRAM_LIBRARY = '" + this.targetKey.library + "' " +
                "AND PROGRAM_NAME = '" + this.targetKey.objectName + "' " +
                "AND OBJECT_TYPE = '" + this.targetKey.objectType.toParam() + "' "
          )) {
      if (!rsObj.next()) {
        // TODO: Maybe this should be optional for new objects. Just throw a warning
        throw new IllegalArgumentException("Could not get object '" + this.targetKey.objectName + "' from library '" + this.targetKey.library + "' type" + "'" + this.targetKey.objectType.toString() + "'");
      }

      System.out.println("Found object '" + this.targetKey.objectName + "' from library '" + this.targetKey.library + "' type " + "'" + this.targetKey.objectType.toParam() + "'");

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
        if ("QILE".equals(actgrp)) ParamCmdSequence.put(ParamCmd.DFTACTGRP, ValCmd.NO.toString());


         retrieveBoundModuleInfo(rsObj.getString("PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY").trim(), 
                                         rsObj.getString("PROGRAM_ENTRY_PROCEDURE_MODULE").trim());
      }

      // For OPM-specific (e.g., SAAFLAG, if in view)
      if ("OPM".equals(programType)) {
        // Map OPM fields similarly if present in query
      }

      if (!debug) return;

      System.out.println("All data: ");
      for (CompilationPattern.ParamCmd paramCmd : this.ParamCmdSequence.keySet()){
        System.out.println(paramCmd.name() + ": " + this.ParamCmdSequence.get(paramCmd));
      }
      
    }
  }

  // NEW: Query BOUND_MODULE_INFO for module-specific fields (e.g., GENLVL, OPTION)
  public void retrieveBoundModuleInfo(String entryModuleLib, String entryModule) throws SQLException {
    if (entryModuleLib == null || entryModule == null) throw new IllegalArgumentException("Entry module or lib are null");;  // Skip if no entry module

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
          "WHERE PROGRAM_LIBRARY = '" + this.targetKey.library + "' " +
            "AND PROGRAM_NAME = '" + this.targetKey.objectName + "' " +
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
        */

        //TODO: Should i validate the compilation command here?
        String dbgData = rsMod.getString("DEBUG_DATA").trim();
        if ("*YES".equals(dbgData)) ParamCmdSequence.put(ParamCmd.DBGVIEW, ValCmd.ALL.toString());
        

        // Override OPTIMIZE if more specific here
        // This gives 10 but the param  OPTIMIZE only accepts: *NONE, *BASIC, *FULL   
        String modOptimize = rsMod.getString("OPTIMIZATION_LEVEL").trim();
        if (!modOptimize.isEmpty()) {
          switch (modOptimize) {
            case "10": ParamCmdSequence.put(ParamCmd.OPTIMIZE, ValCmd.NONE.toString());   break;
            case "20": ParamCmdSequence.put(ParamCmd.OPTIMIZE, ValCmd.BASIC.toString());  break;
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

        // Add more mappings (e.g., DEFINE, INCDIR, PPGENOPT)
      
    }
  }

  

}