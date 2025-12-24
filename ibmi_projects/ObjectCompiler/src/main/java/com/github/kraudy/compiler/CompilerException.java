package com.github.kraudy.compiler;

import java.sql.Timestamp;
import java.util.Map;

/*
 * Custom exception for compiler errors
 */
public class CompilerException extends RuntimeException {
  private final String failedCommand;               // Failed command
  private final TargetKey targetKey;                // Compilation target
  private final Timestamp commandTime;              // Fail time
  private final Map<String, String> extraContext;   // System messages

  /* Constructor for general use (no target/command) */
  public CompilerException(String message, Throwable cause) {
    super(message, cause);
    this.failedCommand = null;
    this.targetKey = null;
    this.commandTime = null;
    this.extraContext = null;
  }

  /* Constructor for no target command */
  public CompilerException(String message, Throwable cause, String failedCommand, Timestamp commandTime, Map<String, String> extraContext) {
    //super(message + " | Command: " + failedCommand + " | Time: " + commandTime, cause);
    super(message, cause);
    this.failedCommand = failedCommand;
    this.targetKey = null;
    this.commandTime = commandTime;
    this.extraContext = extraContext;
  }

  /* Constructor for target and compilation command */
  public CompilerException(String message, Throwable cause,
                            String failedCommand, TargetKey targetKey,
                            Timestamp commandTime, Map<String, String> extraContext) {
    //super(message + " | Command: " + failedCommand + " | Target: " + targetKey.asString() +
    super(message, cause);
    this.failedCommand = failedCommand;
    this.targetKey = targetKey;
    this.commandTime = commandTime;
    this.extraContext = extraContext;
  }

  /* Getters */
  public String getFailedCommand() { return failedCommand; }
  public TargetKey getTargetKey() { return targetKey; }
  public Timestamp getCommandTime() { return commandTime; }
  public Map<String, String> getExtraContext() { return extraContext; }

  /* Get full context */
  public String getFullContext() {
    StringBuilder sb = new StringBuilder();

    sb.append("CompilerException Details:\n");

    sb.append("- Message: ").append(getMessage()).append("\n");
    sb.append("- Command: ").append(failedCommand != null ? failedCommand : "(none)").append("\n");

    if (targetKey != null) sb.append("- Target: ").append(targetKey.asString()).append("\n");

    if (commandTime != null) sb.append("- Time: ").append(commandTime).append("\n");

    if (extraContext != null) sb.append("- Extra: ").append(extraContext).append("\n");

    Throwable cause = getCause();
    /* No previous cause */
    if (cause == null) return sb.append("- Cause: null\n").toString();

    sb.append("- Cause: ");

    /* Previous general exception */
    if (!(cause instanceof CompilerException)) return sb.append(cause).append("\n").toString();

    /* Previous  CompilerException with recursive context */
    CompilerException ce = (CompilerException) cause;
    return sb.append("\nChained ").append(ce.getFullContext()).toString();
  }

}