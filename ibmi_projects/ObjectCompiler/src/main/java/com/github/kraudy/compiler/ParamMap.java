package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import com.github.kraudy.compiler.ObjectDescription.SysCmd;
import com.github.kraudy.compiler.ObjectDescription.ObjectType;
import com.github.kraudy.compiler.ObjectDescription.SourceType;

public class ParamMap {
    //TODO: Add another map like 
    // The problem is that it needs to be able to store other commands besides the compilation ones.
    // I would like to just send the compilation cmd to this thing and let it form the string and compile it.
    // But i need a way to deal with the general Enum of SysCmd and CompCmd, maybe with a SET or a MAP using .contains() to know if it
    // is a compilation command, maybe Overriding the PUT method
    //TODO: (if command instanceof CompCmd) could me useful
    //private Map<Object, Map<ParamCmd, String>> GeneralCmdMap = new EnumMap<>(Object.class);
    private Map<SysCmd, Map<ParamCmd, String>> SysCmdMap = new EnumMap<>(SysCmd.class);
    private Map<SysCmd, Map<ParamCmd, String>> SysCmdChanges = new EnumMap<>(SysCmd.class);

    public Map<CompCmd, Map<ParamCmd, String>> CompCmdMap = new EnumMap<>(CompCmd.class);
    private Map<CompCmd, Map<ParamCmd, String>> CompCmdChanges = new EnumMap<>(CompCmd.class);
    //private List<Object> CmdExecutionChain;
    private String CmdExecutionChain = "";
    private Map<ParamCmd, String> ParamCmdChanges = new HashMap<>();
    public final Connection connection;
    private final boolean debug;
    private final boolean verbose;

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

    //TODO: Missing: CRTCMD

    //TODO: Add config file support like YAML

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
    }     

    public ParamMap(boolean debug, boolean verbose, Connection connection) {
        this.debug = debug;
        this.verbose = verbose;
        this.connection = connection;
    }

    public boolean containsKey(Object cmd, ParamCmd param) {
      return get(cmd).containsKey(param);
    }

    public Set<ParamCmd> keySet(Object cmd){
      return get(cmd).keySet();
    }

    public Map<ParamCmd, String> get(Object cmd) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return CompCmdMap.getOrDefault(compCmd, new HashMap<ParamCmd, String>());
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return SysCmdMap.getOrDefault(sysCmd, new HashMap<ParamCmd, String>());
      }
      //TODO: Throw exception
      return null;
    }

    public List<ParamCmd> getPattern(Object cmd) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return cmdToPatternMap.get(compCmd);
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return SysCmdToPatternMap.get(sysCmd);
      }
      //TODO: Throw exception
      return null;
    }

    public Map<ParamCmd, String> getChanges(Object cmd) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return CompCmdChanges.getOrDefault(compCmd, new HashMap<ParamCmd, String>());
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return SysCmdChanges.getOrDefault(sysCmd, new HashMap<ParamCmd, String>());
      }
      //TODO: Throw exception
      return null;
    }

    public String get(Object cmd, ParamCmd param) {
      return get(cmd).getOrDefault(param, "");
    }

    public String remove(Object cmd, ParamCmd param) {
      return remove(cmd, param, get(cmd), getChanges(cmd));
    }

    public String remove(Object cmd, ParamCmd param, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges) {
      String oldValue = paramMap.remove(param);

      String currentChain = paramChanges.getOrDefault(param, "");
      if (currentChain.isEmpty()) {
        currentChain = param.name() + " : [REMOVED]"; // First entry is a removal
      } else {
        currentChain += " => [REMOVED]"; // Append removal to existing chain
      }
      paramChanges.put(param, currentChain);

      put(cmd, paramMap, paramChanges);

      return oldValue;
    }

    public String put(Object cmd, ParamCmd param, ValCmd value) {
      return put(cmd, param, value.toString());
    }

    public String put(Object cmd, ParamCmd param, String value) {
      return put(cmd, get(cmd), getChanges(cmd), param, value);
    }

    public String put(Object cmd, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges, ParamCmd param, String value) {

      switch (param) {
        case TEXT:
          value = "'" + value + "'";
          break;
      
        case SRCSTMF:
          value = "'" + value + "'";
          break;
      
        default:
          break;
      }

      String oldValue = paramMap.put(param, value);

      //TODO: Add param change tracking
      String currentChain = paramChanges.getOrDefault(param, "");
      if (currentChain.isEmpty()) {
        currentChain = param.name() + " : " + value; // First insertion
      } else {
        currentChain += " => " + value; // Update: append the new value to the chain
      }
      paramChanges.put(param, currentChain);

      put(cmd, paramMap, paramChanges);

      return oldValue;  // Calls the overridden put(ParamCmd, String); no .toString() needed
    }

    public void put(Object cmd, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        CompCmdMap.put(compCmd, paramMap);  // Re-put the (possibly new) inner map
        CompCmdChanges.put(compCmd, paramChanges);
        return;
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        SysCmdMap.put(sysCmd, paramMap);
        SysCmdChanges.put(sysCmd, paramChanges);
        return;
      }
      return;
    }

    public void showChanges(Object command) {
      System.out.println(getCommandName(command));
      showChanges(getPattern(command), getChanges(command));
    }

    public void showChanges(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramChanges) {
      for (ParamCmd param : compilationPattern) {
        getChangeString(paramChanges.getOrDefault(param, ""), param);
      }
    }

    public void getChangeString(String change, ParamCmd paramCmd){
      if (change.isEmpty()) return;

      System.out.println(change);
    }

    public String getCommandName(Object cmd){
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return compCmd.name();
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return sysCmd.name();
      }
      return "";
    }
    
    public String getParamChain(Object command){
      return getParamChain(getPattern(command), get(command), getCommandName(command));
    }

    public String getParamChain(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramMap, String command){
      StringBuilder sb = new StringBuilder(); 

      for (ParamCmd param : compilationPattern) {
        sb.append(getParamString(paramMap.getOrDefault(param, "NULL"), param));
      }

      return command + sb.toString();
    }

    public String getParamString(String val, ParamCmd paramCmd){
      if ("NULL".equals(val)) return "";

      return " " + paramCmd.name() + "(" + val + ")";
    }

    public void executeCommand(Object cmd){
      showChanges(cmd);
      executeCommand(getParamChain(cmd));
    }

    public void executeCommand(String command){
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
        //TODO: Add a class filed that stores the messages and is updated with each compilation command
        // but make the massages ENUM to just do something like .contains(CPF5813) and then the delete
        // like DLTOBJ OBJ() OBJTYPE()
        //if ("CPF5813".equals(e.getMessage()))
        e.printStackTrace();
        getJoblogMessages(commandTime);
        throw new IllegalArgumentException("Could not execute command: " + command); //TODO: Catch this and throw the appropiate message
      }

      System.out.println("Command successful: " + command);
      getJoblogMessages(commandTime);
    }

    public void getJoblogMessages(Timestamp commandTime){
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
}