package com.github.kraudy.compiler;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* This class represents the pattern of the YAML file */
public class BuildSpec {
  /* Global default compilation params */
  @JsonProperty("defaults")
  @JsonDeserialize(using = ParamMapDeserializer.class)
  public final Map<ParamCmd, String> defaults = new HashMap<>();

  /* Global pre-compilation system commands */
  @JsonProperty("before")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> before = new ArrayList<>();

  /* Global post-compilation system commands */
  @JsonProperty("after")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public final List<String> after = new ArrayList<>();


  /* Global on success system commands */
  @JsonProperty("success")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public List<String> success;

  /* Global on failure system commands */
  @JsonProperty("failure")
  @JsonDeserialize(using = CommandMapDeserializer.class)
  public List<String> failure;

  /* Ordered sequence of targets and their spec */
  public final LinkedHashMap<TargetKey, TargetSpec> targets = new LinkedHashMap<>();

  public BuildSpec() {

  }

  public BuildSpec(TargetKey targetKey) {
    TargetSpec spec = new TargetSpec();
    this.targets.put(targetKey,  spec);
  }

  public static class TargetSpec {
    /* Per-target pre-compilation system commands */
    @JsonProperty("before")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public final List<String> before = new ArrayList<>();

    /* Per-target compilation command params */
    @JsonProperty("params")
    @JsonDeserialize(using = ParamMapDeserializer.class)
    public final Map<ParamCmd, String> params = new HashMap<>();

    /* Per-target post-compilation system commands */
    @JsonProperty("after")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public final List<String> after = new ArrayList<>();
    


    /* Per-target on success system commands */
    @JsonProperty("success")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public List<String> success;

    /* Per-target on failure system commands */
    @JsonProperty("failure")
    @JsonDeserialize(using = CommandMapDeserializer.class)
    public List<String> failure;

    @JsonAnySetter
    public void unknown(String name, Object value) {
      throw new IllegalArgumentException(
          "Unknown parameter in target '" + name + "'. Valid parameters are the ones from ParamCmd enum.");
    }
  }
}