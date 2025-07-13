package com.example;

import com.ibm.as400.access.*;

public class CallRPGProgram {
  public static void main(String... args){
    AS400 system = new AS400();
    ProgramCall pgm = new ProgramCall(system);
    pgm.setThreadSafe(true);  // Indicates that the program is to be run on-thread.

    String jobNumber = "";
    try{
      jobNumber = pgm.getServerJob().getNumber();
    }catch(Exception e){
      e.printStackTrace();
    }

    System.out.println("Job number: " + jobNumber);
  }
}
