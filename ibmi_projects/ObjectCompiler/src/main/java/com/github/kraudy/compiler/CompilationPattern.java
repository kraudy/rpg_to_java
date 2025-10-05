package com.github.kraudy.compiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;


public class CompilationPattern {
  private CompCmd compilationCommand;
  private ObjectDescription.ObjectType objectType;
  private ObjectDescription.SourceType sourceType;
  private Map<ParamCmd, String> ParamCmdSequence;
  Utilities.ParsedKey targetKey;
  Supplier<String> cmdSupplier; // Resolver map for command builders (functions that build command strings based on spec)
  List<ParamCmd> compilationPattern;

  public enum CompCmd { 
    CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY;

    //TODO: This could be done with a MAP. or a non static method.
    public static String compilationSourceName(CompCmd cmd){
      switch (cmd) {
        case CRTBNDRPG:
        case CRTBNDCL:
        case CRTRPGPGM:
        case CRTCLPGM:
          return ValCmd.PGM.toString(); //"*PGM";
        case CRTRPGMOD:
        case CRTCLMOD:
          return ValCmd.MODULE.toString();
        case CRTSQLRPGI:
          return ValCmd.OBJ.toString();
        // TODO: Add SQL Types
        //case RUNSQLSTM:
        default:
          throw new IllegalArgumentException("Could not found compilation source name and no default value defined for Cmd: " + cmd.name());
      }
    }
  }

  public enum ParamCmd { 
    PGM, MODULE, OBJ, OBJTYPE, OUTPUT, OUTMBR, SRVPGM, BNDSRVPGM, LIBL, SRCFILE, SRCMBR, ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF,
    OPTION, TGTRLS, SORTSEQ_LIB, SRCSTMF,
    // NEW: Added for (RPG/CL specific)
    GENLVL, DBGVIEW, DBGENCKEY, OPTIMIZE, INDENT, CVTOPT, SRTSEQ, LANGID, REPLACE, USRPRF, AUT, TRUNCNBR, FIXNBR, ALWNULL, DEFINE, ENBPFRCOL, PRFDTA, 
    LICOPT, INCDIR, PGMINFO, INFOSTMF, PPGENOPT, PPSRCFILE, PPSRCMBR, PPSRCSTMF, REQPREXP, PPMINOUTLN,
    GENOPT, SAAFLAG, PRTFILE, PHSTRC, ITDUMP, SNPDUMP, CODELIST, IGNDECERR, LOG, ALWRTVSRC, INCFILE, STGMDL,

    // SQLRPGLEI
    RDB, RPGPPOPT, ALWCPYDTA, CLOSQLCSR, ALWBLK, DLYPRP, CONACC, DATFMT, DATSEP, TIMFMT, TIMSEP, RDBCNNMTH, DFTRDBCOL, DYNDFTCOL, SQLPKG, SQLPATH, SQLCURR,
    FLAGSTD, DYNUSRPRF, CVTCCSID, TOSRCFILE, DECRESULT, DECFLTRND, COMPILEOPT,
    
    // RUNSQLSTM
    NAMING, ERRLVL, MARGINS, DECMPT, PROCESS, SECLVLTXT, SQLCURRULE, SYSTIME,

    // CRTSRVPGM
    EXPORT, DETAIL, ALWUPD, ALWLIBUPD, ALWRINZ, ARGOPT, IPA, IPACTLFILE
    ;

    public static ParamCmd fromString(String value) {
      try {
        return ParamCmd.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get compilation command param from string: '" + value + "'");
      }
    } 

    //TODO: Maybe i could add a method here that does the same as getParamString()
    
