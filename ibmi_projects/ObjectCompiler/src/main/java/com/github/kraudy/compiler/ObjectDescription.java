package com.github.kraudy.compiler;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
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

  public enum PostCmpCmd { CHGOBJD }

  public enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC } // TODO: Expand

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

    // Populate attrToSourceType (basic mapping, expand as needed)
    attrToSourceType.put("RPG", SourceType.RPG);
    attrToSourceType.put("RPGLE", SourceType.RPGLE);
    attrToSourceType.put("SQLRPGLE", SourceType.SQLRPGLE);
    attrToSourceType.put("CLP", SourceType.CLP);
    attrToSourceType.put("CLLE", SourceType.CLLE);

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