package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import com.github.kraudy.compiler.ObjectDescription.ObjectType;
import com.github.kraudy.compiler.ObjectDescription.SourceType;

public class Utilities {
  public static String buildKey(String library, String objectName, ObjectType objectType, SourceType sourceType) {
    String base = library + "." + objectName + "." + objectType.name();
    return (sourceType != null) ? base + "." + sourceType.name() : base;
  }

  public static class ParsedKey {
    public final String library;
    public final String objectName;
    public final ObjectType objectType;
    public final SourceType sourceType; // null if absent

    @JsonCreator  // Enables deserialization from a JSON string like "MYLIB.HELLO.PGM.RPGLE"
    public ParsedKey(String key) {
      String[] parts = key.split("\\.");
      if (parts.length < 3 || parts.length > 4) { // != 4
        throw new IllegalArgumentException("Invalid key: " + key + ". Expected: library.objectName.objectType[.sourceType]");
      }
      //TODO: Add validation if (parts.length == 2) to get *LIBL
      this.library = parts[0].toUpperCase();
      // if (this.library.equals("*LIBL") || this.library.equals("*CURLIB"))
      if (this.library.isEmpty()) throw new IllegalArgumentException("Library name is required.");


      this.objectName = parts[1].toUpperCase();
      if (this.objectName.isEmpty()) throw new IllegalArgumentException("Object name is required.");

      try {
        this.objectType = ObjectType.valueOf(parts[2].toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid objectType : " + parts[2]);
      }
      //TODO: Maybe this should be required. That will depend on the referencer being able to determine the souce type from source code.
      this.sourceType = (parts.length == 4) ? SourceType.valueOf(parts[3].toUpperCase()) : null;
    }

    @JsonValue  // Serializes to a JSON string like "MYLIB.HELLO.PGM.RPGLE"
    public String asString() {
        String base = library + "." + objectName + "." + objectType.name();
        return (sourceType != null) ? base + "." + sourceType.name() : base;
    }
  }
}