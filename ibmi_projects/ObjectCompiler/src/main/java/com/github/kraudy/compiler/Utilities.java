package com.github.kraudy.compiler;

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

    public ParsedKey(String key) {
      String[] parts = key.split("\\.");
      if (parts.length < 3 || parts.length > 4) {
        throw new IllegalArgumentException("Invalid key: " + key + ". Expected: library.objectName.objectType[.sourceType]");
      }
      this.library = parts[0].toUpperCase();
      this.objectName = parts[1].toUpperCase();
      try {
        this.objectType = ObjectType.valueOf(parts[2].toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid objectType in key: " + parts[2]);
      }
      this.sourceType = (parts.length == 4) ? SourceType.valueOf(parts[3].toUpperCase()) : null;
    }
  }
}