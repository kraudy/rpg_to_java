package com.github.kraudy.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

public class ParamMap extends HashMap<ParamCmd, String> {
    private Map<ParamCmd, String> ParamCmdChanges = new HashMap<>();
    private final boolean debug;
    //private final boolean verbose;

    public ParamMap(boolean debug) {
        this.debug = debug;
    }

    @Override
    public String put(ParamCmd param, String value) {

        // Validation: Ensure value is a valid ValCmd option if it matches a known pattern
        //TODO: Add validation here for params with defined values
        /* 
        if (value.startsWith("*")) {
            ValCmd valCmd = ValCmd.valueOf(value.substring(1).toUpperCase());
            value = ParamCmd.paramValue(param, valCmd);  // Re-validate and normalize
        }
        */

        switch (param) {
          case TEXT:
            value = "'" + value + "'";
            break;
        
          default:
            break;
        }

        String oldValue = super.put(param, value);

        //TODO: Add extra values like REMOVE or (RETREIVED) to the change chain
        // to be more transparent

        String currentChain = ParamCmdChanges.getOrDefault(param, "");
        if (currentChain.isEmpty()) {
          currentChain = param.name() + " : " + value; // First insertion
        } else {
          currentChain += " => " + value; // Update: append the new value to the chain
        }
        ParamCmdChanges.put(param, currentChain);

        return oldValue;
    }

    public String put(ParamCmd param, ValCmd value) {
        return put(param, value.toString());
    }

    public void showChanges(List<ParamCmd> compilationPattern) {
      for (ParamCmd param : compilationPattern) {
        getChangeString(param);
      }
    }

    public void getChangeString(ParamCmd paramCmd){
      String change = this.ParamCmdChanges.getOrDefault(paramCmd, "");  // Retrieved or empty

      if (change.isEmpty()) return;

      System.out.println(change);
    }
}