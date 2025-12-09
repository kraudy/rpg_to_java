package com.github.kraudy.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kraudy.compiler.CompilationPattern.Command;
import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import com.github.kraudy.compiler.CompilationPattern.SysCmd;

public class ParamMap {
  private final Map<Command, Map<ParamCmd, String>> paramMap = new HashMap<>();
  private final Map<Command, Map<ParamCmd, String>> paramChanges = new HashMap<>();
  

  public ParamMap() {

  }

  public boolean containsKey(Command cmd, ParamCmd param) {
    return get(cmd).containsKey(param);
  }

  public Set<ParamCmd> keySet(Command cmd){
    return get(cmd).keySet();
  }

  public Map<ParamCmd, String> get(Command cmd) {
    return paramMap.computeIfAbsent(cmd, k -> new HashMap<>());
  }

  public List<ParamCmd> getPattern(Command cmd) {
    return CompilationPattern.commandToPatternMap.getOrDefault(cmd, Collections.emptyList());
  }

  public Map<ParamCmd, String> getChanges(Command cmd) {
    return paramChanges.computeIfAbsent(cmd, k -> new HashMap<>());
  }

  public String get(Command cmd, ParamCmd param) {
    return get(cmd).getOrDefault(param, "");
  }

  public String remove(Command cmd, ParamCmd param) {
    return remove(cmd, param, get(cmd), getChanges(cmd));
  }

  public String remove(Command cmd, ParamCmd param, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges) {
    String oldValue = paramMap.remove(param);

    String currentChain = paramChanges.getOrDefault(param, "");
    if (currentChain.isEmpty()) {
      currentChain = param.name() + " : [REMOVED]"; // First entry is a removal
    } else {
      currentChain += " => [REMOVED]"; // Append removal to existing chain
    }
    paramChanges.put(param, currentChain);

    put(cmd, paramMap, paramChanges);

    return oldValue;
  }

  public void putAll(Command cmd, Map<ParamCmd, String> params) {
    if (params == null) return;

    params.forEach((param, value) -> {
      put(cmd, param, value);
    });

  }

  public String put(Command cmd, ParamCmd param, ValCmd value) {
    return put(cmd, param, value.toString());
  }

  public String put(Command cmd, ParamCmd param, List<String> listValue) {
    String value = "";

    switch (param) {
      case MODULE:
        value = listValue.stream()
          .map(Object::toString)
          .map(s -> ValCmd.LIBL.toString() + "/" + s)
          .collect(Collectors.joining(" "));
        break;
    
      default:
        break;
    }

    //TODO: Add validation here.

    return put(cmd, get(cmd), getChanges(cmd), param, value);
  }

  public String put(Command cmd, ParamCmd param, String value) {
    return put(cmd, get(cmd), getChanges(cmd), param, value);
  }

  public String put(Command cmd, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges, ParamCmd param, String value) {

    switch (param) {
      case TEXT:
        value = "''" + value + "''";
        break;
    
      case SRCSTMF:
        value = "''" + value + "''";
        break;
    
    
      case MODULE:
        String[] list = value.split(" ");
        if(list.length <= 1) break;

        value = "";
        for(String module : list){
          if(module.contains("/")) {
            value += module;
          } else {
            value += ValCmd.LIBL.toString() + "/" + module;
          }
          value += " ";
        }
        value = value.trim();
        break;
    
      default:
        break;
    }

    String oldValue = paramMap.put(param, value);

    //TODO: Add param change tracking
    String currentChain = paramChanges.getOrDefault(param, "");
    if (currentChain.isEmpty()) {
      currentChain = param.name() + " : " + value; // First insertion
    } else {
      currentChain += " => " + value; // Update: append the new value to the chain
    }
    paramChanges.put(param, currentChain);

    put(cmd, paramMap, paramChanges);

    return oldValue;  // Calls the overridden put(ParamCmd, String); no .toString() needed
  }

  public void put(Command cmd, Map<ParamCmd, String> innerParamMap, Map<ParamCmd, String> innerChanges) {
    paramMap.put(cmd, innerParamMap);
    paramChanges.put(cmd, innerChanges);
  }

  public void getChangesSummary(Command command) {
    System.out.println(command.name());
    getChangesSummary(getPattern(command), getChanges(command));
  }

  public void getChangesSummary(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramChanges) {
    for (ParamCmd param : compilationPattern) {
      getChangeString(paramChanges.getOrDefault(param, ""), param);
    }
  }

  public void getChangeString(String change, ParamCmd paramCmd){
    if (change.isEmpty()) return;

    System.out.println(change);
  }
  
  public String getCommandString(Command command){
    ResolveConflicts(command);
    getChangesSummary(command);

    return getCommandString(getPattern(command), get(command), command.name());
  }

  public String getCommandString(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramMap, String command){
    StringBuilder sb = new StringBuilder(); 

    sb.append(command);

    for (ParamCmd param : compilationPattern) {
      sb.append(param.paramString(paramMap.getOrDefault(param, "NULL")));
    }

    return sb.toString();
  }

  public void ResolveConflicts(Command cmd){
    if (cmd instanceof CompCmd) {
      CompCmd compCmd  = (CompCmd) cmd;
      ResolveConflicts(compCmd);
      return;
    }
    if (cmd instanceof SysCmd) {
      SysCmd sysCmd  = (SysCmd) cmd;
      ResolveConflicts(sysCmd);
      return;
    }
  }

  public void ResolveConflicts(CompCmd cmd){

    if (this.containsKey(cmd, ParamCmd.SRCSTMF)) {
      this.put(cmd, ParamCmd.TGTCCSID, ValCmd.JOB);
    }

    switch (cmd){
      case CRTRPGMOD:
      case CRTCLMOD:
        break;

      case CRTBNDRPG:
        if (!this.containsKey(cmd, ParamCmd.DFTACTGRP)) {
          this.remove(cmd, ParamCmd.STGMDL); 
        }
      case CRTBNDCL:
      case CRTCLPGM:
        break;
        
      case CRTRPGPGM:
        break;

      case CRTSQLRPGI:
        if (this.containsKey(cmd, ParamCmd.SRCSTMF)) {
          this.put(cmd, ParamCmd.CVTCCSID, ValCmd.JOB);
        }
        break;

      case CRTSRVPGM:
        if (this.containsKey(cmd, ParamCmd.SRCSTMF) && 
            this.containsKey(cmd, ParamCmd.EXPORT)) {
          this.remove(cmd, ParamCmd.EXPORT); 
        }
        break;

      case RUNSQLSTM:
        break;

      case CRTDSPF:
      case CRTPF:
      case CRTLF:
      case CRTPRTF:
      case CRTMNU:
      case CRTQMQRY:
        break;

      default: 
        break;
    }

    /* Migration logic */
    switch (cmd){
      case CRTRPGMOD:
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTSQLRPGI:
      case CRTSRVPGM:
      case RUNSQLSTM:
        if(this.containsKey(cmd, ParamCmd.SRCSTMF) &&
            this.containsKey(cmd, ParamCmd.SRCFILE)){
          this.remove(cmd, ParamCmd.SRCFILE); 
          this.remove(cmd, ParamCmd.SRCMBR); 
        }
        break;

      default:
          break;
    }
  }

  public void ResolveConflicts(SysCmd cmd){

  }
}