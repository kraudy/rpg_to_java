package com.example;

import com.ibm.as400.access.*;

import java.io.IOException;

public class CheckFileShare {
  public static void main( String... args ){
    AS400 system = null;
    
    if (args.length < 1) {
      System.out.println("You must specify the file share name");
      return;
    }

    String shareName = args[0];
 
    try{
      system = new AS400();

      ISeriesNetServer ns = new ISeriesNetServer(system);

      // check net server status

      //isWINSServer()
      //getWINSPrimaryAddress

      if (!ns.isStarted()) {
        System.out.println("Net server is not active");
        return;
      }

      Object shareFile = checkShare(ns, shareName);
      if(shareFile == null){
        System.out.println("Share file not found");
        return;
      }
    } catch (AS400Exception e) {
        e.printStackTrace();
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
      if (system != null) system.disconnectAllServices();
    }

  }

  
}
