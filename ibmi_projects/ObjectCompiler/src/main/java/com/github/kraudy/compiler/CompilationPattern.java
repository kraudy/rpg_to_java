package com.github.kraudy.compiler;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Supplier;

public class CompilationPattern {
    // Resolver map for command builders (functions that build command strings based on spec)
  private Map<CompCmd, Supplier<String>> cmdBuilders = new EnumMap<>(CompCmd.class);

  private CompCmd compilationCommand;

  private String targetLibrary;
  private String objectName;
  private ObjectDescription.ObjectType objectType;
  private String sourceLibrary;
  private String sourceFile;
  private String sourceName;
  private ObjectDescription.SourceType sourceType;
  // TODO: These should be in a tuple or something else.
  private Map<ParamCmd, String> ParamCmdSequence;


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
    OPTION, TGTRLS, SORTSEQ_LIB,
    // NEW: Added for (RPG/CL specific)
    GENLVL, DBGVIEW, DBGENCKEY, OPTIMIZE, INDENT, CVTOPT, SRTSEQ, LANGID, REPLACE, USRPRF, AUT, TRUNCNBR, FIXNBR, ALWNULL, DEFINE, ENBPFRCOL, PRFDTA, 
    LICOPT, INCDIR, PGMINFO, INFOSTMF, PPGENOPT, PPSRCFILE, PPSRCMBR, PPSRCSTMF, REQPREXP, PPMINOUTLN,
    GENOPT, SAAFLAG, PRTFILE, PHSTRC, ITDUMP, SNPDUMP, CODELIST, IGNDECERR, LOG, ALWRTVSRC, INCFILE, STGMDL;  // STGMDL for teraspace/inherit

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

  //TODO: Add a Map<String, ValCmd>
  public enum ValCmd { 
    FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, MODULE, OBJ, SRVPGM, CURLIB, ALL, CURRENT,
    NONE, BASIC, FULL,

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

    Map<ObjectDescription.ObjectType, CompCmd> rpgMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    rpgMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTRPGPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.RPG, rpgMap);

