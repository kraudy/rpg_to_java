package com.github.kraudy.compiler;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Core struct for capturing compilation specs (JSON-friendly via Jackson)
public class ObjectDescription {
  //TODO: Make this private, add set method and move to another file
  public String targetLibrary;
  public String objectName;
  public ObjectType objectType;
  public String sourceLibrary;
  public String sourceFile;
  public String sourceMember;
  public SourceType sourceType;
  public String text;
  public String actGrp;//TODO: Remove this

  public enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR }

  public enum SourceType { RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL }

  public enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION } // Add more as needed

  public enum CompCmd { CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY }

  public enum ParamCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL, SRCFILE, SRCMBR, ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF }

  //TODO: Maybe is more practical to make these strings
  public enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM, CURLIB } // add * to these

  public enum PostCmpCmd { CHGOBJD }

  public enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC } // TODO: Expand

  /* Maps source type to its compilation command */
  public static final Map<SourceType, Map<ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);

  /* Maps params to values */
  public static final Map<ParamCmd, List<ValCmd>> valueParamsMap = new EnumMap<>(ParamCmd.class);

  /* Maps source type to its default source pf */
  public static final Map<SourceType, DftSrc> typeToDftSrc = new EnumMap<>(SourceType.class);

  /* Maps object attribute to source type (for inference) */
  public static final Map<String, SourceType> attrToSourceType = new HashMap<>();

  static{
        /* From source member type to default source file default name */
    typeToDftSrc.put(SourceType.RPG, DftSrc.QRPGSRC);
    typeToDftSrc.put(SourceType.RPGLE, DftSrc.QRPGLESRC);
    typeToDftSrc.put(SourceType.SQLRPGLE, DftSrc.QRPGLESRC); // Often same as RPGLE
    typeToDftSrc.put(SourceType.CLP, DftSrc.QCLSRC);
    typeToDftSrc.put(SourceType.CLLE, DftSrc.QCLSRC);
    typeToDftSrc.put(SourceType.SQL, DftSrc.QSQLSRC);

    /*
     * Populate mapping from (SourceType, ObjectType) to CompCmd
     */
    // TODO: There has to be a cleaner way of doing this, maybe using :: or lambda to auto define them
    /* Maps sources and object type to compilation command */
    Map<ObjectType, CompCmd> rpgMap = new EnumMap<>(ObjectType.class);
    rpgMap.put(ObjectType.PGM, CompCmd.CRTRPGPGM);
    typeToCmdMap.put(SourceType.RPG, rpgMap);

    Map<ObjectType, CompCmd> rpgLeMap = new EnumMap<>(ObjectType.class);
    rpgLeMap.put(ObjectType.MODULE, CompCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectType.PGM, CompCmd.CRTBNDRPG);
    rpgLeMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM); // Assuming compilation involves module creation first, but mapping to final command
    typeToCmdMap.put(SourceType.RPGLE, rpgLeMap);

    Map<ObjectType, CompCmd> sqlRpgLeMap = new EnumMap<>(ObjectType.class);
    sqlRpgLeMap.put(ObjectType.MODULE, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.PGM, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.SQLRPGLE, sqlRpgLeMap);

    Map<ObjectType, CompCmd> clpMap = new EnumMap<>(ObjectType.class);
    clpMap.put(ObjectType.PGM, CompCmd.CRTCLPGM);
    typeToCmdMap.put(SourceType.CLP, clpMap);

    Map<ObjectType, CompCmd> clleMap = new EnumMap<>(ObjectType.class);
    clleMap.put(ObjectType.MODULE, CompCmd.CRTCLMOD);
    clleMap.put(ObjectType.PGM, CompCmd.CRTBNDCL);
    clleMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.CLLE, clleMap);

    Map<ObjectType, CompCmd> sqlMap = new EnumMap<>(ObjectType.class);
    sqlMap.put(ObjectType.TABLE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.LF, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.VIEW, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.ALIAS, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.PROCEDURE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.FUNCTION, CompCmd.RUNSQLSTM);
    typeToCmdMap.put(SourceType.SQL, sqlMap);

    // Populate attrToSourceType (basic mapping, expand as needed)
    attrToSourceType.put("RPG", SourceType.RPG);
    attrToSourceType.put("RPGLE", SourceType.RPGLE);
    attrToSourceType.put("SQLRPGLE", SourceType.SQLRPGLE);
    attrToSourceType.put("CLP", SourceType.CLP);
    attrToSourceType.put("CLLE", SourceType.CLLE);

    //TODO: Make these strings
    // Populate valueParamsMap with special values for each parameter (add * when using in commands)
    valueParamsMap.put(ParamCmd.OUTPUT, Arrays.asList(ValCmd.OUTFILE));
    valueParamsMap.put(ParamCmd.OUTMBR, Arrays.asList(ValCmd.FIRST, ValCmd.REPLACE)); // FIRST is now reliably first
    valueParamsMap.put(ParamCmd.OBJTYPE, Arrays.asList(ValCmd.PGM, ValCmd.SRVPGM));
    valueParamsMap.put(ParamCmd.MODULE, Arrays.asList(ValCmd.PGM));
    valueParamsMap.put(ParamCmd.BNDSRVPGM, Arrays.asList(ValCmd.SRVPGM));
    valueParamsMap.put(ParamCmd.LIBL, Arrays.asList(ValCmd.LIBL));
    valueParamsMap.put(ParamCmd.SRCFILE, Arrays.asList(ValCmd.FILE, ValCmd.LIBL));
    valueParamsMap.put(ParamCmd.PGM, Arrays.asList(ValCmd.CURLIB, ValCmd.LIBL)); // CURLIB is now first; swap if you want LIBL first
    valueParamsMap.put(ParamCmd.OBJ, Arrays.asList(ValCmd.LIBL, ValCmd.FILE, ValCmd.DTAARA));
    // TODO: for parms with no defined value: EnumSet.noneOf(ValCmd.class)

    // TODO: I think this Supliers is what i really need
    // Maybe i can send enums as parameters too

    //TODO: These suppliers could be instances and not static to add param validation
    //TODO: If there is not a supplier, then an input param is needed
    //TODO: I can also return the lambda function... that would be nice and would allow a higher abstraction function to get it
  }

  //TODO: Move this class to its own file and remove static
  //TODO: Change this name to IbmObject, to be more broader
  // Constructor for Jackson deserialization
  @JsonCreator
  public ObjectDescription(
        @JsonProperty("targetLibrary") String targetLibrary,
        @JsonProperty("objectName") String objectName,
        @JsonProperty("objectType") ObjectType objectType,
        @JsonProperty("sourceLibrary") String sourceLibrary,
        @JsonProperty("sourceFile") String sourceFile,
        @JsonProperty("sourceMember") String sourceMember,
        @JsonProperty("sourceType") SourceType sourceType,
        @JsonProperty("text") String text,
        @JsonProperty("actGrp") String actGrp) {
    //TODO: If validtion like toUpperCase().trim() is needed, add it when passing the params to keep this clean

    if (objectName == null || objectName.isEmpty()) throw new IllegalArgumentException("Object name is required.");

    this.targetLibrary = targetLibrary;
    this.objectName = objectName;
    this.objectType = objectType;
    this.sourceLibrary = sourceLibrary;
    this.sourceFile = (sourceFile.isEmpty()) ? typeToDftSrc.get(sourceType).name() : sourceFile;
    this.sourceMember = (sourceMember.isEmpty() ? objectName : sourceMember); //TODO: Add logic for stream files
    this.sourceType = sourceType;
    this.text = text;
    this.actGrp = actGrp; //TODO: Remove this, maybe add it to another struct with the compilation command params

  }

  // Getters for Jackson serialization
  public String getTargetLibrary() { return targetLibrary; }
  public String getObjectName() { return objectName; }
  public ObjectType getObjectType() { return objectType; }
  public String getSourceLibrary() { return sourceLibrary; }
  public String getSourceFile() { return sourceFile; }
  public String getSourceMember() { return sourceMember; }
  public SourceType getSourceType() { return sourceType; }
  public String getText() { return text; }
  public String getActGrp() { return actGrp; }

  // TODO: This logic encapsulation is nice. It will be helpfull in the future
  // Key method for use in graphs (matches ObjectDependency format)
  public String toGraphKey() {
    return targetLibrary + "/" + objectName + "/" + objectType.name();
  }

  public boolean isPGM(){
    return (this.objectType == ObjectType.PGM) ? true: false;
  }

  public boolean isSRVPGM(){
    return (this.objectType == ObjectType.SRVPGM) ? true: false;
  }

  public boolean isMODULE(){
    return (this.objectType == ObjectType.MODULE) ? true: false;
  }

}