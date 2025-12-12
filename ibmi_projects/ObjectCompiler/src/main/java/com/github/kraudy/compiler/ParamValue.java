package com.github.kraudy.compiler;

import java.util.ArrayList;
import java.util.List;

public class ParamValue {
    private String current = "";
    private Integer count = 0;
    private final List<String> history = new ArrayList<String>();

    public ParamValue() {
    }

    // Constructor for initial insertion
    public ParamValue(String value) {
        this.current = value;
        //this.history.add(value + " [INIT]");
        this.history.add(value);
        this.count += 1;
    }

    // Getters
    public String get() {
      //if (this.current == null) return "NULL";
      return current;
    }

    public String put(String value) {
      this.history.add(value);
      this.current = value;
      this.count += 1;
      return getPrevious();
    }

    public String getPrevious() {
        if (count < 1) return this.history.getFirst();
        return this.history.get(count - 1);
    }

    public String remove() {
      this.history.add("[REMOVED]");
      this.current = null;
      return getPrevious();
    }

    public List<String> getHistory() {
        return new ArrayList<String>(history);  // Return copy to prevent external mutation
    }

    public String getLastChange() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public boolean wasRemoved() {
        return "[REMOVED]".equals(getLastChange());
    }
}