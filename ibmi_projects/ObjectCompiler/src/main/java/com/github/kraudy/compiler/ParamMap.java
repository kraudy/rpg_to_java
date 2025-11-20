package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

import com.github.kraudy.compiler.ObjectDescription.SysCmd;
import com.github.kraudy.compiler.ObjectDescription.ObjectType;
import com.github.kraudy.compiler.ObjectDescription.SourceType;

import com.github.kraudy.compiler.CompilationPattern;

public class ParamMap {
    //TODO: Add another map like 
    // The problem is that it needs to be able to store other commands besides the compilation ones.
    // I would like to just send the compilation cmd to this thing and let it form the string and compile it.
    // But i need a way to deal with the general Enum of SysCmd and CompCmd, maybe with a SET or a MAP using .contains() to know if it
    // is a compilation command, maybe Overriding the PUT method
    //TODO: (if command instanceof CompCmd) could me useful
    //private Map<Object, Map<ParamCmd, String>> GeneralCmdMap = new EnumMap<>(Object.class);
    private Map<SysCmd, Map<ParamCmd, String>> SysCmdMap = new EnumMap<>(SysCmd.class);
    private Map<SysCmd, Map<ParamCmd, String>> SysCmdChanges = new EnumMap<>(SysCmd.class);

    public Map<CompCmd, Map<ParamCmd, String>> CompCmdMap = new EnumMap<>(CompCmd.class);
    private Map<CompCmd, Map<ParamCmd, String>> CompCmdChanges = new EnumMap<>(CompCmd.class);
    //private List<Object> CmdExecutionChain;
    private String CmdExecutionChain = "";
    private Map<ParamCmd, String> ParamCmdChanges = new HashMap<>();
    public final Connection connection;
    private final boolean debug;
    private final boolean verbose;


    public ParamMap(boolean debug, boolean verbose, Connection connection) {
        this.debug = debug;
        this.verbose = verbose;
        this.connection = connection;
    }

    public boolean containsKey(Object cmd, ParamCmd param) {
      return get(cmd).containsKey(param);
    }

    public Set<ParamCmd> keySet(Object cmd){
      return get(cmd).keySet();
    }

    public Map<ParamCmd, String> get(Object cmd) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return CompCmdMap.getOrDefault(compCmd, new HashMap<ParamCmd, String>());
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return SysCmdMap.getOrDefault(sysCmd, new HashMap<ParamCmd, String>());
      }
      //TODO: Throw exception
      return null;
    }

    public List<ParamCmd> getPattern(Object cmd) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return CompilationPattern.cmdToPatternMap.get(compCmd);
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return CompilationPattern.SysCmdToPatternMap.get(sysCmd);
      }
      //TODO: Throw exception
      return null;
    }

    public Map<ParamCmd, String> getChanges(Object cmd) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return CompCmdChanges.getOrDefault(compCmd, new HashMap<ParamCmd, String>());
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return SysCmdChanges.getOrDefault(sysCmd, new HashMap<ParamCmd, String>());
      }
      //TODO: Throw exception
      return null;
    }

    public String get(Object cmd, ParamCmd param) {
      return get(cmd).getOrDefault(param, "");
    }

    public String remove(Object cmd, ParamCmd param) {
      return remove(cmd, param, get(cmd), getChanges(cmd));
    }

    public String remove(Object cmd, ParamCmd param, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges) {
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

    public String put(Object cmd, ParamCmd param, ValCmd value) {
      return put(cmd, param, value.toString());
    }

    public String put(Object cmd, ParamCmd param, String value) {
      return put(cmd, get(cmd), getChanges(cmd), param, value);
    }

    public String put(Object cmd, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges, ParamCmd param, String value) {

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

    public void put(Object cmd, Map<ParamCmd, String> paramMap, Map<ParamCmd, String> paramChanges) {
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        CompCmdMap.put(compCmd, paramMap);  // Re-put the (possibly new) inner map
        CompCmdChanges.put(compCmd, paramChanges);
        return;
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        SysCmdMap.put(sysCmd, paramMap);
        SysCmdChanges.put(sysCmd, paramChanges);
        return;
      }
      return;
    }

    public void showChanges(Object command) {
      System.out.println(getCommandName(command));
      showChanges(getPattern(command), getChanges(command));
    }

    public void showChanges(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramChanges) {
      for (ParamCmd param : compilationPattern) {
        getChangeString(paramChanges.getOrDefault(param, ""), param);
      }
    }

    public void getChangeString(String change, ParamCmd paramCmd){
      if (change.isEmpty()) return;

      System.out.println(change);
    }

    public String getCommandName(Object cmd){
      if (cmd instanceof CompCmd) {
        CompCmd compCmd  = (CompCmd) cmd;
        return compCmd.name();
      }
      if (cmd instanceof SysCmd) {
        SysCmd sysCmd  = (SysCmd) cmd;
        return sysCmd.name();
      }
      return "";
    }
    
    public String getParamChain(Object command){
      return getParamChain(getPattern(command), get(command), getCommandName(command));
    }

    public String getParamChain(List<ParamCmd> compilationPattern, Map<ParamCmd, String> paramMap, String command){
      StringBuilder sb = new StringBuilder(); 

      for (ParamCmd param : compilationPattern) {
        sb.append(getParamString(paramMap.getOrDefault(param, "NULL"), param));
      }

      return command + sb.toString();
    }

    public String getParamString(String val, ParamCmd paramCmd){
      if ("NULL".equals(val)) return "";

      return " " + paramCmd.name() + "(" + val + ")";
    }

    public void executeCommand(Object cmd){
      ResolveConflicts(cmd);
      showChanges(cmd);
      executeCommand(getParamChain(cmd));
    }

    public void ResolveConflicts(Object cmd){
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
}