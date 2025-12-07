package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
  // Global defaults – deserialized by our custom deserializer
  @JsonProperty("defaults")
  @JsonDeserialize(using = ParamMapDeserializer.class)
  public final Map<ParamCmd, JsonNode> defaults = new HashMap<>();

  // Global before
  @JsonProperty("before")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> before = new ArrayList<>();

  // Global after
  @JsonProperty("after")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> after = new ArrayList<>();

  //TODO: This could also be used to get a linked list of comand: params
  // Ordered targets
  //public final LinkedHashMap<String, TargetSpec> targets = new LinkedHashMap<>();
  public final LinkedHashMap<Utilities.ParsedKey, TargetSpec> targets = new LinkedHashMap<>();

  public BuildSpec() {

  }

  public BuildSpec(ParsedKey targetKey) {
    TargetSpec spec = new TargetSpec();
    //TODO: Add specific spec params here
    //this.targets.put(targetKey.asString(),  spec);
    this.targets.put(targetKey,  spec);
  }

  public static class TargetSpec {
    @JsonProperty("params")
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final Map<ParamCmd, JsonNode> params = new HashMap<>();

    // Per-target system commands
    @JsonProperty("before")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public final List<String> before = new ArrayList<>();

    @JsonProperty("after")
    @JsonDeserialize(using = CommandMapDeserializer.class)
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