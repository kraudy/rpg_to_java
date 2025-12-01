package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildSpec {
  // Global defaults – deserialized by our custom deserializer
  @JsonDeserialize(using = ParamMapDeserializer.class)
  public final Map<ParamCmd, Object> defaults = new HashMap<>();

  // Ordered targets
  public final LinkedHashMap<String, TargetSpec> targets = new LinkedHashMap<>();

  // NEW: Global pre/post compilation commands
  public List<String> before;
  public List<String> after;

  // Required no-arg constructor (Jackson uses this)
  public BuildSpec() {
      // everything is already initialized
  }

  public static class TargetSpec {
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final Map<ParamCmd, Object> params = new HashMap<>();

    public List<String> before;   // optional per-target
    public List<String> after;    // optional per-target
    
    public List<String> onSuccess;
    public List<String> onFailure;

    @JsonAnySetter
    public void unknown(String name, Object value) {
      throw new IllegalArgumentException(
          "Unknown parameter in target '" + name + "'. Valid parameters are the ones from ParamCmd enum.");
    }
  }
}