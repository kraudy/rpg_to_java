package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.Utilities.TargetKey;

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
  public final Map<ParamCmd, String> defaults = new HashMap<>();

  // Global before
  @JsonProperty("before")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> before = new ArrayList<>();

  // Global after
  @JsonProperty("after")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> after = new ArrayList<>();

  //TODO: Add onFailure

  public final LinkedHashMap<TargetKey, TargetSpec> targets = new LinkedHashMap<>();

  public BuildSpec() {

  }

  public BuildSpec(TargetKey targetKey) {
    TargetSpec spec = new TargetSpec();
    this.targets.put(targetKey,  spec);
  }

  public static class TargetSpec {
    @JsonProperty("params")
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final Map<ParamCmd, String> params = new HashMap<>();

    // Per-target system commands
    @JsonProperty("before")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public final List<String> before = new ArrayList<>();

    @JsonProperty("after")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public final List<String> after = new ArrayList<>();
    
    //TODO: Add onFailure
    //TODO: Add onSuccess
    public List<String> onSuccess;
    public List<String> onFailure;

    @JsonAnySetter
    public void unknown(String name, Object value) {
      throw new IllegalArgumentException(
          "Unknown parameter in target '" + name + "'. Valid parameters are the ones from ParamCmd enum.");
    }
  }
}