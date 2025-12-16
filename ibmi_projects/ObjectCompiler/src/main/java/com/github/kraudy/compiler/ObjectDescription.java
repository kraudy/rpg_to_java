package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;
import com.github.kraudy.migrator.SourceMigrator;
import com.github.kraudy.compiler.CompilationPattern.ObjectType;

/* Core struct for capturing compilation specs */
public class ObjectDescription {
  private final Connection connection;
  private final boolean debug;
  private final boolean verbose;
  private TargetKey targetKey;

  public ObjectDescription(Connection connection, boolean debug, boolean verbose, TargetKey targetKey) {
    this.connection = connection;
    this.debug = debug;
    this.verbose = verbose;
    this.targetKey = targetKey;
  }


  public void migrateSource(SourceMigrator migrator){
    switch (this.targetKey.getCompilationCommand()){
      case CRTCLMOD:
        break;

      case CRTRPGMOD:
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTSQLRPGI:
      case CRTSRVPGM:
      case RUNSQLSTM:
        if (!this.targetKey.containsKey(ParamCmd.SRCSTMF) && 
          this.targetKey.containsKey(ParamCmd.SRCFILE)) {
          System.out.println("SRCFILE data: " + this.targetKey.getParamMap().get(ParamCmd.SRCFILE));
          migrator.setParams(this.targetKey.getQualifiedSourceFile(), this.targetKey.getObjectName(), "sources");
          migrator.api(); // Try to migrate this thing
          
          this.targetKey.setStreamSourceFile(migrator.getFirstPath());
          this.targetKey.put(ParamCmd.SRCSTMF, this.targetKey.getStreamFile());
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
        if (this.targetKey.containsStreamFile()) {
          //TODO: Do reverse migration here
        }
        break;

      case CRTDSPF:
      case CRTPF:
      case CRTLF:
      case CRTPRTF:
      case CRTMNU:
      case CRTQMQRY:
          break;
    }
  }

  //TODO: Just send the key here.
  public void getObjectInfo () throws SQLException {
    //TODO: Check if the object exists.

    /* Get PGM info */
    switch (this.targetKey.getCompilationCommand()) {
      case CRTSQLRPGI: //TODO: This could be pgm or module
      case CRTBNDRPG :
      case CRTBNDCL :
      case CRTRPGPGM :
      case CRTCLPGM :
      case CRTSRVPGM : //TODO: Should this be here?
        getPgmInfo(this.targetKey.library, this.targetKey.objectName, this.targetKey.objectType);
        break;
     
      case RUNSQLSTM :

      case CRTDSPF : 
      case CRTPF :
      case CRTLF :

      case CRTPRTF :
        break;

      case CRTCMD :
        getCmdInfo(this.targetKey.library, this.targetKey.objectName);
        break;
    
      default:
        break;
    }

    /* Get module info */
    switch (this.targetKey.getCompilationCommand()) {
      case CRTSRVPGM : //TODO: Do I need to get the module info?
        getModuleInfo(this.targetKey.library, this.targetKey.objectName); 
        break;

      case CRTSQLRPGI: //TODO: This could be pgm or module
      case CRTRPGMOD :
      case CRTCLMOD :
        getModuleInfo(this.targetKey.library, this.targetKey.objectName); 
        break;
    
      default:
        break;
    }

    return ;
  }

  private void getPgmInfo(String library, String objectName, ObjectType objectType) throws SQLException {
    
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
              "COALESCE(TEXT_DESCRIPTION, '') As TEXT, " + // textDescription
              "PROGRAM_OWNER, " + // owner
              "PROGRAM_ATTRIBUTE, " + // attribute
              "USER_PROFILE As USRPRF, " +
              "USE_ADOPTED_AUTHORITY, " +
              "RELEASE_CREATED_ON, " +
              "COALESCE(TARGET_RELEASE, '') As TGTRLS, " +
              "MINIMUM_NUMBER_PARMS, " + // minParameters
              "MAXIMUM_NUMBER_PARMS, " + // maxParameters
              "PAGING_POOL, " +
              "PAGING_AMOUNT, " +
              "COALESCE(ALLOW_RTVCLSRC, '') As ALWRTVSRC, " + // allowRTVCLSRC
              "CONVERSION_REQUIRED, " +
              "CONVERSION_DETAIL, " +
              //-- These seem to be for ILE objects
              "COALESCE(PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY, '') As PROGRAM_ENTRY_PROCEDURE_MODULE_LIBRARY, " +
              "COALESCE(PROGRAM_ENTRY_PROCEDURE_MODULE, '') As PROGRAM_ENTRY_PROCEDURE_MODULE, " +
              "COALESCE(ACTIVATION_GROUP, '') AS ACTGRP, " + // activationGroupAttribute
              "SHARED_ACTIVATION_GROUP, " +
              "OBSERVABLE_INFO_COMPRESSED, " +
              "RUNTIME_INFO_COMPRESSED, " +
              "ALLOW_UPDATE, " +
              "ALLOW_BOUND_SRVPGM_LIBRARY_UPDATE, " +
              "ALL_CREATION_DATA, " +
              "COALESCE(PROFILING_DATA, '') As PRFDTA, " +
              "TERASPACE_STORAGE_ENABLED_MODULES, " +
              "TERASPACE_STORAGE_ENABLED_PEP, " +
              "COALESCE(STORAGE_MODEL , '') As STGMDL, " +
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
              "COALESCE(LANGUAGE_ID, '') As LANGID, " +
              "OBSERVABLE, " + // observable
              "COALESCE(OPTIMIZATION, '') As OPTIMIZE, " +
              "COALESCE(LOG_COMMANDS, '' ) As LOG, " +
              "COALESCE(FIX_DECIMAL_DATA, '') As FIXNBR, " + // fixDecimalData
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
        //throw new IllegalArgumentException("Could not get object '" + objectName + "' from library '" + library + "' type" + "'" + objectType.toString() + "'");
      }

      if (verbose) System.out.println("Found object '" + objectName + "' from library '" + library + "' type " + "'" + objectType.toParam() + "'");

      //TODO: Could i change this to objectType for less casses?
      switch (this.targetKey.getCompilationCommand()) {
        case CRTSRVPGM:
        case CRTBNDRPG:
        case CRTBNDCL:
          String actgrp = rsObj.getString("ACTGRP").trim();
          if (!actgrp.isEmpty()) this.targetKey.put(ParamCmd.ACTGRP, actgrp);
          if ("QILE".equals(actgrp)) this.targetKey.put(ParamCmd.DFTACTGRP, ValCmd.NO);
        case CRTRPGMOD:
          String stgMdl = rsObj.getString("STGMDL").trim();
          if (!stgMdl.isEmpty()) this.targetKey.put(ParamCmd.STGMDL, stgMdl);
        case CRTCLMOD:
        case CRTSQLRPGI:
        case CRTRPGPGM:
        case CRTCLPGM:
          String tgtRls = rsObj.getString("TGTRLS").trim();
          this.targetKey.put(ParamCmd.TGTRLS, ValCmd.CURRENT);
          if (!tgtRls.isEmpty()) this.targetKey.put(ParamCmd.TGTRLS, tgtRls);
        case CRTDSPF:
        case CRTPF:
        case CRTLF:
          String text = rsObj.getString("TEXT").trim();
          if (!text.isEmpty()) this.targetKey.put(ParamCmd.TEXT, text);
          break;
      }

      switch (this.targetKey.getCompilationCommand()) {
        case CRTSRVPGM:
        case CRTBNDRPG:
        case CRTBNDCL:
        case CRTSQLRPGI:
        case CRTRPGPGM:
        case CRTCLPGM:
        case RUNSQLSTM:
          String tgtRls = rsObj.getString("TGTRLS").trim();
          this.targetKey.put(ParamCmd.TGTRLS, ValCmd.CURRENT);
          if (!tgtRls.isEmpty()) this.targetKey.put(ParamCmd.TGTRLS, tgtRls);

          String usrPrf = rsObj.getString("USRPRF").trim();
          if (!usrPrf.isEmpty()) this.targetKey.put(ParamCmd.USRPRF, usrPrf);
          break;
      }

      switch (this.targetKey.getCompilationCommand()) {
        case CRTBNDRPG:
        case CRTBNDCL:
        case CRTRPGMOD:
        case CRTCLMOD:
          String optimize = rsObj.getString("OPTIMIZE").trim();
          if (!optimize.isEmpty()) this.targetKey.put(ParamCmd.OPTIMIZE, optimize);
        case CRTSQLRPGI:
        case CRTRPGPGM:
        case CRTCLPGM:
        case RUNSQLSTM:
        case CRTDSPF:
        case CRTPF:
        case CRTLF:
          String srtLib = rsObj.getString("SORT_SEQUENCE_LIBRARY").trim();
          String srtSeq = rsObj.getString("SORT_SEQUENCE").trim();
          if (!srtSeq.isEmpty()) this.targetKey.put(ParamCmd.SRTSEQ, srtSeq + (srtLib.isEmpty() ? "" : " " + srtLib));

          String langId = rsObj.getString("LANGID").trim();
          if (!langId.isEmpty()) this.targetKey.put(ParamCmd.LANGID, langId);
          break;
      }
      
      switch (this.targetKey.getCompilationCommand()) {
        case CRTBNDRPG:
        case CRTRPGMOD:
          String fixNbr = rsObj.getString("FIXNBR").trim();
          if (!fixNbr.isEmpty()){
            this.targetKey.put(ParamCmd.FIXNBR, fixNbr.equals("1") ? ValCmd.YES : ValCmd.NO);
          }

          String prfDta = rsObj.getString("PRFDTA").trim();
          if (!prfDta.isEmpty()) this.targetKey.put(ParamCmd.PRFDTA, prfDta);
          break;
      }

      switch (this.targetKey.getCompilationCommand()) {
        case CRTBNDCL:
        case CRTCLMOD:
        case CRTCLPGM:
          String logCmds = rsObj.getString("LOG").trim();
          if (!logCmds.isEmpty()) this.targetKey.put(ParamCmd.LOG, logCmds.equals("1") ? ValCmd.YES : ValCmd.NO);

          String alwRtvSrc = rsObj.getString("ALWRTVSRC").trim();
          if (!alwRtvSrc.isEmpty()) this.targetKey.put(ParamCmd.ALWRTVSRC, alwRtvSrc.equals("1") ? ValCmd.YES : ValCmd.NO);
          break;
      }


    }
  }

  /* Query BOUND_MODULE_INFO for module-specific fields */
  private void getModuleInfo(String entryModuleLib, String entryModule) throws SQLException {
    if (entryModuleLib.isEmpty() || entryModule.isEmpty()) {
      System.err.println("Entry module or lib are empty");  // Skip if no entry module
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
                "LANGUAGE_ID As LANGID, " +
                "DEBUG_DATA, " +
                "COALESCE(OPTIMIZATION_LEVEL, '') As OPTIMIZE, " +
                "MAX_OPTIMIZATION_LEVEL, " +
                "OBJECT_CONTROL_LEVEL, " +
                "RELEASE_CREATED_ON, " +
                "TARGET_RELEASE AS TGTRLS, " +
                "CREATION_DATA, " +
                "TERASPACE_STORAGE_ENABLED, " +
                "STORAGE_MODEL, " +
                "NUMBER_PROCEDURES, " +
                "NUMBER_PROCEDURES_BLOCK_REORDERED, " +
                "NUMBER_PROCEDURES_BLOCK_ORDER_MEASURED, " +
                "PROFILING_DATA As PRFDTA, " +
                "ALLOW_RTVCLSRC As ALWRTVSRC, " +
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
        return;
        //throw new IllegalArgumentException("Could not found module '" + entryModule + "' in library list");
      }
        // Override OPTIMIZE if more specific here
        // This gives 10 but the param  OPTIMIZE only accepts: *NONE, *BASIC, *FULL   
        String modOptimize = rsMod.getString("OPTIMIZE").trim();
        if (!modOptimize.isEmpty()) {
          switch (modOptimize) {
            case "10": this.targetKey.put(ParamCmd.OPTIMIZE, ValCmd.NONE);   break;
            case "20": this.targetKey.put(ParamCmd.OPTIMIZE, ValCmd.BASIC);  break;
            case "30": this.targetKey.put(ParamCmd.OPTIMIZE, ValCmd.BASIC);  break;
            case "40": this.targetKey.put(ParamCmd.OPTIMIZE, ValCmd.FULL);   break;
          }
        }

        // Update source if more accurate
        String modSrcLib = rsMod.getString("SOURCE_FILE_LIBRARY").trim();
        if (!modSrcLib.isEmpty()) {
          //TODO: I'm not sure if this will work the first time.
          String sourceLibrary = modSrcLib.toUpperCase();
          //TODO: if you want to do this, change the sourceFile in the targetKey and then put() it
          //ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCFILE, sourceLibrary + "/" + sourceFile);  // Update
        }
        String modSrcFil = rsMod.getString("SOURCE_FILE").trim();
        String modSrcMbr = rsMod.getString("SOURCE_FILE_MEMBER").trim();
        String modSteamFile = rsMod.getString("SOURCE_STREAM_FILE_PATH").trim();
        //TODO: Here, if (modSteamFile.isEmpty()) do the migration to /temp or another route. and do the compilation
        String modCCSID = rsMod.getString("MODULE_CCSID").trim();

        // Add more mappings (e.g., DEFINE, INCDIR, PPGENOPT)
      
    }
  }

  private void getCmdInfo(String library, String objectName) throws SQLException {
    
    try (Statement stmt = connection.createStatement();
        ResultSet rsCmdInfo = stmt.executeQuery(
          "With " + //TODO: Put this lib CTE in a String
          "Libs (Libraries) As ( " +
              "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
              "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
          ") " +
          "SELECT " +
            "(TRIM(COMMAND_LIBRARY) || '/' || TRIM(COMMAND_NAME)) As CMD, " +
            "TEXT_DESCRIPTION As TEXT, " +
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
        //throw new IllegalArgumentException("Could not get object '" + objectName + "' from library '" + library + "' type" + "'" + objectType.toString() + "'");
      }

      if (verbose) System.out.println("Found command '" + objectName + "' in library list");
      
      // -- Missing: REXSRCFILE, REXSRCMBR, REXCMDENV, REXEXITPGM
      String cmd = rsCmdInfo.getString("CMD").trim();
      if(!cmd.isEmpty()) this.targetKey.put(ParamCmd.CMD, cmd); 

      String pgm = rsCmdInfo.getString("PGM").trim();
      if(!pgm.isEmpty()) this.targetKey.put(ParamCmd.PGM, pgm); 

      String srcfile = rsCmdInfo.getString("SRCFILE").trim();
      if(!srcfile.isEmpty()) this.targetKey.put(ParamCmd.SRCFILE, srcfile); 

      String srcmbr = rsCmdInfo.getString("SRCMBR").trim();
      if(!srcmbr.isEmpty()) this.targetKey.put(ParamCmd.SRCMBR, srcmbr); 

      ValCmd threadsafe = ValCmd.fromString(rsCmdInfo.getString("THDSAFE").trim());
      this.targetKey.put(ParamCmd.THDSAFE, threadsafe); 

      


      }
  }

  private void getSrvpgmInfo(){

  }

}