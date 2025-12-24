package com.github.kraudy.compiler;

import java.sql.Timestamp;
import java.util.Map;


/*
 * Custom exception for compiler errors, with built-in context to avoid silent failures.
 * Wraps originals and adds: failed command, target key, timestamps, and optional extra details.
 */
public class CompilerException extends RuntimeException {
  private final String failedCommand;      // e.g., "CRTBNDRPG PGM(ROBKRAUDY1/HELLO) ..."
  private final TargetKey targetKey;       // Affected target (if applicable)
  private final Timestamp commandTime;     // When it failed
  private final Map<String, String> extraContext;  // e.g., { "joblog_snippet": "CPF1234..." }

  // Constructor for general use (no target/command)
  public CompilerException(String message, Throwable cause) {
      super(message, cause);
      this.failedCommand = null;
      this.targetKey = null;
      this.commandTime = null;
      this.extraContext = null;
  }

  public CompilerException(String message, Throwable cause,
                            String failedCommand, TargetKey targetKey,
                            Timestamp commandTime, Map<String, String> extraContext) {
      super(message + " | Command: " + failedCommand + 
            (targetKey != null ? " | Target: " + targetKey.asString() : "") +
            (commandTime != null ? " | Time: " + commandTime : ""), cause);
      this.failedCommand = failedCommand;
      this.targetKey = targetKey;
      this.commandTime = commandTime;
      this.extraContext = (extraContext != null) ? extraContext : Map.of();
  }

  // Getters
  public String getFailedCommand() { return failedCommand; }
  public TargetKey getTargetKey() { return targetKey; }
  public Timestamp getCommandTime() { return commandTime; }
  public Map<String, String> getExtraContext() { return extraContext; }

  // Convenience: Dump full context as string (for logging)
  public String getFullContext() {
      return "CompilerException Details:\n" +
              "- Message: " + getMessage() + "\n" +
              "- Command: " + failedCommand + "\n" +
              "- Target: " + (targetKey != null ? targetKey.asString() : "N/A") + "\n" +
              "- Time: " + commandTime + "\n" +
              "- Extra: " + extraContext + "\n" +
              "- Cause: " + getCause();
  }
}