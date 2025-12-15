package com.github.kraudy.compiler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;

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
 
  public void executeCommand(List<String> commandList){
    for(String command: commandList){
      executeCommand(command);
    }
  }

  //TODO: I need a way to know if it is a CompCdm or a SysCmd
  public void executeCommand(String command){
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
      System.out.println("Command failed: " + command);
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
      throw new IllegalArgumentException("Could not get command time.");
    }
    return currentTime;
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

  //TODO: This is kinda slow.
  // String cpysplfCmd = "CPYSPLF FILE(" + objectName + ") TOFILE(QTEMP/SPLFCPY) JOB(*) SPLNBR(*LAST)";
  // Or send it to a stream file
  // Try to use CPYSPLF to a stream file or db2 table
  /*  https://gist.github.com/BirgittaHauser/f28e3527f1cc4c422a05eea865b455bb */
  private void showCompilationSpool(Timestamp compilationTime, String user, String objectName) throws SQLException{

    System.out.println("Compiler error messages: \n");

    try(Statement stmt = connection.createStatement();
      ResultSet rsCompilationSpool = stmt.executeQuery(
        "With " +
        "Spool as ( " +
          "Select b.ordinal_position, Spooled_Data " + 
          "from  qsys2.OutPut_Queue_Entries a Cross Join " +
              "Lateral(Select * " +
                        "From Table(SysTools.Spooled_File_Data( " +
                                                "Job_Name            => a.Job_Name, " +
                                                "Spooled_File_Name   => a.Spooled_File_Name, " +
                                                "Spooled_File_Number => File_Number))) b " +
          "Where     Output_Queue_Name = '" + user + "' " +
                "and USER_NAME = '" + user + "' " + 
                "and SPOOLED_FILE_NAME = '" + objectName + "' " +
                "and OUTPUT_QUEUE_LIBRARY_NAME = 'QGPL' " +
                "and CREATE_TIMESTAMP > '" + compilationTime + "' " +
        "), " +
        "Message As ( " +
          "Select ordinal_position From Spool Where Spooled_Data like '%M e s s a g e   S u m m a r y%' " +
        ") " +
        "Select RTrim(Cast(Spooled_Data As Varchar(132) CCSID " + ObjectCompiler.INVARIANT_CCSID +" )) As  Spooled_Data " + 
        "from Spool Where ordinal_position >= (Select ordinal_position From Message) "
      )){
        while (rsCompilationSpool.next()) {
          System.out.println(rsCompilationSpool.getString("Spooled_Data"));
        }
    }
  }
}
