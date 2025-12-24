package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandExecutor {
  private final Connection connection;
  private final boolean debug;
  private final boolean verbose;
  private final boolean dryRun;
  private final StringBuilder CmdExecutionChain = new StringBuilder();

  public CommandExecutor(Connection connection, boolean debug, boolean verbose, boolean dryRun){
    this.connection = connection;
    this.debug = debug;
    this.verbose = verbose;
    this.dryRun = dryRun;

  }
 
  public void executeCommand(List<String> commandList) throws SQLException{
    for(String command: commandList){
      executeCommand(command);
    }
  }

  /* Executes targets compilation commands */
  public void executeCommand(TargetKey key) throws Exception{
    Timestamp commandTime = getCurrentTime();
    String command = key.getCommandString();

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
      System.err.println("Command failed: " + command);
      if(verbose) showCompilationSpool(commandTime);
      Map<String, String> extra = getMapMessages(commandTime);
      throw new CompilerException("Target compilation failed", e, command, key, commandTime, extra);
    }

    System.out.println("Command successful: " + command);
    if(verbose) getJoblogMessages(commandTime);

    /* Set build time */
    key.setLastBuild(commandTime);
  }

  /* Executes system commands */
  public void executeCommand(String command) throws CompilerException {
    Timestamp commandTime = getCurrentTime();

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
      System.err.println("Command failed: " + command);

      Map<String, String> extra = getMapMessages(commandTime);
      throw new CompilerException("Command execution failed", e, command, commandTime, extra);  // No target here
    }

    System.out.println("Command successful: " + command);
    if(verbose) getJoblogMessages(commandTime);
  }

  public Timestamp getCurrentTime(){
    Timestamp currentTime = null;
    try (Statement stmt = connection.createStatement();
        ResultSet rsTime = stmt.executeQuery("SELECT CURRENT_TIMESTAMP AS Command_Time FROM sysibm.sysdummy1")) {
      if (rsTime.next()) {
        currentTime = rsTime.getTimestamp("Command_Time");
      }
    } catch (SQLException e) {
      if (verbose) System.err.println("Could not get command time.");
      if (debug) e.printStackTrace();
      throw new RuntimeException("Could not get command time.");
    }
    return currentTime;
  }

  public Map<String, String> getMapMessages(Timestamp commandTime){
      //TODO: Add an enum of messages
    Map<String, String> extra = new HashMap<String, String>();

    try (Statement stmt = connection.createStatement();
        ResultSet rsMessages = stmt.executeQuery(
          "SELECT MESSAGE_TIMESTAMP, MESSAGE_ID, SEVERITY, MESSAGE_TEXT, COALESCE(MESSAGE_SECOND_LEVEL_TEXT, '') As MESSAGE_SECOND_LEVEL_TEXT " +
          "FROM TABLE(QSYS2.JOBLOG_INFO('*')) " + 
          "WHERE FROM_USER = USER " +
          "AND MESSAGE_TIMESTAMP > '" + commandTime + "' " +
          "AND MESSAGE_ID NOT IN ('SQL0443', 'CPC0904', 'CPF2407') " +
          "ORDER BY MESSAGE_TIMESTAMP ASC " /* Show from first to last */
        )) {
      while (rsMessages.next()) {
        String messageId = rsMessages.getString("MESSAGE_ID").trim();
        String message = rsMessages.getString("MESSAGE_TEXT").trim();
        // Format the timestamp as a string
        
        // Print in a formatted table-like structure
        extra.put(messageId, message);
      } 
    } catch (SQLException e) {
      if (verbose) System.out.println("Could not get messages.");
      if (debug) e.printStackTrace();
      throw new RuntimeException("Could not get messages.");
    }
    return extra;
  }

  public void getJoblogMessages(Timestamp commandTime){
    try (Statement stmt = connection.createStatement();
        ResultSet rsMessages = stmt.executeQuery(
          "SELECT MESSAGE_TIMESTAMP, MESSAGE_ID, SEVERITY, MESSAGE_TEXT, COALESCE(MESSAGE_SECOND_LEVEL_TEXT, '') As MESSAGE_SECOND_LEVEL_TEXT " +
          "FROM TABLE(QSYS2.JOBLOG_INFO('*')) " + 
          "WHERE FROM_USER = USER " +
          "AND MESSAGE_TIMESTAMP > '" + commandTime + "' " +
          "AND MESSAGE_ID NOT IN ('SQL0443', 'CPC0904', 'CPF2407') " +
          "ORDER BY MESSAGE_TIMESTAMP ASC " /* Show from first to last */
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
      if (verbose) System.out.println("Could not get messages.");
      if (debug) e.printStackTrace();
      throw new RuntimeException("Could not get messages.");
    }
  }

  public String getExecutionChain() {
    return CmdExecutionChain.toString();
  }

  private void showCompilationSpool(Timestamp compilationTime) throws SQLException{
    /* Is there a spool file? */
    try(Statement stmt = connection.createStatement();
      ResultSet rsCheckSpool = stmt.executeQuery(
      "Select SPOOLED_FILE_NAME, SPOOLED_FILE_NUMBER, QUALIFIED_JOB_NAME " +
      "From Table ( " +
          "QSYS2.SPOOLED_FILE_INFO( " +
              "USER_NAME => USER , " +
              "STARTING_TIMESTAMP => '" + compilationTime + "' " +
              //"JOB_NAME => (VALUES QSYS2.JOB_NAME) " +
          ") " +
        ") " +
        "LIMIT 1 "
      )){
        if (!rsCheckSpool.next()) {
          if(verbose) System.err.println("No spool found for compilation command");
          return;
        }
    }

    /* Get compilation spool */
    try(Statement stmt = connection.createStatement();
      ResultSet rsCompilationSpool = stmt.executeQuery(
        "Select d.SPOOLED_DATA " +
        "From Table ( " +
            "QSYS2.SPOOLED_FILE_INFO( " +
                "USER_NAME => USER, " +
                "STARTING_TIMESTAMP => '" + compilationTime + "' " +
                //"JOB_NAME => (VALUES QSYS2.JOB_NAME)" +
            ") " +
        ") As s " +
        "Inner Join Table ( " +
            "SYSTOOLS.SPOOLED_FILE_DATA( " +
                "JOB_NAME => s.QUALIFIED_JOB_NAME, " +
                "SPOOLED_FILE_NAME => s.SPOOLED_FILE_NAME, " +
                "SPOOLED_FILE_NUMBER => s.SPOOLED_FILE_NUMBER " +
            ") " +
        ") As d On 1=1" 
      )){
        while (rsCompilationSpool.next()) {
          System.out.println(rsCompilationSpool.getString("SPOOLED_DATA").trim());
        }
    }
  }
}
