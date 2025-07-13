package com.example;

import com.ibm.as400.access.*;

public class GetJobData {
  public static void main(String... args){
    AS400 system = new AS400();
    Job job = new Job(system);

    System.out.println("Qualified name: " + job.getQualifiedJobName());
  }
}
