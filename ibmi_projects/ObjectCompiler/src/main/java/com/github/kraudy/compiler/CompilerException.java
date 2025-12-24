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
  public CompilerException(String message, Throwable cause, String failedCommand, Timestamp commandTime, Map<String, String> extraContext) {
    super(message + " | Command: " + failedCommand + " | Time: " + commandTime, cause);
    this.failedCommand = failedCommand;
    this.targetKey = null;
    this.commandTime = commandTime;
    this.extraContext = extraContext;
  }

  public CompilerException(String message, Throwable cause,
                            String failedCommand, TargetKey targetKey,
                            Timestamp commandTime, Map<String, String> extraContext) {
    super(message + " | Command: " + failedCommand + " | Target: " + targetKey.asString() +
                    " | Time: " + commandTime, cause);
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
    return "CompilerException Details:\n" +
            "- Message: " + getMessage() + "\n" +
            "- Command: " + failedCommand + "\n" +
            (targetKey != null ? "- Target: " + targetKey.asString() + "\n" : "") +
            "- Time: " + commandTime + "\n" +
            "- Extra: " + extraContext + "\n" +
            "- Cause: " + getCause();
  }
}