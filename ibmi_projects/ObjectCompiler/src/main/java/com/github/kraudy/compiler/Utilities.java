package com.github.kraudy.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kraudy.compiler.CompilationPattern.Command;
import com.github.kraudy.compiler.CompilationPattern.ObjectType;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
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

    public String asString() {
        String base = library + "." + objectName + "." + objectType.name();
        return (sourceType != null) ? base + "." + sourceType.name() : base;
    }
  }

  public static String nodeToString(JsonNode node) {
    String value = "";
    if (node.isNull()) return null;
    if (node.isTextual()) {
      try { value = ValCmd.fromString(node.asText()).toString(); }
      catch (Exception ignored) { value = node.asText();}
    }
    if (node.isInt()) value = node.asText();
    if (node.isArray()) {
        //TODO: Do here the list joining(" ") and make it string to not deal with that later
        List<String> elements = new ArrayList<>();
        node.elements().forEachRemaining(child -> {
            // Recursively extract text, handle nested values safely
            if (!child.isNull()) {
                elements.add(child.asText());
            }
        });
        value = String.join(" ", elements).trim(); // Space sparated list
    }
    return value;
  }

  public static String validateParamValue(ParamCmd param, String value) {
    switch (param) {
      case TEXT:
      case SRCSTMF:
        return "''" + value + "''";
    
      case MODULE:
        String[] list = value.split(" ");
        if(list.length <= 1) return value;

        value = "";
        for(String module : list){
          if(module.contains("/")) {
            value += module;
          } else {
            value += ValCmd.LIBL.toString() + "/" + module;
          }
          value += " ";
        }
        return value.trim();
    
      default:
        break;
    }
    return value;
  }

  public static boolean validateCommandParam(Command cmd, ParamCmd param) {
    /* Check if param is in the command pattern */
    System.out.println("validateCommandParam command: " + cmd.name());
    if (!CompilationPattern.commandToPatternMap.getOrDefault(cmd, Collections.emptyList()).contains(param)) {
      return false;
    }

    return true;
  }

  public static String validParamList() {
    return String.join(", ", 
        java.util.Arrays.stream(ParamCmd.values())
            .map(Enum::name)
            .toList());
  }
}