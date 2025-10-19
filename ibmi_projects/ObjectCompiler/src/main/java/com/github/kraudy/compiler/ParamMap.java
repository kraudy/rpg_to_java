package com.github.kraudy.compiler;

import java.util.HashMap;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.github.kraudy.compiler.CompilationPattern.ValCmd;

public class ParamMap extends HashMap<ParamCmd, String> {
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
            //value = "'" + value + "'";
            break;
        
          default:
            break;
        }

        String oldValue = super.put(param, value);

        if (debug) {
          //if (oldValue != null) {
          //  System.out.println("Previous value. " + param.name() + ": " + oldValue);
          //} 
          //System.out.println("Current value. " + param.name() + ": " + value);
          System.out.println(param.name() + ": " + (oldValue != null ? oldValue + " => " : "") + value);
        }

        return oldValue;
    }

    public String put(ParamCmd param, ValCmd value) {
        return put(param, value.toString());
    }
}