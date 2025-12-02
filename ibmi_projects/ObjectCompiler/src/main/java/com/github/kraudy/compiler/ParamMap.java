package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;

import com.github.kraudy.compiler.CompilationPattern.Command;
import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import com.github.kraudy.compiler.CompilationPattern.SysCmd;

public class ParamMap {
  public final Connection connection;
  private final boolean debug;
  private final boolean verbose;
  private boolean dryRun;

  private final Map<Command, Map<ParamCmd, String>> paramMap = new HashMap<>();
  private final Map<Command, Map<ParamCmd, String>> paramChanges = new HashMap<>();
  private final StringBuilder CmdExecutionChain = new StringBuilder();
  

  public ParamMap(boolean debug, boolean verbose, Connection connection, boolean dryRun) {
    this.debug = debug;
    this.verbose = verbose;
    this.connection = connection;
    this.dryRun = dryRun;
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

  public void put(Command cmd, Map<ParamCmd, Object> params) {
    if (params == null) return;


    params.forEach((param, value) -> put(cmd, param, value));

  }

  public String put(Command cmd, ParamCmd param, Object value) {
    //TODO: toString() should work on any case
    if (value instanceof String) {
      String strValue = value.toString().trim();  
      
      if ("yes".equalsIgnoreCase(strValue) || "true".equalsIgnoreCase(strValue)) {
        return this.put(cmd, param, ValCmd.YES);
      }
        
      if ("no".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
        return this.put(cmd, param, ValCmd.NO);
      }
        
      return this.put(cmd, param, strValue);
    }

    if (value instanceof ValCmd) {
      ValCmd valCmd = (ValCmd) value;
      return this.put(cmd, param, valCmd);
    }

    // Handle modules list
    if (value instanceof List<?>) {
      if(param != ParamCmd.MODULE){
        return "";
      } 
      List<String> list = (List<String>) value;
      String joined = list.stream()
          .map(Object::toString)
          .map(s -> "*LIBL/" + s)
          .collect(Collectors.joining(" "));
      return this.put(cmd, param, joined);
    }
    
    return put(cmd, param, value.toString());
  }

  public String put(Command cmd, ParamCmd param, ValCmd value) {
    return put(cmd, param, value.toString());
  }

  public String put(Command cmd, ParamCmd param, String value) {
    return put(cmd, get(cmd), getChanges(cmd), param, value);
  }

  public String put(Command cmd, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges, ParamCmd param, String value) {

    switch (param) {
      case TEXT:
        value = "'" + value + "'";
        break;
    
      case SRCSTMF:
        value = "'" + value + "'";
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

  //TODO: Fix this to just use executeCommand
  public void executeRawCommands(List<String> commands, String phase) {
    if (commands == null || commands.isEmpty()) return;

    System.out.println("=== " + phase.toUpperCase() + " COMMANDS ===");
    for (String cmd : commands) {
      if (cmd == null || cmd.trim().isEmpty()) continue;

      String trimmed = cmd.trim();
      if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
        // Allow comments
        System.out.println("# " + trimmed.substring(1).trim());
        continue;
      }

      System.out.println("Executing: " + trimmed);
      executeCommand(trimmed);

    }
    System.out.println("=== END " + phase.toUpperCase() + " ===\n");
  }

  public void executeCommand(Command cmd){
    ResolveConflicts(cmd);
    getChangesSummary(cmd);
    executeCommand(getCommandString(cmd));
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
    switch (cmd){
      case CRTRPGMOD:
        if (this.containsKey(cmd, ParamCmd.SRCSTMF)) {
          this.remove(cmd, ParamCmd.SRCFILE); 
          this.remove(cmd, ParamCmd.SRCMBR); 
        }
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

  public void executeCommand(String command){
    Timestamp commandTime = null;
    try (Statement stmt = connection.createStatement();
        ResultSet rsTime = stmt.executeQuery("SELECT CURRENT_TIMESTAMP AS Command_Time FROM sysibm.sysdummy1")) {
      if (rsTime.next()) {
        commandTime = rsTime.getTimestamp("Command_Time");
      }
    } catch (SQLException e) {
      if (verbose) System.err.println("Could not get command time.");
      if (debug) e.printStackTrace();
      throw new IllegalArgumentException("Could not get command time.");
    }

    if (this.CmdExecutionChain.length() > 0) {
      this.CmdExecutionChain.append(" => ");
    }
    this.CmdExecutionChain.append(command);

    /* Dry run just returns before executing the command */
    if(dryRun){
      return;
    }

    try (Statement cmdStmt = connection.createStatement()) {
      cmdStmt.execute("CALL QSYS2.QCMDEXC('" + command + "')");
    } catch (SQLException e) {
      System.out.println("Command failed.");
      //TODO: Add a class filed that stores the messages and is updated with each compilation command
      // but make the massages ENUM to just do something like .contains(CPF5813) and then the delete
      // like DLTOBJ OBJ() OBJTYPE()
      //if ("CPF5813".equals(e.getMessage()))
      e.printStackTrace();
      getJoblogMessages(commandTime);
      throw new IllegalArgumentException("Could not execute command: " + command); //TODO: Catch this and throw the appropiate message
    }

    System.out.println("Command successful: " + command);
    getJoblogMessages(commandTime);
  }

  public void getJoblogMessages(Timestamp commandTime){
    // SQL0601 : Object already exists
    // CPF5813 : File CUSTOMER in library ROBKRAUDY2 already exists
    try (Statement stmt = connection.createStatement();
        ResultSet rsMessages = stmt.executeQuery(
          "SELECT MESSAGE_TIMESTAMP, MESSAGE_ID, SEVERITY, MESSAGE_TEXT, COALESCE(MESSAGE_SECOND_LEVEL_TEXT, '') As MESSAGE_SECOND_LEVEL_TEXT " +
          "FROM TABLE(QSYS2.JOBLOG_INFO('*')) " + 
          "WHERE FROM_USER = USER " +
          "AND MESSAGE_TIMESTAMP > '" + commandTime + "' " +
          "AND MESSAGE_ID NOT IN ('SQL0443', 'CPC0904', 'CPF2407') " +
          "ORDER BY MESSAGE_TIMESTAMP DESC "
        )) {
      while (rsMessages.next()) {
        Timestamp messageTime = rsMessages.getTimestamp("MESSAGE_TIMESTAMP");
        String messageId = rsMessages.getString("MESSAGE_ID").trim();
        String severity = rsMessages.getString("SEVERITY").trim();
        String message = rsMessages.getString("MESSAGE_TEXT").trim();
        String messageSecondLevel = rsMessages.getString("MESSAGE_SECOND_LEVEL_TEXT").trim();
        // Format the timestamp as a string
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formattedTime = sdf.format(messageTime);
        
        // Print in a formatted table-like structure
        System.out.printf("%-20s | %-10s | %-4s | %s%n", formattedTime, messageId, severity, message);
      } 
    } catch (SQLException e) {
      System.out.println("Could not get messages.");
      e.printStackTrace();
    }
  }

  public String getExecutionChain() {
    return CmdExecutionChain.toString();
  }
}