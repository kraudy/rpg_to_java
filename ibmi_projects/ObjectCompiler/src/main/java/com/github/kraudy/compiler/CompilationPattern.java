package com.github.kraudy.compiler;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;

import com.github.kraudy.compiler.ObjectDescription.ObjectType;

public class CompilationPattern {
    // Resolver map for command builders (functions that build command strings based on spec)
  private Map<CompCmd, Function<ObjectDescription, String>> cmdBuilders = new EnumMap<>(CompCmd.class);

  public enum CompCmd { CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY }

  public enum ParamCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL, SRCFILE, SRCMBR, ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF }

  //TODO: Add a Map<String, ValCmd>
  public enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM, CURLIB; 
    @Override
    public String toString() {
        return "*" + name();
    }  
  } // add * to these

  /* Maps source type to its compilation command */
  public static final Map<ObjectDescription.SourceType, Map<ObjectDescription.ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(ObjectDescription.SourceType.class);

  /* Maps params to values */
  public static final Map<ParamCmd, EnumSet<ValCmd>> valueParamsMap = new EnumMap<>(ParamCmd.class);

  static{
    /*
     * Populate mapping from (ObjectDescription.SourceType, ObjectDescription.ObjectType) to CompCmd
    */
    // TODO: There has to be a cleaner way of doing this, maybe using :: or lambda to auto define them
    /* Maps sources and object type to compilation command */
    Map<ObjectDescription.ObjectType, CompCmd> rpgMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    rpgMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTRPGPGM);
    typeToCmdMap.put(ObjectDescription.SourceType.RPG, rpgMap);

    Map<ObjectDescription.ObjectType, CompCmd> rpgLeMap = new EnumMap<>(ObjectDescription.ObjectType.class);
    rpgLeMap.put(ObjectDescription.ObjectType.MODULE, CompCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectDescription.ObjectType.PGM, CompCmd.CRTBNDRPG);
    rpgLeMap.put(ObjectDescription.ObjectType.SRVPGM, CompCmd.CRTSRVPGM); // Assuming compilation involves module creation first, but mapping to final command
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

    // TODO: Make the Arrays as Set and use them to check if the parameter value is valid
    // The corresponding order should be defined just be sequence of if validaitons on the command constructor
    // for this, a mapping from string to ParamCmd is needed like '*OUTPUT' => ParamCmd.OUTPUT  Map<String, ParamCmd>
    // and other for Map<String, ValCmd>. These two are neede to make the conversion between parmas/value strinc to Enums
    // this will ease the validation using the switch and also validate if they exist
    // valueParamsMap would be change to Map<ParamCmd, Set<ValCmd>>
    // i'm thinking of a switch without break for optionla params where the command follow the requiered compilation order by the OS
    // TODO: Make these strings
    // TODO: Maybe create a new class: CompilationPattern
    // Populate valueParamsMap with special values for each parameter (add * when using in commands)
    valueParamsMap.put(ParamCmd.OUTPUT, EnumSet.of(ValCmd.OUTFILE));
    valueParamsMap.put(ParamCmd.OUTMBR, EnumSet.of(ValCmd.FIRST, ValCmd.REPLACE)); // FIRST is now reliably first
    valueParamsMap.put(ParamCmd.OBJTYPE, EnumSet.of(ValCmd.PGM, ValCmd.SRVPGM));
    valueParamsMap.put(ParamCmd.MODULE, EnumSet.of(ValCmd.PGM));
    valueParamsMap.put(ParamCmd.BNDSRVPGM, EnumSet.of(ValCmd.SRVPGM));
    valueParamsMap.put(ParamCmd.LIBL, EnumSet.of(ValCmd.LIBL));
    valueParamsMap.put(ParamCmd.SRCFILE, EnumSet.of(ValCmd.FILE, ValCmd.LIBL));
    valueParamsMap.put(ParamCmd.PGM, EnumSet.of(ValCmd.CURLIB, ValCmd.LIBL)); // CURLIB is now first; swap if you want LIBL first
    valueParamsMap.put(ParamCmd.OBJ, EnumSet.of(ValCmd.LIBL, ValCmd.FILE, ValCmd.DTAARA));
    // TODO: for parms with no defined value: EnumSet.noneOf(ValCmd.class)

    // TODO: I think this Supliers is what i really need
    // Maybe i can send enums as parameters too

    //TODO: These suppliers could be instances and not static to add param validation
    //TODO: If there is not a supplier, then an input param is needed
    //TODO: I can also return the lambda function... that would be nice and would allow a higher abstraction function to get it
  }  

  public CompilationPattern(){
    // TODO: These could be build base on object type and source.
    // TODO: Move this to the constructor or the iObject class?
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
  public String buildModuleCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MODULE(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceName(spec, CompCmd.CRTRPGMOD)).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // Similar for bound commands
  public String buildBoundCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" PGM(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceName(spec, CompCmd.CRTBNDRPG)).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For CRTSQLRPGI
  public String buildSqlRpgCmd(ObjectDescription spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" OBJ(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" OBJTYPE(*").append(spec.getObjectType().name()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceName(spec, CompCmd.CRTSQLRPGI)).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For CRTSRVPGM
  public String buildSrvPgmCmd(ObjectDescription spec) {
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
    sb.append(" SRCMBR(").append(getSourceName(spec, CompCmd.RUNSQLSTM)).append(")");
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

  // TODO: Move this to ObjectDescription and add '*' if needed to the String method
  public String getSourceName(ObjectDescription spec, CompCmd cmd) {
    /* Get Source Member name */
    if (spec.getSourceName() != null && !spec.getSourceName().isEmpty()) {
      return spec.getSourceName();
    }

    // ObjectDescription.ObjectType objectType = spec.getObjectType();
    
    // TODO: I tried using objectType to do the map but CRTSQLRPGI is different. Validate this
    /* If no source member name is provided, the system default is supplied based on the object type */
    switch (cmd) {
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTRPGPGM:
      case CRTCLPGM:
        return "*PGM";
      case CRTRPGMOD:
      case CRTCLMOD:
        return "*MODULE";
      case CRTSQLRPGI:
        return "*OBJ";
      default:
        return spec.objectName; // Fallback for SQL, etc.
    }
  }

}