    Map<ObjectDescription.ObjectType, CompCmd> rpgLeMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    rpgLeMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTBNDRPG);
    rpgLeMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.RPGLE, rpgLeMap);

    Map<ObjectDescription.ObjectType, CompCmd> sqlRpgLeMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    sqlRpgLeMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.SQLRPGLE, sqlRpgLeMap);

    Map<ObjectDescription.ObjectType, CompCmd> clpMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    clpMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTCLPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.CLP, clpMap);

    Map<ObjectDescription.ObjectType, CompCmd> clleMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    clleMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTCLMOD);
    clleMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTBNDCL);
    clleMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.CLLE, clleMap);

    Map<ObjectDescription.ObjectType, CompCmd> sqlMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    sqlMap.put(ObjectDescription.ObjectType.TABLE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.LF, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.VIEW, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.ALIAS, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.PROCEDURE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectDescription.ObjectType.FUNCTION, CompCmd.RUNSQLSTM);
    typeToCmdMap.put(ObjectDescription.SourceType.SQL, sqlMap);

    // The corresponding order should be defined just be sequence of if validaitons on the command constructor
    // i'm thinking of a switch without break for optionla params where the command follow the requiered compilation order by the OS
    // TODO: Make these strings

    // TODO: I think this Supliers is what i really need
    // Maybe i can send enums as parameters too

    //TODO: These suppliers could be instances and not static to add param validation
    //TODO: If there is not a supplier, then an input param is needed
    //TODO: I can also return the lambda function... that would be nice and would allow a higher abstraction function to get it
  }  

  public CompilationPattern(ObjectDescription odes){

    this.targetLibrary = odes.getTargetLibrary();
    this.objectName = odes.getObjectName();
    this.objectType = odes.getObjectType();
    this.sourceLibrary = odes.getSourceLibrary();
    this.sourceFile = odes.getSourceFile();
    this.sourceName = odes.getSourceName();
    this.sourceType = odes.getSourceType();

    /* Get optional params */

    this.ParamCmdSequence = odes.getParamCmdSequence();

    /* Get compilation command */

    this.compilationCommand = typeToCmdMap.get(sourceType).get(objectType);

    /* Add default values if not provided */

    if (this.targetLibrary.isEmpty()) this.targetLibrary = ValCmd.LIBL.toString();
    if (this.sourceName.isEmpty())    this.sourceName = CompCmd.compilationSourceName(compilationCommand);//ValCmd.PGM.toString();

    /* Generate compilation params values from object description */
    ParamCmdSequence.put(ParamCmd.OBJ, this.targetLibrary + "/" + this.objectName);
    ParamCmdSequence.put(ParamCmd.PGM, this.targetLibrary + "/" + this.objectName);
    ParamCmdSequence.put(ParamCmd.SRVPGM, this.targetLibrary + "/" + this.objectName);
    ParamCmdSequence.put(ParamCmd.MODULE, this.targetLibrary + "/" + this.objectName);

    ParamCmdSequence.put(ParamCmd.OBJTYPE, this.objectType.toParam());

    ParamCmdSequence.put(ParamCmd.SRCFILE, this.sourceLibrary + "/" + this.sourceFile);

    ParamCmdSequence.put(ParamCmd.SRCMBR, this.sourceName);

    ParamCmdSequence.put(ParamCmd.BNDSRVPGM, ValCmd.NONE.toString());
    ParamCmdSequence.put(ParamCmd.COMMIT, ValCmd.NONE.toString());

    // TODO: These could be build base on object type and source.
    // Command builders as functions (pattern matching via enums)
    //TODO: Use buildBoundCmdForOPM() if programType == "OPM"
    cmdBuilders.put(CompCmd.CRTRPGMOD, this::buildModuleCmd); 
    cmdBuilders.put(CompCmd.CRTCLMOD, this::buildModuleCmd);
    cmdBuilders.put(CompCmd.CRTBNDRPG, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTBNDCL, this::buildBoundCmd);

    cmdBuilders.put(CompCmd.CRTRPGPGM, this::builOpmCmd); // OPM command

    cmdBuilders.put(CompCmd.CRTCLPGM, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTSQLRPGI, this::buildSqlRpgCmd);
    cmdBuilders.put(CompCmd.CRTSRVPGM, this::buildSrvPgmCmd);
    cmdBuilders.put(CompCmd.RUNSQLSTM, this::buildSqlCmd);
    // Add more builders for other commands
  }

  public CompCmd getCompilationCommand(){
    return this.compilationCommand;
  }

  public String buildCommand() {
    String params = cmdBuilders.get(compilationCommand).get();
    // Prepend the command name
    return compilationCommand.name() + params;
  }

  // Update for module cmds if needed (e.g., CRTRPGMOD uses similar optionals)
  // TODO: Add CompCmd as param or something, don't burn it.
  public String buildModuleCmd() {
    // Similar to buildBoundCmd but without PGM/DFTACTGRP; add GENLVL, OPTION, etc.
    StringBuilder sb = new StringBuilder();

    sb.append(getParamString(ParamCmd.MODULE));
    sb.append(getParamString(ParamCmd.SRCFILE));
    sb.append(getParamString(ParamCmd.SRCMBR));

    appendCommonParams(sb);

    return sb.toString();
  }

    //TODO: Maybe define what params all these have in common and then a list of the option params as enmus
  // and then validate them in the corresponding order like : PGM, SRCFILE, SRCMBR
  // Similar for bound commands
  public String buildBoundCmd() { // For CRTBNDRPG/CRTBNDCL
    //TODO: Here on in the initializer i could check for required params that are missing and use the default values
    StringBuilder sb = new StringBuilder();

    //TODO: If i can store each ParamCmd in an ordered list, here, i could just do for parcmd in parcmdList : and call the method
    sb.append(getParamString(ParamCmd.PGM));
    sb.append(getParamString(ParamCmd.SRCFILE));
    sb.append(getParamString(ParamCmd.SRCMBR));
    
    // Required/early optionals
    sb.append(getParamString(ParamCmd.GENLVL));
    sb.append(getParamString(ParamCmd.TEXT));
    sb.append(getParamString(ParamCmd.DFTACTGRP));  // Or ACTGRP for ILE
    // Additional params section
    sb.append(getParamString(ParamCmd.OPTION));
    sb.append(getParamString(ParamCmd.DBGVIEW));
    sb.append(getParamString(ParamCmd.DBGENCKEY));
    sb.append(getParamString(ParamCmd.OUTPUT));
    sb.append(getParamString(ParamCmd.OPTIMIZE));
    sb.append(getParamString(ParamCmd.INDENT));
    sb.append(getParamString(ParamCmd.CVTOPT));
    sb.append(getParamString(ParamCmd.SRTSEQ));
    sb.append(getParamString(ParamCmd.LANGID));
    sb.append(getParamString(ParamCmd.REPLACE));
    sb.append(getParamString(ParamCmd.USRPRF));
    sb.append(getParamString(ParamCmd.AUT));
    sb.append(getParamString(ParamCmd.TRUNCNBR));
    sb.append(getParamString(ParamCmd.FIXNBR));
    sb.append(getParamString(ParamCmd.TGTRLS));
    sb.append(getParamString(ParamCmd.ALWNULL));
    sb.append(getParamString(ParamCmd.DEFINE));
    sb.append(getParamString(ParamCmd.ENBPFRCOL));
    if (compilationCommand != CompCmd.CRTBNDCL){
      sb.append(getParamString(ParamCmd.PRFDTA));
    }
    sb.append(getParamString(ParamCmd.LICOPT));
    sb.append(getParamString(ParamCmd.INCDIR));
    sb.append(getParamString(ParamCmd.PGMINFO));
    sb.append(getParamString(ParamCmd.INFOSTMF));
    sb.append(getParamString(ParamCmd.PPGENOPT));
    sb.append(getParamString(ParamCmd.PPSRCFILE));
    sb.append(getParamString(ParamCmd.PPSRCMBR));
    sb.append(getParamString(ParamCmd.PPSRCSTMF));
    sb.append(getParamString(ParamCmd.TGTCCSID));
    sb.append(getParamString(ParamCmd.REQPREXP));
    sb.append(getParamString(ParamCmd.PPMINOUTLN));
    // if (!ParamCmdSequence.getOrDefault(ParamCmd.DFTACTGRP, "").equals("*YES")) {
    if (ParamCmdSequence.keySet().contains(ParamCmd.DFTACTGRP)) {
      sb.append(getParamString(ParamCmd.STGMDL));  // Late optional
    }    

    return sb.toString();

    // if(!options.contains(ParamCmd.PGM)) throw new IllegalArgumentException("Required param not found : '" + ParamCmd.PGM.name() + "'");
    // TODO: This is important!!!
    // if(!ParamCmdSequence.keySet().contains(ParamCmd.PGM)) throw new IllegalArgumentException("Required param not found : '" + ParamCmd.PGM.name() + "'");
    // ParamCmdSequence.values() => Returns a colletion of values from the map. Can be useful. : Collection<Integer> values = map.values();
  }

  public String builOpmCmd() {  // For CRTRPGPGM/CRTCLPGM (similar but OPM-specific)
    StringBuilder sb = new StringBuilder();
    sb.append(getParamString(ParamCmd.PGM));
    sb.append(getParamString(ParamCmd.SRCFILE));
    sb.append(getParamString(ParamCmd.SRCMBR));
    sb.append(getParamString(ParamCmd.GENLVL));
    sb.append(getParamString(ParamCmd.TEXT));
    sb.append(getParamString(ParamCmd.OPTION));
    sb.append(getParamString(ParamCmd.GENOPT));
    sb.append(getParamString(ParamCmd.INDENT));
    sb.append(getParamString(ParamCmd.CVTOPT));
    sb.append(getParamString(ParamCmd.SRTSEQ));
    sb.append(getParamString(ParamCmd.LANGID));
    sb.append(getParamString(ParamCmd.SAAFLAG));
    sb.append(getParamString(ParamCmd.PRTFILE));
    sb.append(getParamString(ParamCmd.REPLACE));
    sb.append(getParamString(ParamCmd.TGTRLS));
    sb.append(getParamString(ParamCmd.USRPRF));
    sb.append(getParamString(ParamCmd.AUT));
    sb.append(getParamString(ParamCmd.PHSTRC));
    sb.append(getParamString(ParamCmd.ITDUMP));
    sb.append(getParamString(ParamCmd.SNPDUMP));
    sb.append(getParamString(ParamCmd.CODELIST));
    sb.append(getParamString(ParamCmd.IGNDECERR));
    sb.append(getParamString(ParamCmd.ALWNULL));
    return sb.toString();
  }

  // For CRTSQLRPGI
  public String buildSqlRpgCmd() {
    StringBuilder sb = new StringBuilder();

    sb.append(getParamString(ParamCmd.OBJ));
    sb.append(getParamString(ParamCmd.OBJTYPE));
    sb.append(getParamString(ParamCmd.SRCFILE));
    sb.append(getParamString(ParamCmd.SRCMBR));

    appendCommonParams(sb);

    return sb.toString();
  }

  // For CRTSRVPGM
  public String buildSrvPgmCmd() {
    //TODO: The spec for the SRPVGM should indicate if it is build using modules, binding dir, export symbols or export all, etc
    StringBuilder sb = new StringBuilder();

    sb.append(getParamString(ParamCmd.SRVPGM));
    sb.append(getParamString(ParamCmd.MODULE));

    sb.append(getParamString(ParamCmd.BNDSRVPGM));

    appendCommonParams(sb);

    return sb.toString();
  }

  // For RUNSQLSTM
  public String buildSqlCmd() {
    StringBuilder sb = new StringBuilder();

    sb.append(getParamString(ParamCmd.SRCFILE));
    sb.append(getParamString(ParamCmd.SRCMBR));

    sb.append(getParamString(ParamCmd.COMMIT));

    appendCommonParams(sb);

    return sb.toString();
  }

  public void appendCommonParams(StringBuilder sb) {
    //TODO: This should have the optional params in the corresponding order so they can be added or omited with no problem
    
    // TODO: Maybe soemthing like this to check the param list: // if(options.contains(ParamCmd.TEXT)) do something;
    sb.append(getParamString(ParamCmd.TEXT));

    // for now, this gives error por CRTBNDRPG: CRTBNDRPG PGM(ROBKRAUDY1/HELLO) SRCFILE(*LIBL/QRPGLESRC) SRCMBR(HELLO) TEXT('Cool hello') ACTGRP('QILE')
    // sb.append(getParamString(ParamCmd.ACTGRP));

  }

  public  String getParamString(ParamCmd paramCmd){
    //TODO: Shoul i update the object desc?
    String val = ParamCmdSequence.getOrDefault(paramCmd, "");  // Retrieved or empty

    if (val.isEmpty()) return "";

    return " " + paramCmd.name() + "(" + val + ")";
  }

}
