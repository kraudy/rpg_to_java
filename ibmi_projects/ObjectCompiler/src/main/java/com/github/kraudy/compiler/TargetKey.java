package com.github.kraudy.compiler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.github.kraudy.compiler.CompilationPattern.Command;
import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ObjectType;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.SourceType;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

public class TargetKey {
  public String library;
  public String objectName;
  public ObjectType objectType;
  public SourceType sourceType; // null if absent
  public String sourceFile;
  public String sourceName; // Set to object name
  public String sourceStmf; // IFS route
  public CompCmd compilationCommand;
  public ParamMap ParamCmdSequence;

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

    /* Set default source file in case no SRCFILE or SRCSTMF are not provided  */
    this.sourceFile = SourceType.defaultSourcePf(this.sourceType, this.objectType);

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