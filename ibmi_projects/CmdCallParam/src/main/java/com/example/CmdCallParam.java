package com.example;

import java.io.*;
import java.util.*;
import com.ibm.as400.access.*;

public class CmdCallParam {
  public static void main( String... args ){
    BufferedReader inputStream = new BufferedReader(new InputStreamReader(System.in),1);
    AS400 system = new AS400();

    String commandStr = null;

    try{
      System.out.println("Command to execute: ");
      commandStr = inputStream.readLine();

      CommandCall cmd = new CommandCall(system);
      if(!cmd.run(commandStr)){
        System.out.println("Could not execute command");
        return;
      }

      AS400Message[] messages = cmd.getMessageList();
      if(messages.length > 0){
        for(AS400Message message: messages){
          System.out.print ( message.getID() );
          System.out.print ( ": " );
          System.out.println( message.getText() );
        }
      }
    } catch (Exception e){
      e.printStackTrace();
    } finally {
      if(system != null) system.disconnectAllServices();
    }

  }
}
