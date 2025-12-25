package com.github.kraudy.compiler;

import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;
import com.github.kraudy.migrator.SourceMigrator;

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
      case CRTRPGMOD:
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTSQLRPGI:
      case CRTSRVPGM:
      case RUNSQLSTM:
      case CRTCMD:
        /* 
         * Migrate from source member to stream file
         */
        if (!this.targetKey.containsKey(ParamCmd.SRCSTMF) && 
          this.targetKey.containsKey(ParamCmd.SRCFILE)) {
          migrator.setMigrationParams(this.targetKey.getQualifiedSourceFile(), this.targetKey.getObjectName(), "sources");
          migrator.api(); // Try to migrate this thing
          
          this.targetKey.setStreamSourceFile(migrator.getFirstPath());
          this.targetKey.put(ParamCmd.SRCSTMF, this.targetKey.getStreamFile());
        }
        break;

      case CRTCLPGM:
      case CRTRPGPGM:
      case CRTDSPF:
      case CRTPF:
      case CRTLF:
      case CRTPRTF:
      case CRTMNU:
      case CRTQMQRY:
        /* 
         * Migrate from stream file to source member
         */
        if (this.targetKey.containsStreamFile()) {
          /* I'd like this to be migrated to QTEMP but the migrator needs some changes for that */
          migrator.setReverseMigrationParams(this.targetKey.getQualifiedSourceFile(), this.targetKey.getObjectName(), this.targetKey.getStreamFile());
          migrator.api(); // Try to migrate this thing
          
          this.targetKey.setStreamSourceFile(migrator.getFirstPath());
          this.targetKey.put(ParamCmd.SRCFILE, this.targetKey.getQualifiedSourceFile());
          this.targetKey.put(ParamCmd.SRCMBR, this.targetKey.getObjectName());
        }
        break;
    }
  }

  public boolean sourcePfExists() throws SQLException{
    //TODO: Change this for library list
    // Validate if Source PF exists
    try (Statement validateStmt = connection.createStatement();
        ResultSet validateRs = validateStmt.executeQuery(
            "SELECT 1 AS Exist FROM QSYS2. SYSPARTITIONSTAT " +
                "WHERE SYSTEM_TABLE_SCHEMA = '" + this.targetKey.getLibrary() + "' " +
                "AND SYSTEM_TABLE_NAME = '" + this.targetKey.getSourceFile() + "' " +
                "AND TRIM(SOURCE_TYPE) <> '' LIMIT 1")) {
      if (validateRs.next()) {
        if (verbose) System.err.println(" *Source PF " + this.targetKey.getSourceFile() + " already exist in library " + this.targetKey.getLibrary());
        return true;
      }
      return false;
    }
  }

  public boolean sourceMemberExists() throws SQLException {
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery(
             "SELECT CAST(SYSTEM_TABLE_MEMBER AS VARCHAR(10) CCSID " + SourceMigrator.INVARIANT_CCSID + ") AS Member " +
             "FROM QSYS2.SYSPARTITIONSTAT " +
             "WHERE SYSTEM_TABLE_SCHEMA = '" + this.targetKey.getLibrary() + "' " +
             "AND SYSTEM_TABLE_NAME = '" + this.targetKey.getSourceFile() + "' " +
             "AND SYSTEM_TABLE_MEMBER = '" + this.targetKey.getSourceName() + "' " +
             "AND TRIM(SOURCE_TYPE) <> '' ")) { 
      if (rs.next()) {
        if (verbose) System.err.println("Member " + this.targetKey.getSourceName() + " already exist in library " + this.targetKey.getLibrary());
        return true;
      }
      return false;
    }
  }

  /* Get Pgm, Module and SrvPgm objects creation timestamp */
  public void getObjectCreation () throws SQLException {
    try (Statement stmt = connection.createStatement();
        ResultSet rsObjCreationInfo = stmt.executeQuery(
          "With " +
          "Libs (Libraries) As ( " +
              "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
              "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
          ") " +
          "SELECT " +
              "CREATE_TIMESTAMP, " + // creationDateTime
              "SOURCE_FILE_CHANGE_TIMESTAMP " + // sourceUpdatedDateTime
            "FROM QSYS2.PROGRAM_INFO " +
            "INNER JOIN Libs " +
            "ON (PROGRAM_LIBRARY = Libs.Libraries) " +
            "WHERE " + 
                "PROGRAM_NAME = '" + this.targetKey.getObjectName() + "' " +
                "AND OBJECT_TYPE = '" + this.targetKey.getObjectType() + "' "
          )) {
      if (!rsObjCreationInfo.next()) {
        System.err.println(("Could not extract object creation time '" + this.targetKey.asString() ));
        return;
      }

      if (verbose) System.out.println("Found object creation data '" + this.targetKey.asString());

      this.targetKey.setLastBuild(rsObjCreationInfo.getTimestamp("CREATE_TIMESTAMP"));
      this.targetKey.setLastEdit(rsObjCreationInfo.getTimestamp("SOURCE_FILE_CHANGE_TIMESTAMP"));

    }
  }

  public void getObjectTimestamps() throws SQLException {
    /* Get object creation timestamp */
    getObjectCreation();

    /* Get source stream file last change */
    if (this.targetKey.containsStreamFile()){
      //TODO: Add git diff
      getSourceStreamFileLastChange();
      return;
    }
    /* Get source member last change */
    getSourceMemberLastChange();
    return;
  }

  public void getSourceMemberLastChange() throws SQLException {
    try (Statement stmt = connection.createStatement();
          ResultSet rs = stmt.executeQuery(
            "With " +
            "Libs (Libraries) As ( " +
                "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
                "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
            ") " +
              "SELECT LAST_SOURCE_UPDATE_TIMESTAMP FROM QSYS2.SYSPARTITIONSTAT " +
              "INNER JOIN Libs " +
              "ON (TABLE_SCHEMA = Libs.Libraries) " +
              "WHERE TABLE_NAME = '" + this.targetKey.getSourceFile() + "' " +
              "AND TABLE_PARTITION = '" + this.targetKey.getSourceName() + "'" +
              "AND SOURCE_TYPE = '" + this.targetKey.getSourceType() + "'")) {
        if (!rs.next()) {
          if (verbose) System.out.println("Could not get source member last change: " + this.targetKey.getSourceName());
          this.targetKey.setLastEdit(null);  // File not found
          return;  
        }

        if (verbose) System.out.println("Found source member last change: " + this.targetKey.getSourceName());
        this.targetKey.setLastEdit(rs.getTimestamp("LAST_SOURCE_UPDATE_TIMESTAMP"));
        return;
    }
  }

  public void getSourceStreamFileLastChange() throws SQLException {
    try (Statement stmt = connection.createStatement();
          ResultSet rs = stmt.executeQuery(
            "SELECT DATA_CHANGE_TIMESTAMP " + 
            "FROM TABLE (QSYS2.IFS_OBJECT_STATISTICS( " +
                    "START_PATH_NAME => '" + this.targetKey.getStreamFile() +  "', " +
                    "SUBTREE_DIRECTORIES => 'NO' " +
                ") " +
            ")")) {
        if (!rs.next()) {
            if (verbose) System.out.println("Could not get source stream file last change: " + this.targetKey.getStreamFile());
          this.targetKey.setLastEdit(null);  // File not found
          return;
        }
        
        if (verbose) System.out.println("Found source stream file last change: " + this.targetKey.getStreamFile());
        this.targetKey.setLastEdit(rs.getTimestamp("DATA_CHANGE_TIMESTAMP"));
        return;
    }
  }

  public void getObjectInfo () throws SQLException {

    switch (this.targetKey.getCompilationCommand()) {
      /* PGM info */
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTRPGPGM:
      case CRTCLPGM:
        getPgmInfo();
        break;

      /* Sql Rpg info */
      case CRTSQLRPGI:
        getSqlRpgInfo();
        break;

      /* SrvPgm info */
      case CRTSRVPGM:
        getSrvpgmInfo();
        break;
     
      /* Module info */
      case CRTRPGMOD :
      case CRTCLMOD :
        getModuleInfo(); 
        break;

      /* Sql info */
      case RUNSQLSTM :
        getSqlInfo();
        break;

      /* Dds info */
      case CRTDSPF: 
      case CRTPF:
      case CRTLF:
      case CRTPRTF:
        getDdsInfo();
        break;

      /* Cmd info */
      case CRTCMD :
        getCmdInfo();
        break;
    
      default:
        break;
    }

    return;
  }

  /* This should work for *PGM, *SRVPGM, *MODULE */
  private void getPgmInfo() throws SQLException {
    
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
              "COALESCE(TEXT_DESCRIPTION, '') As TEXT, " + // textDescription
              "PROGRAM_OWNER, " + // owner
              "PROGRAM_ATTRIBUTE, " + // attribute
              "USER_PROFILE As USRPRF, " +
              "USE_ADOPTED_AUTHORITY, " +
              "RELEASE_CREATED_ON, " +
              "COALESCE(TARGET_RELEASE, '') As TGTRLS, " +
              "MINIMUM_NUMBER_PARMS, " + // minParameters
              "MAXIMUM_NUMBER_PARMS, " + // maxParameters
              "COALESCE(ALLOW_RTVCLSRC, '') As ALWRTVSRC, " + // allowRTVCLSRC
              "CONVERSION_REQUIRED, " +
              "CONVERSION_DETAIL, " +
              //-- These seem to be for ILE objects
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
              // Module related data
              "COPYRIGHTS, " +
              "COPYRIGHT_STRINGS, " +
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
              "(TRIM(SOURCE_FILE_LIBRARY) || '/' || TRIM(SOURCE_FILE)) As SRCFILE, " +
              "SOURCE_FILE_MEMBER As SRCMBR, " +
              "COALESCE((TRIM(SQL_SORT_SEQUENCE_LIBRARY) || '/' || TRIM(SQL_SORT_SEQUENCE)), '') As SRTSEQ, " +
              "COALESCE(LANGUAGE_ID, '') As LANGID, " +
              "OBSERVABLE, " + // observable
              "COALESCE(OPTIMIZATION, '') As OPTIMIZE, " +
              "COALESCE(LOG_COMMANDS, '' ) As LOG, " +
              "COALESCE(FIX_DECIMAL_DATA, '') As FIXNBR, " + // fixDecimalData
              "TERASPACE_STORAGE_ENABLED_PROGRAM " + // teraspaceEnabled
            "FROM QSYS2.PROGRAM_INFO " +
            "INNER JOIN Libs " +
            "ON (PROGRAM_LIBRARY = Libs.Libraries) " +
            "WHERE " + 
                "PROGRAM_NAME = '" + this.targetKey.getObjectName() + "' " +
                "AND OBJECT_TYPE = '" + this.targetKey.getObjectType() + "' "
          )) {
      if (!rsObj.next()) {
        System.err.println(("Could not get object '" + this.targetKey.asString() ));
        return;
      }

      if (verbose) System.out.println("Found object '" + this.targetKey.asString());


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
        case CRTRPGPGM:
        case CRTCLPGM:
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
        case CRTRPGPGM:
        case CRTCLPGM:
        case CRTDSPF:
        case CRTPF:
        case CRTLF:
          String srtSeq = rsObj.getString("SRTSEQ").trim();
          if (!srtSeq.isEmpty()) this.targetKey.put(ParamCmd.SRTSEQ, srtSeq);          

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
  private void getModuleInfo() throws SQLException {

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
                "PROFILING_DATA As PRFDTA, " +
                "ALLOW_RTVCLSRC As ALWRTVSRC, " +
                "USER_MODIFIED, " +
                "LIC_OPTIONS, " +
                "LICENSED_PROGRAM, " +
                "PTF_NUMBER, " +
                "APAR_ID, " +
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
            "PROGRAM_NAME = '" + this.targetKey.getObjectName() + "' " +
            "AND BOUND_MODULE = '" + this.targetKey.getObjectName() + "' " //TODO: Fix this
        )) {
      if (!rsMod.next()) {
        if(verbose) System.err.println("Could not find module '" + this.targetKey.asString());
        return;
      }

      if (verbose) System.out.println("Found module '" + this.targetKey.asString());

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
        String modCCSID = rsMod.getString("MODULE_CCSID").trim();

        // Add more mappings (e.g., DEFINE, INCDIR, PPGENOPT)
      
    }
  }

  private void getCmdInfo() throws SQLException {
    
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
                "COMMAND_NAME = '" + this.targetKey.getObjectName() + "' "
          )) {
      if (!rsCmdInfo.next()) {
        System.err.println(("Could not get command '" + this.targetKey.asString()));
        return;
      }

      if (verbose) System.out.println("Found command '" + this.targetKey.asString());
      
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

  //TODO: Change this view for QSYS2.SYSFILES and use it for crtsqlrpgle isntead
  private void getSqlInfo() throws SQLException{
    
  }

  private void getSqlRpgInfo()throws SQLException{
    try (Statement stmt = connection.createStatement();
        ResultSet rsSqlRpgInfo = stmt.executeQuery(
          "With " +
          "Libs (Libraries) As ( " +
              "SELECT DISTINCT(SCHEMA_NAME) FROM QSYS2.LIBRARY_LIST_INFO " + 
              "WHERE TYPE NOT IN ('SYSTEM','PRODUCT') AND SCHEMA_NAME NOT IN ('QGPL', 'GAMES400') " +
          ") " +
          "SELECT PROGRAM_LIBRARY, " + // programLibrary
              "PROGRAM_NAME, " + // programName
              "OBJECT_TYPE, " +   // typeOfProgram
              "COALESCE(TEXT_DESCRIPTION, '') As TEXT, " + // textDescription
              "PROGRAM_OWNER, " + // owner
              "PROGRAM_ATTRIBUTE, " + // attribute
              "USER_PROFILE As USRPRF, " +
              "USE_ADOPTED_AUTHORITY, " +
              "COALESCE(TARGET_RELEASE, '') As TGTRLS, " +
              // Module related data
              "COPYRIGHTS, " +
              "COPYRIGHT_STRINGS, " +
              // Source file related data
              "(TRIM(SOURCE_FILE_LIBRARY) || '/' || TRIM(SOURCE_FILE)) As SRCFILE, " +
              "SOURCE_FILE_MEMBER As SRCMBR, " +
              //-- Sql related info
              "SQL_RELATIONAL_DATABASE, " +
              "COALESCE(SQL_COMMITMENT_CONTROL, '') As COMMIT, " +
              "COALESCE(SQL_NAMING, '') As NAMING, " +
              "SQL_DATE_FORMAT As DATFMT, " +
              "SQL_DATE_SEPARATOR As DATSEP, " +
              "SQL_TIME_FORMAT As TIMFMT, " +
              "SQL_TIME_SEPARATOR As TIMSEP, " +
              "(TRIM(SQL_SORT_SEQUENCE_LIBRARY) || '/' || TRIM(SQL_SORT_SEQUENCE)) As SRTSEQ, " +
              "SQL_LANGUAGE_ID As LANGID, " +
              "SQL_DEFAULT_SCHEMA, " +
              "SQL_PATH, " +
              "SQL_DYNAMIC_USER_PROFILE As DYNUSRPRF, " +
              "SQL_ALLOW_COPY_DATA As ALWCPYDTA, " +
              "SQL_CLOSE_SQL_CURSOR As CLOSQLCSR, " +
              "SQL_DELAY_PREPARE As DLYPRP, " +
              "SQL_ALLOW_BLOCK As ALWBLK " +
            "FROM QSYS2.PROGRAM_INFO " +
            "INNER JOIN Libs " +
            "ON (PROGRAM_LIBRARY = Libs.Libraries) " +
            "WHERE " + 
                "PROGRAM_NAME = '" + this.targetKey.getObjectName() + "' " +
                "AND OBJECT_TYPE = '" + this.targetKey.getObjectType() + "' "
          )) {
      if (!rsSqlRpgInfo.next()) {
        System.err.println(("Could not get object '" + this.targetKey.asString() ));
        return;
      }

      if (verbose) System.out.println("Found object '" + this.targetKey.asString());

      this.targetKey.put(ParamCmd.TEXT, rsSqlRpgInfo.getString("TEXT").trim()); 
      this.targetKey.put(ParamCmd.USRPRF, rsSqlRpgInfo.getString("USRPRF").trim()); 

      String tgtrls = rsSqlRpgInfo.getString("TGTRLS").trim();
      if(!tgtrls.isEmpty()) this.targetKey.put(ParamCmd.TGTRLS, tgtrls); 

      //String srcfile = rsSqlRpgInfo.getString("SRCFILE").trim();
      //if(!srcfile.isEmpty()) this.targetKey.put(ParamCmd.SRCFILE, srcfile); 
      //String srcmbr = rsSqlRpgInfo.getString("SRCMBR").trim();
      //if(!srcmbr.isEmpty()) this.targetKey.put(ParamCmd.SRCMBR, srcmbr); 

      //TODO: The service does not returns data for these filed. They need to fix that.

      //this.targetKey.put(ParamCmd.COMMIT, ValCmd.fromString(rsSqlRpgInfo.getString("COMMIT"))); 
      //this.targetKey.put(ParamCmd.NAMING, ValCmd.fromString(rsSqlRpgInfo.getString("NAMING"))); 

      //this.targetKey.put(ParamCmd.DATFMT, ValCmd.fromString(rsSqlRpgInfo.getString("DATFMT"))); 
      //this.targetKey.put(ParamCmd.DATSEP, ValCmd.fromString(rsSqlRpgInfo.getString("DATSEP"))); 

      //this.targetKey.put(ParamCmd.TIMFMT, ValCmd.fromString(rsSqlRpgInfo.getString("TIMFMT"))); 
      //this.targetKey.put(ParamCmd.TIMSEP, ValCmd.fromString(rsSqlRpgInfo.getString("TIMSEP"))); 

      //this.targetKey.put(ParamCmd.SRTSEQ, rsSqlRpgInfo.getString("SRTSEQ").trim()); 
      //this.targetKey.put(ParamCmd.LANGID, rsSqlRpgInfo.getString("LANGID").trim()); 

      //this.targetKey.put(ParamCmd.DYNUSRPRF, ValCmd.fromString(rsSqlRpgInfo.getString("DYNUSRPRF"))); 
      //this.targetKey.put(ParamCmd.ALWCPYDTA, ValCmd.fromString(rsSqlRpgInfo.getString("ALWCPYDTA"))); 
      //this.targetKey.put(ParamCmd.CLOSQLCSR, ValCmd.fromString(rsSqlRpgInfo.getString("CLOSQLCSR"))); 
      //this.targetKey.put(ParamCmd.DLYPRP, ValCmd.fromString(rsSqlRpgInfo.getString("DLYPRP"))); 
      //this.targetKey.put(ParamCmd.ALWBLK, ValCmd.fromString(rsSqlRpgInfo.getString("ALWBLK"))); 
    }
  }

  private void getDdsInfo(){

  }

  private void getSrvpgmInfo() {
    
  }

}