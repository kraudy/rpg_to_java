package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.kraudy.compiler.BuildSpec.TargetSpec;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.Utilities.ParsedKey;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* This class represents the pattern of the YAML file */
public class BuildSpec {
  public final Connection connection;
  private final boolean debug;
  private final boolean verbose;
  private boolean dryRun;
  
  // Global defaults – deserialized by our custom deserializer
  @JsonProperty("defaults")
  @JsonDeserialize(using = ParamMapDeserializer.class)
  public final Map<ParamCmd, Object> defaults = new HashMap<>();

  // Global system commands (before/after everything)
  @JsonProperty("before")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> before = new ArrayList<>();

  @JsonProperty("after")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> after = new ArrayList<>();

  //TODO: This could also be used to get a linked list of comand: params
  // Ordered targets
  public final LinkedHashMap<String, TargetSpec> targets = new LinkedHashMap<>();

  //TODO: Change the list for these maps
  //public Map<SysCmd, Object> before;
  //public Map<SysCmd, Object> after;

  public BuildSpec(boolean debug, boolean verbose, Connection connection, boolean dryRun) {
    this.debug = debug;
    this.verbose = verbose;
    this.connection = connection;
    this.dryRun = dryRun;
  }

  public BuildSpec() {
    this.debug = false;
    this.verbose = false;
    this.connection = null;
    this.dryRun = false;
}

  public BuildSpec(ParsedKey targetKey, boolean debug, boolean verbose, Connection connection, boolean dryRun) {
    TargetSpec spec = new TargetSpec();
    //TODO: Add specific spec params here
    this.targets.put(targetKey.asString(),  spec);
    this.debug = debug;
    this.verbose = verbose;
    this.connection = connection;
    this.dryRun = dryRun;
  }

  public static class TargetSpec {
    @JsonProperty("params")
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final Map<ParamCmd, Object> params = new HashMap<>();

    // Per-target system commands
    @JsonProperty("before")
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final List<String> before = new ArrayList<>();

    @JsonProperty("after")
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final List<String> after = new ArrayList<>();
    
    public List<String> onSuccess;
    public List<String> onFailure;

    @JsonAnySetter
    public void unknown(String name, Object value) {
      throw new IllegalArgumentException(
          "Unknown parameter in target '" + name + "'. Valid parameters are the ones from ParamCmd enum.");
    }
  }
}