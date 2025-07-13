package com.example;

import java.beans.PropertyVetoException;
import java.io.IOException;

import com.ibm.as400.access.*;

public class GetEnvVar {
  public static void main(String... args ){
    AS400 system = new AS400();
    EnvironmentVariable path = new EnvironmentVariable(system, "PATH");
    CommandCall cmd = new CommandCall(system);

    try{
      System.out.println(path.getValue());
    } catch (ObjectDoesNotExistException e) {
      // Try adding the env var here with the cmd object
      System.out.println("Environment variable 'PATH' does not exist on the system.");
      try {
        if(!cmd.run("ADDENVVAR ENVVAR(PATH) VALUE('/home/robrkaudy') LEVEL(*JOB)")){
          System.out.println("Could not add envvar PATH to job");
        }
      } catch (PropertyVetoException | AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException e2) {
        e2.printStackTrace();
      }

      try {
        System.out.println(path.getValue());
      }catch (AS400SecurityException | ErrorCompletingRequestException | InterruptedException | IOException | ObjectDoesNotExistException e3) {
        e3.printStackTrace();
      }  
    } catch (AS400SecurityException e4){
      System.out.println("Security exception");
    } catch (Exception e){
      e.printStackTrace();
    }

    system.disconnectService(AS400.COMMAND);
    }
}
