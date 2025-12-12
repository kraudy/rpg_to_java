package com.github.kraudy.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
  //private final Map<Command, Map<ParamCmd, String>> paramMap = new HashMap<>();
  //private final Map<Command, Map<ParamCmd, String>> paramChanges = new HashMap<>();

  private final Map<Command, EnumMap<ParamCmd, ParamValue>> paramMap = new HashMap<>();

  public ParamMap() {

  }

  public boolean containsKey(Command cmd, ParamCmd param) {
    return get(cmd).containsKey(param);
  }

  public Set<ParamCmd> keySet(Command cmd){
    return get(cmd).keySet();
  }

  /* Get map of ParamCmd and ParamValue */
  public EnumMap<ParamCmd, ParamValue> get(Command cmd) {
    return paramMap.computeIfAbsent(cmd, k -> new  EnumMap<>(ParamCmd.class));
  }

  /* Get specific param value */
  public String get(Command cmd, ParamCmd param) {
    ParamValue pv = get(cmd).get(param);
    if (pv == null) return "";
    return pv.get();
    //return get(cmd).getOrDefault(param, "").get();
  }

  public List<ParamCmd> getPattern(Command cmd) {
    return CompilationPattern.commandToPatternMap.getOrDefault(cmd, Collections.emptyList());
  }

  //public Map<ParamCmd, String> getChanges(Command cmd) {
  //  return paramChanges.computeIfAbsent(cmd, k -> new HashMap<>());
  //}

  public String remove(Command cmd, ParamCmd param) {

    EnumMap<ParamCmd, ParamValue> inner = get(cmd);
    ParamValue pv = inner.get(param);

    inner.remove(param);

    if (pv == null) {
      return ""; // If no pv, there is nothing to remove
      //pv = new ParamValue();
    }

    return pv.remove();

    //String oldValue = paramMap.remove(param);
    //String currentChain = paramChanges.getOrDefault(param, "");
    //if (currentChain.isEmpty()) {
    //  currentChain = param.name() + " : [REMOVED]"; // First entry is a removal
    //} else {
    //  currentChain += " => [REMOVED]"; // Append removal to existing chain
    //}
    //paramChanges.put(param, currentChain);
    //put(cmd, paramMap, paramChanges);
    //return oldValue;
  }

  public void putAll(Command cmd, Map<ParamCmd, String> params) {
    if (params == null) return;

    params.forEach((param, value) -> {
      if (!Utilities.validateCommandParam(cmd, param)) {
        System.out.println("Rejected: Parameter " + param.name() + " not valid for command " + cmd.name());
        return;
      }
      put(cmd, param, value);
    });

  }

  public String put(Command cmd, ParamCmd param, ValCmd value) {
    return put(cmd, param, value.toString());
  }

  public String put(Command cmd, ParamCmd param, String value) {

    value = Utilities.validateParamValue(param, value);

    /* At this point there should be not invalid command params */
    if (!Utilities.validateCommandParam(cmd, param)) {
      throw new IllegalArgumentException("Parameters " + param.name() + " not valid for command " + cmd.name());
    }

    EnumMap<ParamCmd, ParamValue> inner = get(cmd);
    ParamValue pv = inner.get(param);

    /* If a previous value exists, just append it */
    if (pv != null) return pv.put(value);

    /* Create new value */
    pv = new ParamValue(value);
    inner.put(param, pv);
    return pv.getPrevious();
    
    //TODO: Do i need to do the put here again? it should point to the same object instance.
    //inner.put(param, pv);

    //ParamValue oldPv = inner.get(param); // Get ParamCmd ParamValue
    //ParamValue newPv;
    ////TODO: Maybe just add a put(ParamValue) that accepst another ParamValue isntance and does the changes inside
    //if (oldPv == null) {
    //    newPv = new ParamValue(value);  // Initial insert
    //    //return inner.put(param, new ParamValue(value));
    //} else {
    //    newPv = oldPv.appendChange(value);  // Update with history
    //}
    //inner.put(param, newPv);

    // String oldValue = paramMap.put(param, value);
    //String currentChain = paramChanges.getOrDefault(param, "");
    //if (currentChain.isEmpty()) {
    //  currentChain = param.name() + " : " + value; // First insertion
    //} else {
    //  currentChain += " => " + value; // Update: append the new value to the chain
    //}
    //paramChanges.put(param, currentChain);
    //put(cmd, paramMap, paramChanges);

    //return pv.getPrevious();  // Calls the overridden put(ParamCmd, String); no .toString() needed
  }

  //public void put(Command cmd, Map<ParamCmd, String> innerParamMap, Map<ParamCmd, String> innerChanges) {
  //  paramMap.put(cmd, innerParamMap);
  //  paramChanges.put(cmd, innerChanges);
  //}

  public void getChangesSummary(Command cmd) {
    System.out.println(cmd.name());

    List<ParamCmd> compilationPattern = getPattern(cmd);
    EnumMap<ParamCmd, ParamValue> inner = get(cmd);

    for (ParamCmd param : compilationPattern) {
      ParamValue pv = inner.get(param);
      if (pv == null) continue;
      System.out.println(param.name() + ":" + pv.getHistory());
    }


    //getChangesSummary(getPattern(cmd), getChanges(cmd));
  }

  //public void getChangesSummary(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramChanges) {
  //  for (ParamCmd param : compilationPattern) {
  //    getChangeString(paramChanges.getOrDefault(param, ""), param);
  //  }
  //}

  //public void getChangeString(String change, ParamCmd paramCmd){
  //  if (change.isEmpty()) return;

  //  System.out.println(change);
  //}
  
  public String getCommandString(Command cmd){
    ResolveConflicts(cmd);
    getChangesSummary(cmd);

    List<ParamCmd> compilationPattern = getPattern(cmd);
    EnumMap<ParamCmd, ParamValue> inner = get(cmd);
    StringBuilder sb = new StringBuilder(); 

    sb.append(cmd.name());

    for (ParamCmd param : compilationPattern) {
      ParamValue pv = inner.get(param);
      if (pv == null) continue;
      sb.append(param.paramString(pv.get()));
    }

    return sb.toString();

    //return getCommandString(getPattern(cmd), get(cmd), cmd.name());
  }

  //public String getCommandString(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramMap, String command){
  //  StringBuilder sb = new StringBuilder(); 

  //  sb.append(command);

  //  for (ParamCmd param : compilationPattern) {
  //    sb.append(param.paramString(paramMap.getOrDefault(param, "NULL")));
  //  }

  //  return sb.toString();
  //}

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

    switch (cmd){
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTRPGMOD:
      case CRTCLMOD:
        if (this.containsKey(cmd, ParamCmd.SRCSTMF)) {
          this.put(cmd, ParamCmd.TGTCCSID, ValCmd.JOB);
        }
        break;

      default: 
        break;
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