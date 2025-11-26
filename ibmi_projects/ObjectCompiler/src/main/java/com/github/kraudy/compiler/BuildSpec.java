package com.github.kraudy.compiler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildSpec {
  public Map<String, Object> defaults = new HashMap<>();
  public LinkedHashMap<String, TargetSpec> targets = new LinkedHashMap<>();

  public static class TargetSpec {
      public String dbgview;
      public String actgrp;
      public String text;
      public String export;
      public List<String> modules;
      public String replace;
      public List<String> onSuccess;
      public List<String> onFailure;
      // ... add more as needed
      public Map<String, Object> extra = new HashMap<>(); // catch-all
  }
}