    /* Validates if the option for a given param is valid */
    public static String paramValue(ParamCmd paramCmd, ValCmd valCmd){
      try {
        switch (paramCmd){
          case OUTPUT:
            if (!EnumSet.of(ValCmd.OUTFILE).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case OUTMBR:
            if (!EnumSet.of(ValCmd.FIRST, ValCmd.REPLACE).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case OBJTYPE:
            if (!EnumSet.of(ValCmd.PGM, ValCmd.SRVPGM).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case MODULE:
            if (!EnumSet.of(ValCmd.PGM).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case BNDSRVPGM:
            if (!EnumSet.of(ValCmd.SRVPGM).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case LIBL:
            if (!EnumSet.of(ValCmd.LIBL).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case SRCFILE:
            if (!EnumSet.of(ValCmd.PGM, ValCmd.FILE, ValCmd.LIBL).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case PGM:
            if (!EnumSet.of(ValCmd.CURLIB, ValCmd.LIBL).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case OBJ:
            if (!EnumSet.of(ValCmd.LIBL, ValCmd.FILE, ValCmd.DTAARA).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case GENLVL:
            if (!EnumSet.of(ValCmd.NONE).contains(valCmd) && !(valCmd.name().matches("\\d+"))) throw new IllegalArgumentException();
            break;
          case DBGVIEW:
            if (!EnumSet.of(ValCmd.ALL, ValCmd.NONE, ValCmd.STMT, ValCmd.SOURCE, ValCmd.LIST).contains(valCmd)) throw new IllegalArgumentException();  // Add more as needed
            break;
          case OPTIMIZE:
            if (!EnumSet.of(ValCmd.NONE).contains(valCmd) && !(valCmd.name().matches("\\d+"))) throw new IllegalArgumentException();
            break;
          case SRTSEQ:
            if (!EnumSet.of(ValCmd.HEX, ValCmd.NONE).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case LANGID:
            if (!EnumSet.of(ValCmd.JOBRUN).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case REPLACE:
          case TRUNCNBR:
          case ALWNULL:
          case FIXNBR:
          case REQPREXP:
          case SAAFLAG:
          case PHSTRC:
          case IGNDECERR:
          case LOG:
          case ALWRTVSRC:
            if (!EnumSet.of(ValCmd.YES, ValCmd.NO).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case USRPRF:
            if (!EnumSet.of(ValCmd.USER).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case AUT:
            if (!EnumSet.of(ValCmd.LIBCRTAUT).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case PRFDTA:
            if (!EnumSet.of(ValCmd.NOCOL).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case ENBPFRCOL:
            if (!EnumSet.of(ValCmd.PEP).contains(valCmd)) throw new IllegalArgumentException();
            break;
          case STGMDL:
            if (!EnumSet.of(ValCmd.SNGLVL).contains(valCmd)) throw new IllegalArgumentException();
            break;
            // Add cases for others as needed (e.g., DEFINE(*NONE), etc.)
          default:
            throw new IllegalArgumentException();  
        }
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Option: '" + valCmd.name() + "'' not valid for param '" + paramCmd.name() + "'");
      }     
      return valCmd.toString();
    }
  }

  public enum ValCmd { 
    FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, MODULE, OBJ, SRVPGM, CURLIB, ALL, CURRENT,
    NONE, BASIC, FULL, LSTDBG, JOB, EVENTF,

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

  /* Maps source type to its compilation command */
  public static final Map<ObjectDescription.SourceType, Map<ObjectDescription.ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(ObjectDescription.SourceType.class);

  static{
    /*
     * Populate mapping from (ObjectDescription.SourceType, ObjectDescription.ObjectType) to CompCmd
    */

    Map<ObjectDescription.ObjectType, CompCmd> bndMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    bndMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.BND, bndMap);

    Map<ObjectDescription.ObjectType, CompCmd> rpgMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    rpgMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTRPGPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.RPG, rpgMap);

    // TODO: Maybe add another sourc type BND to map SRVPGM to CRTSRVPGM
    Map<ObjectDescription.ObjectType, CompCmd> rpgLeMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    rpgLeMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTBNDRPG);
    //rpgLeMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.RPGLE, rpgLeMap);

    Map<ObjectDescription.ObjectType, CompCmd> sqlRpgLeMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    sqlRpgLeMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTSQLRPGI);
    //sqlRpgLeMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.SQLRPGLE, sqlRpgLeMap);

    Map<ObjectDescription.ObjectType, CompCmd> clpMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    clpMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTCLPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.CLP, clpMap);

    Map<ObjectDescription.ObjectType, CompCmd> clleMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    clleMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTCLMOD);
    clleMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTBNDCL);
    //clleMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.CLLE, clleMap);

    Map<ObjectDescription.ObjectType, CompCmd> sqlMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    sqlMap.put(ObjectDescription.ObjectType.TABLE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.LF, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.VIEW, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.ALIAS, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.PROCEDURE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.FUNCTION, CompCmd.RUNSQLSTM);
    typeToCmdMap.put(ObjectDescription.SourceType.SQL, sqlMap);

  }  

  /* ILE Patterns */
  
  // CRTSRVPGM
  public static final List<ParamCmd> SrvpgmPattern = Arrays.asList(
    ParamCmd.SRVPGM,
    ParamCmd.MODULE,
    ParamCmd.EXPORT,  
    ParamCmd.SRCFILE, 
    ParamCmd.SRCMBR,  
    ParamCmd.SRCSTMF, 
    ParamCmd.TEXT,    
    ParamCmd.BNDSRVPGM,
    ParamCmd.BNDDIR,
    ParamCmd.ACTGRP,
    ParamCmd.OPTION,
    ParamCmd.DETAIL,
    ParamCmd.ALWUPD,    
    ParamCmd.ALWLIBUPD, 
    ParamCmd.USRPRF,    
    ParamCmd.REPLACE,   
    ParamCmd.AUT,       
    ParamCmd.TGTRLS,    
    ParamCmd.ALWRINZ,   
    ParamCmd.STGMDL,    
    ParamCmd.ARGOPT,    
    ParamCmd.IPA,       
    ParamCmd.IPACTLFILE

  );

  // CRTBNDRPG
  public static final List<ParamCmd> ileRpgPgmPattern = Arrays.asList(
    ParamCmd.PGM,       // Program
    ParamCmd.SRCFILE,   // Source file
    ParamCmd.SRCMBR,    // Source member
    ParamCmd.SRCSTMF,   // Source stream file

    ParamCmd.GENLVL,    // Generation severity level
    ParamCmd.TEXT,      // Text 'description'

    ParamCmd.DFTACTGRP,
    ParamCmd.ACTGRP,
    ParamCmd.STGMDL,    //TODO: Maybe this needs to be conditional

    ParamCmd.BNDDIR,    // Binding directory

    ParamCmd.OPTION,    // Compiler options

    ParamCmd.DBGVIEW,   // Debugging views
    ParamCmd.DBGENCKEY, // Debug encryption key
    ParamCmd.OUTPUT,    // Output
    ParamCmd.OPTIMIZE,  // Optimization level
    ParamCmd.INDENT,    // Source listing indentation
    ParamCmd.CVTOPT,    // Type conversion options

    ParamCmd.SRTSEQ,    // Sort sequence
    ParamCmd.LANGID,    // Sort sequence
    ParamCmd.REPLACE,
    ParamCmd.USRPRF,
    ParamCmd.AUT,
    ParamCmd.TRUNCNBR,
    ParamCmd.FIXNBR,
    ParamCmd.TGTRLS,
    ParamCmd.ALWNULL,
    ParamCmd.DEFINE,
    ParamCmd.ENBPFRCOL,

    ParamCmd.PRFDTA,
    ParamCmd.LICOPT,
    ParamCmd.INCDIR,
    ParamCmd.PGMINFO,

    ParamCmd.INFOSTMF,
    ParamCmd.PPGENOPT,
    ParamCmd.PPSRCFILE,
    ParamCmd.PPSRCMBR,
    ParamCmd.PPSRCSTMF,
    ParamCmd.TGTCCSID,
    ParamCmd.REQPREXP,
    ParamCmd.PPMINOUTLN

  );

  // CRTBNDCL
  public static final List<ParamCmd> ileClPgmPattern = Arrays.asList(
    ParamCmd.PGM,       // Program
    ParamCmd.SRCFILE,   // Source file
    ParamCmd.SRCMBR,    // Source member
    ParamCmd.SRCSTMF,   // Source stream file
    ParamCmd.TEXT,      // Text 'description'

    ParamCmd.DFTACTGRP,
    ParamCmd.ACTGRP,
    ParamCmd.STGMDL,
    ParamCmd.OUTPUT,

    ParamCmd.OPTION,
    ParamCmd.USRPRF,   
    ParamCmd.LOG,      
    ParamCmd.ALWRTVSRC,
    ParamCmd.REPLACE,  
    ParamCmd.TGTRLS,   
    ParamCmd.AUT,      
    ParamCmd.SRTSEQ,           
    ParamCmd.LANGID,   
    ParamCmd.OPTIMIZE, 
    ParamCmd.DBGVIEW,  
    ParamCmd.DBGENCKEY,
    ParamCmd.ENBPFRCOL,

    ParamCmd.INCFILE,
    ParamCmd.INCDIR,
    ParamCmd.TGTCCSID

  );

  // Modules 

  // CRTRPGMOD
  public static final List<ParamCmd> RpgModulePattern = Arrays.asList(
    ParamCmd.MODULE, 
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR, 
    ParamCmd.SRCSTMF,
    ParamCmd.GENLVL, 
    ParamCmd.TEXT,   
    ParamCmd.OPTION,   
    ParamCmd.DBGVIEW,
    
    ParamCmd.DBGENCKEY,  
    ParamCmd.OUTPUT,     
    ParamCmd.OPTIMIZE,   
    ParamCmd.INDENT,     
    ParamCmd.CVTOPT,     
    ParamCmd.SRTSEQ,     
    ParamCmd.LANGID,     
    ParamCmd.REPLACE,    
    ParamCmd.AUT,        
    ParamCmd.TRUNCNBR,   
    ParamCmd.FIXNBR,     
    ParamCmd.TGTRLS,     
    ParamCmd.ALWNULL,    

    ParamCmd.DEFINE,            
    ParamCmd.ENBPFRCOL,
    ParamCmd.PRFDTA,   
    ParamCmd.STGMDL,   
    ParamCmd.BNDDIR,   

    ParamCmd.LICOPT,   
    ParamCmd.INCDIR,   
    ParamCmd.PGMINFO,
    
    ParamCmd.INFOSTMF,   
    ParamCmd.PPGENOPT,   
    ParamCmd.PPSRCFILE,  
    ParamCmd.PPSRCMBR,   
    ParamCmd.PPSRCSTMF,  
    ParamCmd.TGTCCSID,   
    ParamCmd.REQPREXP,   
    ParamCmd.PPMINOUTLN
  );

  // CRTCLMOD
  public static final List<ParamCmd> ClleModulePattern = Arrays.asList(
    ParamCmd.MODULE, 
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR, 
    ParamCmd.SRCSTMF,
    ParamCmd.TEXT,   
    ParamCmd.OUTPUT, 
    ParamCmd.OPTION,   

    ParamCmd.LOG,
    ParamCmd.ALWRTVSRC, 
    ParamCmd.REPLACE,   
    ParamCmd.TGTRLS,    
    ParamCmd.AUT,       
    ParamCmd.SRTSEQ,    
    ParamCmd.LANGID,    
    ParamCmd.OPTIMIZE,  
    ParamCmd.DBGVIEW,   
    ParamCmd.DBGENCKEY, 
    ParamCmd.ENBPFRCOL, 
    ParamCmd.INCFILE,  
    ParamCmd.INCDIR,
    ParamCmd.TGTCCSID

  );

  // Sql and RPG

  // CRTSQLRPGI
  public static final List<ParamCmd> SqlRpgPgmPattern = Arrays.asList(
    ParamCmd.OBJ, 
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR, 
    ParamCmd.SRCSTMF,

    ParamCmd.COMMIT,  
    ParamCmd.RDB,     
    ParamCmd.OBJTYPE, 
    ParamCmd.OUTPUT,  
    ParamCmd.TEXT,   
    ParamCmd.OPTION,    
    ParamCmd.RPGPPOPT,  
    ParamCmd.TGTRLS,    
    ParamCmd.INCFILE,   
    ParamCmd.INCDIR,    
    ParamCmd.ALWCPYDTA, 
    ParamCmd.CLOSQLCSR, 
    ParamCmd.ALWBLK,    
    ParamCmd.DLYPRP,    
    ParamCmd.CONACC,       
    ParamCmd.GENLVL,    
    ParamCmd.DATFMT,    
    ParamCmd.DATSEP,    
    ParamCmd.TIMFMT,    
    ParamCmd.TIMSEP,    
    ParamCmd.REPLACE,   
    ParamCmd.RDBCNNMTH, 
    ParamCmd.DFTRDBCOL, 
    ParamCmd.DYNDFTCOL, 
    ParamCmd.SQLPKG, 
    ParamCmd.SQLPATH,
    ParamCmd.SQLCURR,
    ParamCmd.SAAFLAG,
    ParamCmd.FLAGSTD,
    ParamCmd.PRTFILE,  
    ParamCmd.DBGVIEW,  
    ParamCmd.DBGENCKEY,
    ParamCmd.USRPRF,   
    ParamCmd.DYNUSRPRF,
    ParamCmd.SRTSEQ,   
    ParamCmd.LANGID,   
    ParamCmd.CVTCCSID, 
    ParamCmd.TOSRCFILE,
    ParamCmd.DECRESULT,
    ParamCmd.DECFLTRND, 
    ParamCmd.COMPILEOPT

  );

  /* OPM */

  // CRTRPGPGM
  public static final List<ParamCmd> opmRpgPgmPattern = Arrays.asList(
    ParamCmd.PGM,       // Program
    ParamCmd.SRCFILE,   // Source file
    ParamCmd.SRCMBR,    // Source member
    ParamCmd.GENLVL,   
    ParamCmd.TEXT,   

    ParamCmd.OPTION,   
    ParamCmd.GENOPT,   
    ParamCmd.INDENT,   

    ParamCmd.CVTOPT,  
    ParamCmd.SRTSEQ,  
    ParamCmd.LANGID,  
    ParamCmd.SAAFLAG, 
    ParamCmd.PRTFILE, 
    ParamCmd.REPLACE, 
    ParamCmd.TGTRLS,  
    ParamCmd.USRPRF,  
    ParamCmd.AUT,     
    ParamCmd.PHSTRC,  
    ParamCmd.ITDUMP,  

    ParamCmd.SNPDUMP,   
    ParamCmd.CODELIST,  
    ParamCmd.IGNDECERR, 
    ParamCmd.ALWNULL  

  );

  // CRTCLPGM
  public static final List<ParamCmd> opmClPgmPattern = Arrays.asList(
    ParamCmd.PGM,       // Program
    ParamCmd.SRCFILE,   // Source file
    ParamCmd.SRCMBR,    // Source member
    ParamCmd.TEXT,   

    ParamCmd.OUTPUT,
    ParamCmd.OPTION,
    ParamCmd.GENOPT,
    ParamCmd.USRPRF,

    ParamCmd.LOG,      
    ParamCmd.ALWRTVSRC,
    ParamCmd.REPLACE,  
    ParamCmd.TGTRLS,   
    ParamCmd.AUT,      
    ParamCmd.SRTSEQ,   
    ParamCmd.LANGID,   
    ParamCmd.INCFILE  

  );


  /* Sql */
  // RUNSQLSTM
  public static final List<ParamCmd> SqlPattern = Arrays.asList(
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR, 
    ParamCmd.SRCSTMF,
    ParamCmd.COMMIT, 
    ParamCmd.NAMING,      
    ParamCmd.ERRLVL, 
    ParamCmd.DATFMT, 
    ParamCmd.DATSEP, 
    ParamCmd.TIMFMT, 
    ParamCmd.TIMSEP,
    ParamCmd.MARGINS,
    ParamCmd.DFTRDBCOL,
    ParamCmd.SAAFLAG,  
    ParamCmd.FLAGSTD,  
    ParamCmd.DECMPT,   
    ParamCmd.SRTSEQ,   
    ParamCmd.LANGID,   
    ParamCmd.OPTION,   
    ParamCmd.PRTFILE,  
    ParamCmd.PROCESS,  
    ParamCmd.SECLVLTXT,
    ParamCmd.ALWCPYDTA,
    ParamCmd.ALWBLK,    
    ParamCmd.SQLCURRULE,
    ParamCmd.DECRESULT, 
    ParamCmd.CONACC,    
    ParamCmd.SYSTIME,   
    ParamCmd.OUTPUT,    
    ParamCmd.TGTRLS,    
    ParamCmd.DBGVIEW,   
    ParamCmd.CLOSQLCSR, 
    ParamCmd.DLYPRP,    
    ParamCmd.USRPRF,    
    ParamCmd.DYNUSRPRF

  );

  /* Maps compilation command to its pattern */
  public static final Map<CompCmd, List<ParamCmd>> cmdToPatternMap = new EnumMap<>(CompCmd.class);

  static{
    /* ILE */
    cmdToPatternMap.put(CompCmd.CRTSRVPGM, SrvpgmPattern);
    cmdToPatternMap.put(CompCmd.CRTBNDRPG, ileRpgPgmPattern);
    cmdToPatternMap.put(CompCmd.CRTBNDCL, ileClPgmPattern);
    cmdToPatternMap.put(CompCmd.CRTRPGMOD, RpgModulePattern);
    cmdToPatternMap.put(CompCmd.CRTCLMOD, ClleModulePattern);
    cmdToPatternMap.put(CompCmd.CRTSQLRPGI, SqlRpgPgmPattern);
    /* OPM */
    cmdToPatternMap.put(CompCmd.CRTRPGPGM, opmRpgPgmPattern);
    cmdToPatternMap.put(CompCmd.CRTCLPGM, opmClPgmPattern);
    /* SQL */
    cmdToPatternMap.put(CompCmd.RUNSQLSTM, SqlPattern);
  }

  public static final List<ParamCmd> reqPgmParams = Arrays.asList(
      ParamCmd.PGM,       // Required first
      ParamCmd.SRCFILE,
      ParamCmd.SRCMBR,
      ParamCmd.TEXT
    );

  public static final List<ParamCmd> ilePgmParams = Arrays.asList(
      // reqPgmParams
      ParamCmd.DFTACTGRP,
      ParamCmd.DBGVIEW,
      ParamCmd.OPTIMIZE
      // releasePgmParams
    );

  public static final List<ParamCmd> opmPgmParams = Arrays.asList(
      // reqPgmParams
      ParamCmd.OPTION,
      ParamCmd.GENOPT
      // releasePgmParams
    );            

  public static final List<ParamCmd> releasePgmParams = Arrays.asList(
        ParamCmd.USRPRF,
        ParamCmd.TGTRLS
    );          
    
  //TODO: Maybe these could be put in a MAP in the future along the suppliers
  public static final List<ParamCmd> ilePgmPattern;
  static {
      ilePgmPattern = new ArrayList<>(reqPgmParams);
      ilePgmPattern.addAll(ilePgmParams);
      ilePgmPattern.addAll(releasePgmParams);
  }
    
  public static final List<ParamCmd> opmPgmPattern;
  static {
      opmPgmPattern = new ArrayList<>(reqPgmParams);
      opmPgmPattern.add(ParamCmd.GENLVL); //TODO: Could be added to opmPgmParams
      opmPgmPattern.addAll(opmPgmParams);
      opmPgmPattern.addAll(releasePgmParams);
  }

  //public CompilationPattern(ObjectDescription odes){
  public CompilationPattern(ObjectDescription odes){

    this.objectType = odes.targetKey.objectType;
    //this.targetKey = odes.targetKey;
    this.sourceType = odes.getSourceType();

    /* Get optional params */
    //TODO: I think, only this is necessary.
    this.ParamCmdSequence = odes.getParamCmdSequence();

    /* Get compilation command */

    this.compilationCommand = typeToCmdMap.get(sourceType).get(objectType);
    //this.compilationPattern = cmdToPatternMap.get(this.compilationCommand); // new ArrayList<>(ileRpgPgmPattern)
    this.compilationPattern = new ArrayList<>(cmdToPatternMap.get(this.compilationCommand));
    /* Command builders */
    
    switch (compilationCommand){
      case CRTRPGMOD:
        if (ParamCmdSequence.containsKey(ParamCmd.SRCSTMF)) {
          this.compilationPattern.remove(ParamCmd.SRCFILE);
          this.compilationPattern.remove(ParamCmd.SRCMBR);
        }
      case CRTCLMOD:
        break;

      case CRTBNDRPG:
        if (!ParamCmdSequence.containsKey(ParamCmd.DFTACTGRP)) {
          this.compilationPattern.remove(ParamCmd.STGMDL);
        }
      case CRTBNDCL:
      case CRTCLPGM:
        break;
        
      case CRTRPGPGM:
        break;

      case CRTSQLRPGI:
        break;
      case CRTSRVPGM:
        if (ParamCmdSequence.containsKey(ParamCmd.EXPORT)) {
          this.compilationPattern.remove(ParamCmd.SRCFILE);
          this.compilationPattern.remove(ParamCmd.SRCMBR);
        }
        break;
      case RUNSQLSTM:
        break;
      default: throw new IllegalArgumentException("Compilation command builder not found");
    }
    //TODO: Add more builders for other commands
  }

  public CompCmd getCompilationCommand(){
    return this.compilationCommand;
  }

  public String buildCommand() {
    StringBuilder sb = new StringBuilder(); 

    for (ParamCmd param : compilationPattern) {
      sb.append(getParamString(param));
    }

    return compilationCommand.name() + sb.toString();
  }

  public  String getParamString(ParamCmd paramCmd){
    //TODO: Should i update the object desc?
    String val = ParamCmdSequence.getOrDefault(paramCmd, "");  // Retrieved or empty

    if (val.isEmpty()) return "";

    return " " + paramCmd.name() + "(" + val + ")";
  }

}
