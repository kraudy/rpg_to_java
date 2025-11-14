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
import com.github.kraudy.compiler.ObjectDescription.ObjectType;
import com.github.kraudy.compiler.ObjectDescription.SourceType;
import com.github.kraudy.migrator.SourceMigrator;


public class CompilationPattern {
  private ParamMap ParamCmdSequence;
  Utilities.ParsedKey targetKey;
  private SourceMigrator migrator;

  public enum CompCmd { 
    CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY, CRTPF, CRTCMD;

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

    //TODO: Maybe i could add a method here that does the same as getParamString()
    
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
  public static final Map<SourceType, Map<ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);

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
   

  //TODO: Maybe overload this to only pass the key as parameter or get the data and call with specific values
  public CompilationPattern(SourceMigrator migrator){
    this.migrator = migrator;
  }

  public ParamMap ResolveCompilationParams(ParamMap ParamCmdSequence, CompCmd compilationCommand, String objectName){
    /* Get optional params */
    //TODO: I think, only this is necessary.

    
    /* Migration logic */
    switch (compilationCommand){
      case CRTCLMOD:
        break;

      case CRTRPGMOD:
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTSQLRPGI:
      case CRTSRVPGM:
      case RUNSQLSTM:
        if (!ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCSTMF)) {
          System.out.println("SRCFILE data: " + ParamCmdSequence.get(compilationCommand, ParamCmd.SRCFILE));
          this.migrator.setParams(ParamCmdSequence.get(compilationCommand, ParamCmd.SRCFILE), objectName, "sources");
          this.migrator.api(); // Try to migrate this thing
          System.out.println("After calling migration api");
          
          ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, this.migrator.getFirstPath());
          ParamCmdSequence.put(compilationCommand, ParamCmd.TGTCCSID, ValCmd.JOB); // Needed to compile from stream files

          ParamCmdSequence.remove(compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(compilationCommand, ParamCmd.SRCMBR); 
        }
        if(ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCSTMF) &&
            ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCFILE)){
          ParamCmdSequence.remove(compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(compilationCommand, ParamCmd.SRCMBR); 
        }

      case CRTCLPGM:
      case CRTRPGPGM:
        /* 
        For OPM, create temp members if source is IFS (reverse migration).
        ParamCmdSequence.put(compilationCommand, ParamCmd.SRCSTMF, stmfPath);
        migrator.IfsToMember(ParamCmdSequence.get(ParamCmd.SRCSTMF), Library);
        ParamCmdSequence.remove(ParamCmd.SRCFILE);  // Switch to stream file
        ParamCmdSequence.put(compilationCommand, ParamCmd.SRCMBR, member);
        */
        break;

      case CRTDSPF:
      case CRTPF:
      case CRTLF:
      case CRTPRTF:
      case CRTMNU:
      case CRTQMQRY:
          break;
    }

    /* Resolve params conflicts */
    
    switch (compilationCommand){
      case CRTRPGMOD:
        if (ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCSTMF)) {
          ParamCmdSequence.remove(compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(compilationCommand, ParamCmd.SRCMBR); 
        }
      case CRTCLMOD:
        break;

      case CRTBNDRPG:
        if (!ParamCmdSequence.containsKey(compilationCommand, ParamCmd.DFTACTGRP)) {
          ParamCmdSequence.remove(compilationCommand, ParamCmd.STGMDL); 
        }
      case CRTBNDCL:
      case CRTCLPGM:
        break;
        
      case CRTRPGPGM:
        break;

      case CRTSQLRPGI:
        if (ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCSTMF)) {
          ParamCmdSequence.put(compilationCommand, ParamCmd.CVTCCSID, ValCmd.JOB);
        }
        break;

      case CRTSRVPGM:
        if (ParamCmdSequence.containsKey(compilationCommand, ParamCmd.SRCSTMF) && 
            ParamCmdSequence.containsKey(compilationCommand, ParamCmd.EXPORT)) {
          ParamCmdSequence.remove(compilationCommand, ParamCmd.EXPORT); 
        }
        break;

      case RUNSQLSTM:
        break;

      case CRTDSPF:
      case CRTPF:
      case CRTLF:
      case CRTPRTF:
      case CRTMNU:
      case CRTQMQRY:
        break;

      default: 
        break;
    }

    return ParamCmdSequence;

  }

  public static CompCmd getCompilationCommand(SourceType sourceType, ObjectType objectType){
    return typeToCmdMap.get(sourceType).get(objectType);
  }

}
