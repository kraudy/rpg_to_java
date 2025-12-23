package com.github.kraudy.compiler;

import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

/*
 * Simple Unix-style CLI argument parser.
 */
public class ArgParser {
  private final Map<String, Object> options = new HashMap<>();

  private static final Map<String, String> validOptions = new HashMap<>();

  static {
    validOptions.put("f", "yamlFile");
    validOptions.put("file", "yamlFile");

    validOptions.put("dry-run", "dryRun");

    validOptions.put("x", "debug");

    validOptions.put("v", "verbose");

    validOptions.put("diff", "diff");

  }

  private static final List<String> booleanOptions = Arrays.asList(
    "dryRun", "debug", "verbose", "diff"
  );

  public ArgParser(String[] args) {
    parse(args);
  }

  private void parse(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];

      if (!arg.startsWith("-")) throw new IllegalArgumentException("Invalid argument: " + arg + ". Use -short or --long options.");

      /* Extract option name (strip leading - or --) */
      String optName = arg.startsWith("--") ? arg.substring(2) : arg.substring(1);

      if (optName.isEmpty()) throw new IllegalArgumentException("Empty option: " + arg);

      // Handle combined short options (e.g., -vx means -v -x)
      if (!arg.startsWith("--") && optName.length() > 1) {
        for (char c : optName.toCharArray()) {
          String shortOpt = String.valueOf(c);
          String fieldName = validOptions.get(shortOpt);
          if (fieldName == null) throw new IllegalArgumentException("Unknown option: -" + shortOpt);

          if (!booleanOptions.contains(fieldName)) throw new IllegalArgumentException("Combined short options are only supported for boolean flags: -" + shortOpt);

          options.put(fieldName, true);
        }
        continue;
      }

      // Map short/long to valid names
      String fieldName = validOptions.get(optName);
      if (fieldName == null) throw new IllegalArgumentException("Unknown option: --" + optName + " or -" + optName);

      // Check if it's a flag (no value expected next)
      if(booleanOptions.contains(fieldName)){
        options.put(fieldName, true);
        continue;
      }

      // Expect value at next index
      if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + arg);

      /* Extract value from next string in args */
      String value = args[++i];
      if (value.startsWith("-")) throw new IllegalArgumentException("Value for " + arg + " cannot start with '-': " + value);

      options.put(fieldName, value);
    }
  }

  // Getters (with defaults and validation)
  public String getYamlFile() {
      String file = (String) options.get("yamlFile");

      if (file == null) throw new IllegalArgumentException("Required: -f or --file <YAML build file>");

      if (!isValidFile(file)) throw new IllegalArgumentException("Invalid YAML file: " + file + " (must exist and be readable)");

      return file;
  }

  public boolean isDryRun() {
    return (boolean) options.getOrDefault("dryRun", false);
  }

  public boolean isDebug() {
    return (boolean) options.getOrDefault("debug", false);
  }

  public boolean isVerbose() {
    return (boolean) options.getOrDefault("verbose", false);
  }

  public boolean isDiff() {
    return (boolean) options.getOrDefault("diff", false);
  }

  // Simple validation helpers (extend as needed)
  private boolean isValidFile(String path) {
    File f = new File(path);
    return f.exists() && f.canRead() && path.endsWith(".yaml");
  }

  // Print usage (call on error)
  public static void printUsage() {
    System.err.println("Usage: compiler [-f|--file <YAML>] [--dry-run] [-x] [-v]");
    System.err.println("  -f, --file     YAML build file (required)");
    System.err.println("  --diff         Only build changed objects");
    System.err.println("  --dry-run      Show commands without executing");
    System.err.println("  -x,            Debug mode");
    System.err.println("  -v,            Verbose output");
    System.exit(1);
  }

}
