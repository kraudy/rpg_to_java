package com.github.kraudy.compiler;

import java.sql.Timestamp;
import java.util.Map;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ObjectType;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.SourceType;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

/*
 * Simple POJO for compilation targets
 * A new object is created per each target defined in the spec (Yaml file)
 */
public class TargetKey {
  private String library;              // Target object library
  private String objectName;           // Target object name
  private ObjectType objectType;       // Target object type
  private SourceType sourceType;       // Target source type
  private String sourceFile;           // Target Source Phisical File name.
  private String sourceName;           // Target Source member name. Set to object name by default
  private String sourceStmf;           // Target Ifs source stream file
  private CompCmd compilationCommand;  // Compilation command
  private ParamMap ParamCmdSequence;   // Compilation command's Param:Value 

  private Timestamp lastSourceEdit;    // Last time the source was edited
  private Timestamp lastBuild;         // Last time the object was compiled

  public TargetKey(String key) {
    String[] parts = key.split("\\.");
    if (parts.length != 4) {
      throw new IllegalArgumentException("Invalid key: " + key + ". Expected: library.objectName.objectType.sourceType");
    }

    this.library = parts[0].toUpperCase();
    if (this.library.isEmpty()) throw new IllegalArgumentException("Library name is required.");


    this.objectName = parts[1].toUpperCase();
    if (this.objectName.isEmpty()) throw new IllegalArgumentException("Object name is required.");

    try {
      this.objectType = ObjectType.valueOf(parts[2].toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid objectType : " + parts[2]);
    }

    try {
      this.sourceType = SourceType.valueOf(parts[3].toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid sourceType : " + parts[3]);
    }

    /* Set default source file */
    try {
      this.sourceFile = SourceType.defaultSourcePf(this.sourceType, this.objectType);
    } catch (IllegalArgumentException e){
      throw e;
    }

    /* Set default source name to object name */
    this.sourceName = this.objectName;

    /* Get target key compilation command */
    this.compilationCommand = CompilationPattern.getCompilationCommand(this.sourceType, this.objectType);

    /* Init param map for this target */
    this.ParamCmdSequence = new ParamMap();

    /* Set default compilation params */
    Utilities.SetDefaultParams(this);
  }

  public void setStreamSourceFile(String sourcePath){
    this.sourceStmf = sourcePath;
  }

  public void setLastEdit(Timestamp lastSourceEdit){
    this.lastSourceEdit = lastSourceEdit;
  }

  public void setLastBuild(Timestamp lastBuild){
    this.lastBuild = lastBuild;
  }

  public String getQualifiedObject(){
    return this.library + "/" + this.objectName;
  }

  public String getQualifiedObject(ValCmd valcmd){
    return valcmd.toString() + "/" + this.objectName;
  }

  public String getQualifiedSourceFile(){
    return this.library + "/" + this.sourceFile;
  }

  public boolean containsKey(ParamCmd param) {
    return this.ParamCmdSequence.containsKey(param);
  }

  public boolean containsStreamFile() {
    return !("").equals(this.sourceStmf);
  }

  public boolean isModule() {
    return this.objectType == ObjectType.MODULE;
  }

  public boolean isProgram() {
    return this.objectType == ObjectType.PGM;
  }

  public boolean isServiceProgram() {
    return this.objectType == ObjectType.SRVPGM;
  }

  public void putAll(Map<ParamCmd, String> params) {
    this.ParamCmdSequence.putAll(this.compilationCommand, params);
  }

  public String get(ParamCmd param) {
    return this.ParamCmdSequence.get(param);
  }

  public String getCommandString(){
    return this.ParamCmdSequence.getCommandString(this.compilationCommand);
  }

  public String put(ParamCmd param, String value) {
    return this.ParamCmdSequence.put(this.compilationCommand, param, value);
  }

  public String put(ParamCmd param, ValCmd value) {
    return this.ParamCmdSequence.put(this.compilationCommand, param, value);
  }

  public String asString() {
    return library + "." + objectName + "." + objectType.name() + "." + sourceType.name();
  }

  public String getStreamFile() {
    return this.sourceStmf;
  }

  public String getObjectName() {
    return this.objectName;
  }

  public String getObjectType() {
    return this.objectType.toParam();
  }

  public String getSourceFile() {
    return this.sourceFile;
  }

  public String getSourceName() {
    return this.sourceName;
  }

  public CompCmd getCompilationCommand() {
    return this.compilationCommand;
  }

  public ParamMap getParamMap() {
    return this.ParamCmdSequence;
  }

}