package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import com.github.kraudy.compiler.CompilationPattern.ObjectType;
import com.github.kraudy.compiler.CompilationPattern.SourceType;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

public class Utilities {
  public static String buildKey(String library, String objectName, ObjectType objectType, SourceType sourceType) {
    String base = library + "." + objectName + "." + objectType.name();
    return (sourceType != null) ? base + "." + sourceType.name() : base;
  }

  public static class ParsedKey {
    public String library;
    public String objectName;
    public ObjectType objectType;
    public SourceType sourceType; // null if absent

    public String getQualifiedObject(){
      return this.library + "/" + this.objectName;
    }

    public String getQualifiedObject(ValCmd valcmd){
      return valcmd.toString() + "/" + this.objectName;
    }

    @JsonCreator  // Enables deserialization from a JSON string like "MYLIB.HELLO.PGM.RPGLE"
    public ParsedKey(String key) {
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
    }

    public ParsedKey withLibrary(String newLibrary) {
      if (newLibrary == null || newLibrary.trim().isEmpty()) {
        throw new IllegalArgumentException("Library name is required.");
      }
      String upperLibrary = newLibrary.toUpperCase();
      String newKey = upperLibrary + "." + this.objectName + "." + this.objectType.name() +
                      (this.sourceType != null ? "." + this.sourceType.name() : "");
      return new ParsedKey(newKey); // Reuse constructor for full validation
    }

    @JsonValue  // Serializes to a JSON string like "MYLIB.HELLO.PGM.RPGLE"
    public String asString() {
        String base = library + "." + objectName + "." + objectType.name();
        return (sourceType != null) ? base + "." + sourceType.name() : base;
    }
  }
}