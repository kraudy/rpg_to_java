package com.github.kraudy.compiler;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.SysCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class CommandMapDeserializer extends JsonDeserializer<List<String>> {
  @Override
  public List<String> deserialize(JsonParser p, DeserializationContext ctxt)
          throws IOException {

    //TODO: List of commands to be executed. They need to be stored to be later executed. For now, it is a mundane list.
    List<String> paramList = new ArrayList<>();

    ParamMap result = new ParamMap();
    ObjectNode node = p.getCodec().readTree(p);

    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      String cmdName = entry.getKey().toUpperCase();
      JsonNode paramsNode = entry.getValue();

      //TODO: Add CompCmd?
      SysCmd sysCmd;
      try {
          sysCmd = SysCmd.valueOf(cmdName);
      } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Unknown system command: " + cmdName);
      }

      if (!paramsNode.isObject()) {
          throw new IllegalArgumentException("Parameters for " + cmdName + " must be a map");
      }

      ObjectNode paramObj = (ObjectNode) paramsNode;
      Iterator<Map.Entry<String, JsonNode>> paramFields = paramObj.fields();
      while (paramFields.hasNext()) {
          Map.Entry<String, JsonNode> paramEntry = paramFields.next();
          String paramName = paramEntry.getKey().toUpperCase();
          JsonNode valueNode = paramEntry.getValue();

          ParamCmd paramCmd = ParamCmd.valueOf(paramName);

          result.put(sysCmd, paramCmd, valueNode);
      }

      paramList.add(result.getCommandString(sysCmd));
    }

    return paramList;
  }
}