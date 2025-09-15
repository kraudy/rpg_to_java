package com.github.kraudy.compiler;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;

public class CompilationPattern {
    // Resolver map for command builders (functions that build command strings based on spec)
  private Map<CompCmd, Function<ObjectDescription, String>> cmdBuilders = new EnumMap<>(CompCmd.class);

  private String targetLibrary;
  private String objectName;
  private ObjectDescription.ObjectType objectType;
  private String sourceLibrary;
  private String sourceFile;
  private String sourceName;
  private ObjectDescription.SourceType sourceType;


  public enum CompCmd { 
    CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY;

    public static String compilationSourceName(CompCmd cmd, String sourceName){
      if (sourceName != null && !sourceName.isEmpty()) {
        return sourceName;
      }
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
    PGM, MODULE, OBJ, OBJTYPE, OUTPUT, OUTMBR, BNDSRVPGM, LIBL, SRCFILE, SRCMBR, ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF;

    public static ParamCmd fromString(String value) {
      try {
        return ParamCmd.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get compilation command param from string: '" + value + "'");
      }
    } 
    
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
    FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, MODULE, OBJ, SRVPGM, CURLIB; 

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

    /* Add default values if not provided */

    if (this.targetLibrary.isEmpty()) this.targetLibrary = ValCmd.LIBL.toString();
    if (this.sourceName.isEmpty())    this.sourceName = ValCmd.PGM.toString();



    // TODO: These could be build base on object type and source.
    // Command builders as functions (pattern matching via enums)
    cmdBuilders.put(CompCmd.CRTRPGMOD, this::buildModuleCmd);
    cmdBuilders.put(CompCmd.CRTCLMOD, this::buildModuleCmd);
    cmdBuilders.put(CompCmd.CRTBNDRPG, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTBNDCL, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTRPGPGM, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTCLPGM, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTSQLRPGI, this::buildSqlRpgCmd);
    cmdBuilders.put(CompCmd.CRTSRVPGM, this::buildSrvPgmCmd);
    cmdBuilders.put(CompCmd.RUNSQLSTM, this::buildSqlCmd);
    // Add more builders for other commands
  }

  public CompCmd getCompilationCommand(ObjectDescription.SourceType sourceType, ObjectDescription.ObjectType objectType){
    return typeToCmdMap.get(sourceType).get(objectType);
  }

  public String buildCommand(ObjectDescription spec, CompCmd cmd) {
    Function<ObjectDescription, String> builder = cmdBuilders.getOrDefault(cmd, s -> {
      throw new IllegalArgumentException("Unsupported command: " + cmd);
    });
    String params = builder.apply(spec);
    // Prepend the command name
    return cmd.name() + params;
  }

  // Example builder function for module commands
  // TODO: Add CompCmd as param or something, don't burn it.
  public String buildModuleCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MODULE(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(CompCmd.compilationSourceName(CompCmd.CRTRPGMOD, spec.getSourceName())).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

    //TODO: Maybe define what params all these have in common and then a list of the option params as enmus
  // and then validate them in the corresponding order like : PGM, SRCFILE, SRCMBR
  // Similar for bound commands
  // public String buildBoundCmd(ObjectDescription spec, EnumSet<ParamCmd> options) {
  public String buildBoundCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();

    //TODO: If i can store each ParamCmd in an ordered list, here, i could just do for parcmd in parcmdList : and call the method
    //sb.append(" " + ParamCmd.PGM.name() + "(").append(targetLibrary).append("/").append(objectName).append(")");
    sb.append(getParamString(ParamCmd.PGM));
    //sb.append(" " + ParamCmd.SRCFILE.name() + "(").append(ParamCmd.ParamSrcfileName(sourceLibrary, sourceFile)).append(")");
    sb.append(getParamString(ParamCmd.SRCFILE));
    sb.append(" " + ParamCmd.SRCMBR.name() + "(").append(CompCmd.compilationSourceName(CompCmd.CRTBNDRPG, sourceName)).append(")");
    
    appendCommonParams(sb, spec);

    // if(!options.contains(ParamCmd.PGM)) throw new IllegalArgumentException("Required param not found : '" + ParamCmd.PGM.name() + "'");

    return sb.toString();
  }

  // For CRTSQLRPGI
  public String buildSqlRpgCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" OBJ(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" OBJTYPE(*").append(spec.getObjectType().name()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(CompCmd.compilationSourceName(CompCmd.CRTSQLRPGI, spec.getSourceName())).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For CRTSRVPGM
  public String buildSrvPgmCmd(ObjectDescription spec) {
    //TODO: The spec for the SRPVGM should indicate if it is build using modules, binding dir, export symbols or export all, etc
    StringBuilder sb = new StringBuilder();
    sb.append(" SRVPGM(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" MODULE(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")"); // Assume single module
    sb.append(" BNDSRVPGM(*NONE)");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For RUNSQLSTM
  public String buildSqlCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(CompCmd.compilationSourceName(CompCmd.RUNSQLSTM, spec.getSourceName())).append(")");
    sb.append(" COMMIT(*NONE)");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  public void appendCommonParams(StringBuilder sb, ObjectDescription spec) {
    //TODO: This should have the optional params in the corresponding order so they can be added or omited with no problem
    if (spec.getText() != null && !spec.getText().isEmpty()) {
      sb.append(" TEXT('").append(spec.getText()).append("')");
    }
    // if (spec.getActGrp() != null) && !spec.getActGrp().isEmpty(){
    //   sb.append(" ACTGRP(").append(spec.getActGrp()).append(")");
    // }
    // Add more common params (e.g., DFTACTGRP(*NO), BNDDIR, etc.)
  }

  public  String getParamString(ParamCmd paramCmd){
    switch (paramCmd) {
      case PGM:
        return " " + paramCmd.name() + "(" + targetLibrary + "/" + objectName + ")";
    
      case SRCFILE:
        return " " + paramCmd.name() + "(" + sourceLibrary + "/" + sourceFile + ")";

      default:
        return "";
    }
  }

}
