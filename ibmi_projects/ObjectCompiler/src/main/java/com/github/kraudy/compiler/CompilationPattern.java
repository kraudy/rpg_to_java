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
  private CompCmd compilationCommand;
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

    /* Sql maps */

    Map<ObjectDescription.ObjectType, CompCmd> sqlMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    sqlMap.put(ObjectDescription.ObjectType.TABLE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.INDEX, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.VIEW, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.ALIAS, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.PROCEDURE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.FUNCTION, CompCmd.RUNSQLSTM);
    typeToCmdMap.put(ObjectDescription.SourceType.SQL, sqlMap);

    /* Dds maps */

    Map<ObjectDescription.ObjectType, CompCmd> ddsMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    ddsMap.put(ObjectDescription.ObjectType.PF, CompCmd.CRTPF);
    ddsMap.put(ObjectDescription.ObjectType.DSPF, CompCmd.CRTDSPF);
    ddsMap.put(ObjectDescription.ObjectType.LF, CompCmd.CRTLF);
    typeToCmdMap.put(ObjectDescription.SourceType.DDS, ddsMap);

    //TODO: Maybe i could extend this to being also like a standard command executor. We'll see.

  }  
   

  //TODO: Maybe overload this to only pass the key as parameter or get the data and call with specific values
  public CompilationPattern(SourceMigrator migrator, ParamMap ParamCmdSequence, CompCmd compilationCommand, String objectName){

    this.migrator = migrator;

    /* Get optional params */
    //TODO: I think, only this is necessary.
    this.ParamCmdSequence = ParamCmdSequence;

    this.compilationCommand = compilationCommand;
    
    /* Migration logic */
    switch (this.compilationCommand){
      case CRTCLMOD:
        break;

      case CRTRPGMOD:
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTSQLRPGI:
      case CRTSRVPGM:
      case RUNSQLSTM:
        if (!ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.SRCSTMF)) {
          System.out.println("SRCFILE data: " + ParamCmdSequence.get(this.compilationCommand, ParamCmd.SRCFILE));
          this.migrator.setParams(ParamCmdSequence.get(this.compilationCommand, ParamCmd.SRCFILE), objectName, "sources");
          this.migrator.api(); // Try to migrate this thing
          System.out.println("After calling migration api");
          
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.SRCSTMF, this.migrator.getFirstPath());
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.TGTCCSID, ValCmd.JOB); // Needed to compile from stream files

          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCMBR); 
        }
        if(ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.SRCSTMF) &&
            ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.SRCFILE)){
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCMBR); 
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
    
    switch (this.compilationCommand){
      case CRTRPGMOD:
        if (ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.SRCSTMF)) {
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCMBR); 
        }
      case CRTCLMOD:
        break;

      case CRTBNDRPG:
        if (!ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.DFTACTGRP)) {
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.STGMDL); 
        }
      case CRTBNDCL:
      case CRTCLPGM:
        break;
        
      case CRTRPGPGM:
        break;

      case CRTSQLRPGI:
        if (ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.SRCSTMF)) {
          ParamCmdSequence.put(this.compilationCommand, ParamCmd.CVTCCSID, ValCmd.JOB);
        }
        break;

      case CRTSRVPGM:
        if (ParamCmdSequence.containsKey(this.compilationCommand, ParamCmd.EXPORT)) {
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCFILE); 
          ParamCmdSequence.remove(this.compilationCommand, ParamCmd.SRCMBR); 
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

      default: throw new IllegalArgumentException("Compilation command builder not found");
    }
  }

  public static CompCmd getCompilationCommand(SourceType sourceType, ObjectType objectType){
    return typeToCmdMap.get(sourceType).get(objectType);
  }

  public CompCmd getCompilationCommand(){
    return this.compilationCommand;
  }

  public ParamMap getParamMap(){
    return this.ParamCmdSequence;
  }

  public ParamMap getParamCmdSequence() { return this.ParamCmdSequence; }

}
