package com.example;

import com.ibm.as400.access.*;

import java.io.IOException;

public class ListFileShares {
  public static void main( String[] args ){
    AS400 system = new AS400();
    ISeriesNetServer ns = new ISeriesNetServer(system);

    try {
      // Get the name of the NetServer.
      System.out.println("Name: " + ns.getName());

      // Get the CCSID of the NetServer.
      System.out.println("CCSID: " + ns.getCCSID());

      // Get the "allow system name" value of the NetServer.
      System.out.println("'Allow system name': " + ns.isAllowSystemName());

      // List all existing file shares.
      listFileShares(ns);

    } catch (AS400Exception e) {
        e.printStackTrace();
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        // Disconnect all services
        if (system != null) {
            system.disconnectAllServices();
        }
    }
  }

  
}
