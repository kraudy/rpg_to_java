package com.github.kraudy.compiler;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CompilationPattern {

  public enum SysCmd { 
    // Library commands
    CHGLIBL, CHGCURLIB, 
    // Dependency commands
    DSPPGMREF, DSPOBJD, DSPDBR 
  
  }

  public enum SourceType { 
    RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL, BND, DDS;

    public static SourceType fromString(String value) {
      try {
        return SourceType.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get source type from object attribute '" + value + "'");
      }
    } 

    public static String defaultSourcePf (SourceType sourceType, ObjectType objectType){
      switch (sourceType){
        case RPG:
          return DftSrc.QRPGSRC.name(); //TODO: Add .name()? and return string?
        case RPGLE:
          return DftSrc.QRPGLESRC.name();
        case SQLRPGLE:
          return DftSrc.QSQLRPGSRC.name();
        case BND:
          return DftSrc.QSRVSRC.name(); 
        case CLP:
        case CLLE:
          return DftSrc.QCLSRC.name();
        case DDS: //TODO: This need to be fixed for DSPF, PF and LF, maybe add objectType as param
          switch (objectType) {
            case DSPF:
              return DftSrc.QDSPFSRC.name();
            case PF:
              return DftSrc.QPFSRC.name();
            case LF:
              return DftSrc.QLFSRC.name();
          }
          
        case SQL:
          return DftSrc.QSQLSRC.name();
        default:
          throw new IllegalArgumentException("Could not get default sourcePf for '" + sourceType + "'");
      }
    }
  }

  public enum ObjectType { 
    PGM, SRVPGM, MODULE, TABLE, LF, INDEX, VIEW, ALIAS, PROCEDURE, FUNCTION, PF, DSPF;
    public String toParam(){
      return "*" + this.name();
    }
  } // Add more as needed

  public enum PostCmpCmd { CHGOBJD }

  public enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC, QSRVSRC, QDSPFSRC, QPFSRC, QLFSRC, QSQLRPGSRC, QSQLMODSRC }

  public enum CompCmd { 
    CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY, CRTPF, CRTCMD;
  }

  public enum ParamCmd { 
    PGM, MODULE, OBJ, OBJTYPE, OUTPUT, OUTMBR, SRVPGM, BNDSRVPGM, LIBL, SRCFILE, SRCMBR, ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF,
    OPTION, TGTRLS, SRCSTMF,
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
    EXPORT, DETAIL, ALWUPD, ALWLIBUPD, ALWRINZ, ARGOPT, IPA, IPACTLFILE,

    // CRTDSPF
    FILE, FLAG, DEV, MAXDEV, ENHDSP, RSTDSP, DFRWRT, CHRID, DECFMT, SFLENDTXT, WAITFILE, WAITRCD, DTAQ, SHARE, LVLCHK,

    // CRTPF
    RCDLEN, FILETYPE, MBR, SYSTEM, EXPDATE, MAXMBRS, ACCPTHSIZ, PAGESIZE, MAINT, RECOVER, FRCACCPTH, SIZE, ALLOCATE, CONTIG, UNIT, FRCRATIO,
    DLTPCT, REUSEDLT, CCSID, ALWDLT, NODGRP, PTNKEY,

    // CRTLF
    DTAMBRS, FMTSLR,

    // CHGLIBL, CURLIB
    CURLIB,

    // CRTPRTF
    DEVTYPE, LPI, CPI, FRONTMGN, BACKMGN, OVRFLW, FOLD, RPLUNPRT, ALIGN, CTLCHAR, CHLVAL, FIDELITY, PRTQLTY, FORMFEED, DRAWER, OUTBIN, FONT,     
    FNTCHRSET, CDEFNT, TBLREFCHR, PAGDFN, FORMDF, AFPCHARS, PAGRTT, MULTIUP, REDUCE, PRTTXT, JUSTIFY, DUPLEX, UOM, FRONTOVL, BACKOVL, CVTLINDTA, 
    IPDSPASTHR, USRRSCLIBL, CORNERSTPL, EDGESTITCH, SADLSTITCH, FNTRSL, SPOOL, OUTQ, FORMTYPE, COPIES, DAYS, PAGERANGE, MAXRCDS, FILESEP, SCHEDULE,
    HOLD, SAVE, OUTPTY, USRDTA, SPLFOWN, USRDFNOPT, USRDFNDTA, USRDFNOBJ, TOSTMF, WSCST,

    // CRTCMD
    CMD, REXSRCFILE, REXSRCMBR, REXCMDENV, REXEXITPGM, THDSAFE
    ;

    public static ParamCmd fromString(String value) {
      try {
        return ParamCmd.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get compilation command param from string: '" + value + "'");
      }
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

  /* Maps Source Type => Object Type => Compilation command */
  private static final Map<SourceType, Map<ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);

  static{

    /* Source type: BND */
    Map<ObjectType, CompCmd> bndMap = new EnumMap<>(ObjectType.class);
    bndMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.BND, bndMap);

    /* Source type: RPG */
    Map<ObjectType, CompCmd> rpgMap = new EnumMap<>(ObjectType.class);
    rpgMap.put(ObjectType.PGM, CompCmd.CRTRPGPGM);
    typeToCmdMap.put(SourceType.RPG, rpgMap);

    /* Source type: RPGLE */
    Map<ObjectType, CompCmd> rpgLeMap = new EnumMap<>(ObjectType.class);
    rpgLeMap.put(ObjectType.MODULE, CompCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectType.PGM, CompCmd.CRTBNDRPG);
    typeToCmdMap.put(SourceType.RPGLE, rpgLeMap);

    /* Source type: SQLRPGLE */
    Map<ObjectType, CompCmd> sqlRpgLeMap = new EnumMap<>(ObjectType.class);
    sqlRpgLeMap.put(ObjectType.MODULE, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.PGM, CompCmd.CRTSQLRPGI);
    typeToCmdMap.put(SourceType.SQLRPGLE, sqlRpgLeMap);

    /* Source type: CLP */
    Map<ObjectType, CompCmd> clpMap = new EnumMap<>(ObjectType.class);
    clpMap.put(ObjectType.PGM, CompCmd.CRTCLPGM);
    typeToCmdMap.put(SourceType.CLP, clpMap);

    /* Source type: CLLE */
    Map<ObjectType, CompCmd> clleMap = new EnumMap<>(ObjectType.class);
    clleMap.put(ObjectType.MODULE, CompCmd.CRTCLMOD);
    clleMap.put(ObjectType.PGM, CompCmd.CRTBNDCL);
    typeToCmdMap.put(SourceType.CLLE, clleMap);

    /* Source type: SQL */
    Map<ObjectType, CompCmd> sqlMap = new EnumMap<>(ObjectType.class);
    sqlMap.put(ObjectType.TABLE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.INDEX, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.VIEW, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.ALIAS, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.PROCEDURE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.FUNCTION, CompCmd.RUNSQLSTM);
    typeToCmdMap.put(SourceType.SQL, sqlMap);

    /* Source type: DDS */
    Map<ObjectType, CompCmd> ddsMap = new EnumMap<>(ObjectType.class);
    ddsMap.put(ObjectType.PF, CompCmd.CRTPF);
    ddsMap.put(ObjectType.DSPF, CompCmd.CRTDSPF);
    ddsMap.put(ObjectType.LF, CompCmd.CRTLF);
    typeToCmdMap.put(SourceType.DDS, ddsMap);
  }  

  /* System command patterns */
  public static final List<ParamCmd> ChgLibLPattern = Arrays.asList(
    ParamCmd.LIBL,
    ParamCmd.CURLIB // Add USRLIBL if needed and added to ParamCmd
  );

  public static final List<ParamCmd> ChgCurLibPattern = Arrays.asList(
    ParamCmd.CURLIB
  );

  
  /* Maps compilation command to its pattern */
  public static final Map<SysCmd, List<ParamCmd>> SysCmdToPatternMap = new EnumMap<>(SysCmd.class);
  static{
    /* Libraries */
    SysCmdToPatternMap.put(SysCmd.CHGLIBL, ChgLibLPattern);
    SysCmdToPatternMap.put(SysCmd.CHGCURLIB, ChgCurLibPattern);
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

  /* DDS Files */

  // CRTDSPF
  public static final List<ParamCmd> ddsDspfPattern = Arrays.asList(
    ParamCmd.FILE,    
    ParamCmd.SRCFILE, 
    ParamCmd.SRCMBR,  
    ParamCmd.GENLVL,  
    ParamCmd.FLAG,    
    ParamCmd.DEV,     
    ParamCmd.TEXT,
    ParamCmd.OPTION,
    ParamCmd.MAXDEV, 
    ParamCmd.ENHDSP, 
    ParamCmd.RSTDSP, 
    ParamCmd.DFRWRT, 
    ParamCmd.CHRID,  
    ParamCmd.DECFMT,    
    ParamCmd.SFLENDTXT, 
    ParamCmd.WAITFILE,  
    ParamCmd.WAITRCD,   
    ParamCmd.DTAQ,      
    ParamCmd.SHARE, 
    ParamCmd.SRTSEQ,
    ParamCmd.LANGID,  
    ParamCmd.LVLCHK,  
    ParamCmd.AUT,     
    ParamCmd.REPLACE
  );

  // CRTPF
  public static final List<ParamCmd> ddsPfPattern = Arrays.asList(
    ParamCmd.FILE,
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR,  
    ParamCmd.RCDLEN,  
    ParamCmd.GENLVL,  
    ParamCmd.FLAG,    
    ParamCmd.FILETYPE,
    ParamCmd.MBR,     
    ParamCmd.TEXT,    
    ParamCmd.OPTION,
    ParamCmd.SYSTEM,   
    ParamCmd.EXPDATE,  
    ParamCmd.MAXMBRS,  
    ParamCmd.ACCPTHSIZ,
    ParamCmd.PAGESIZE, 
    ParamCmd.MAINT,    
    ParamCmd.RECOVER,  
    ParamCmd.FRCACCPTH,
    ParamCmd.SIZE,
    ParamCmd.ALLOCATE,
    ParamCmd.CONTIG,  
    ParamCmd.UNIT,    
    ParamCmd.FRCRATIO,
    ParamCmd.WAITFILE,
    ParamCmd.WAITRCD, 
    ParamCmd.SHARE,   
    ParamCmd.DLTPCT,  
    ParamCmd.REUSEDLT,
    ParamCmd.SRTSEQ,       
    ParamCmd.LANGID,  
    ParamCmd.CCSID, 
    ParamCmd.ALWUPD,
    ParamCmd.ALWDLT,
    ParamCmd.LVLCHK,
    ParamCmd.NODGRP,
    ParamCmd.PTNKEY,
    ParamCmd.AUT
  );

  // CRTLF
  public static final List<ParamCmd> ddsLfPattern = Arrays.asList(
    ParamCmd.FILE,
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR,  
    ParamCmd.GENLVL,  
    ParamCmd.FLAG,    
    ParamCmd.FILETYPE,
    ParamCmd.MBR,     
    ParamCmd.DTAMBRS, 
    ParamCmd.TEXT,
    ParamCmd.OPTION,
    ParamCmd.SYSTEM,    
    ParamCmd.MAXMBRS,   
    ParamCmd.ACCPTHSIZ, 
    ParamCmd.PAGESIZE,  
    ParamCmd.MAINT,     
    ParamCmd.RECOVER,   
    ParamCmd.FRCACCPTH, 
    ParamCmd.UNIT,      
    ParamCmd.FMTSLR,
    ParamCmd.FRCRATIO, 
    ParamCmd.WAITFILE, 
    ParamCmd.WAITRCD,  
    ParamCmd.SHARE,    
    ParamCmd.SRTSEQ,   
    ParamCmd.LANGID,   
    ParamCmd.LVLCHK,   
    ParamCmd.AUT 
  );

  // CRTPRTF
  public static final List<ParamCmd> ddsPrtfPattern = Arrays.asList(
    ParamCmd.FILE,   
    ParamCmd.SRCFILE,
    ParamCmd.SRCMBR, 
    ParamCmd.GENLVL, 
    ParamCmd.FLAG,   
    ParamCmd.DEV,    
    ParamCmd.DEVTYPE,
    ParamCmd.TEXT,   
    ParamCmd.OPTION,
    ParamCmd.PAGESIZE,
    ParamCmd.LPI,     
    ParamCmd.CPI,     
    ParamCmd.FRONTMGN,
    ParamCmd.BACKMGN,
    ParamCmd.OVRFLW,  
    ParamCmd.FOLD,    
    ParamCmd.RPLUNPRT,
    ParamCmd.ALIGN,   
    ParamCmd.CTLCHAR, 
    ParamCmd.CHLVAL,  
    ParamCmd.FIDELITY,
    ParamCmd.PRTQLTY,  
    ParamCmd.FORMFEED, 
    ParamCmd.DRAWER,   
    ParamCmd.OUTBIN,   
    ParamCmd.FONT,     
    ParamCmd.CHRID,    
    ParamCmd.DECFMT,   
    ParamCmd.FNTCHRSET,
    ParamCmd.CDEFNT,
    ParamCmd.TBLREFCHR,
    ParamCmd.PAGDFN,   
    ParamCmd.FORMDF,   
    ParamCmd.AFPCHARS,
    ParamCmd.PAGRTT,   
    ParamCmd.MULTIUP,  
    ParamCmd.REDUCE,   
    ParamCmd.PRTTXT,   
    ParamCmd.JUSTIFY,  
    ParamCmd.DUPLEX,   
    ParamCmd.UOM,      
    ParamCmd.FRONTOVL, 
    ParamCmd.BACKOVL,
    ParamCmd.CVTLINDTA, 
    ParamCmd.IPDSPASTHR,
    ParamCmd.USRRSCLIBL,
    ParamCmd.CORNERSTPL,
    ParamCmd.EDGESTITCH,
    ParamCmd.SADLSTITCH,
    ParamCmd.FNTRSL,
    ParamCmd.DFRWRT,
    ParamCmd.SPOOL, 
    ParamCmd.OUTQ,  
    ParamCmd.FORMTYPE, 
    ParamCmd.COPIES,   
    ParamCmd.EXPDATE,  
    ParamCmd.DAYS,     
    ParamCmd.PAGERANGE,
    ParamCmd.MAXRCDS, 
    ParamCmd.FILESEP, 
    ParamCmd.SCHEDULE,
    ParamCmd.HOLD,    
    ParamCmd.SAVE,    
    ParamCmd.OUTPTY,  
    ParamCmd.USRDTA,  
    ParamCmd.SPLFOWN, 
    ParamCmd.USRDFNOPT,
    ParamCmd.USRDFNDTA,
    ParamCmd.USRDFNOBJ,
    ParamCmd.TOSTMF,   
    ParamCmd.WSCST,    
    ParamCmd.WAITFILE, 
    ParamCmd.SHARE,   
    ParamCmd.LVLCHK,   
    ParamCmd.AUT,      
    ParamCmd.REPLACE
  );

  // CRTCMD
  public static final List<ParamCmd> CmdPattern = Arrays.asList(
    ParamCmd.CMD,
    ParamCmd.PGM,        
    ParamCmd.SRCFILE,    
    ParamCmd.SRCMBR,     
    ParamCmd.SRCSTMF,    
    ParamCmd.REXSRCFILE, 
    ParamCmd.REXSRCMBR,  
    ParamCmd.REXCMDENV,  
    ParamCmd.REXEXITPGM,
    ParamCmd.THDSAFE
  );

  // CRTBNDDIR
  public static final List<ParamCmd> BndDirPattern = Arrays.asList(
    ParamCmd.BNDDIR,
    ParamCmd.AUT,   
    ParamCmd.TEXT
  );

  // CRTMNU

  //TODO: Add config file support like YAML. This will allow specific patterns to be provided.
  // So, you would try to load the file with the param patter or use the default one.

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
    /* DDS */
    cmdToPatternMap.put(CompCmd.CRTDSPF, ddsDspfPattern);
    cmdToPatternMap.put(CompCmd.CRTPF, ddsPfPattern);
    cmdToPatternMap.put(CompCmd.CRTLF, ddsLfPattern);

    cmdToPatternMap.put(CompCmd.CRTPRTF, ddsPrtfPattern);
    /* CMD */
    cmdToPatternMap.put(CompCmd.CRTCMD, CmdPattern);
  }     

  public static CompCmd getCompilationCommand(SourceType sourceType, ObjectType objectType){
    return typeToCmdMap.get(sourceType).get(objectType);
  }

}
