package com.github.kraudy.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.kraudy.compiler.CompilationPattern.Command;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

public class Utilities {
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
    //System.out.println("validateCommandParam command: " + cmd.name());
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