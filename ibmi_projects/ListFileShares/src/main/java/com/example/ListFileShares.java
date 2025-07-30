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

  private static void listFileShares(ISeriesNetServer ns) throws 
      AS400Exception, AS400SecurityException, ErrorCompletingRequestException, 
      InterruptedException, IOException, ObjectDoesNotExistException {
    System.out.println("\nExisting File Shares:");
    System.out.println("---------------------");

    // Retrieve the list of file shares.
    ISeriesNetServerFileShare[] shares = ns.listFileShares();

    if (shares.length == 0) {
        System.out.println("No file shares found.");
        return;
    }

    // Iterate through the file shares and print details.
    for (ISeriesNetServerFileShare share : shares) {
      System.out.println("Share Name: " + share.getName());
      System.out.println("Path: " + share.getPath());
      System.out.println("Description: " + share.getDescription());
      System.out.println("Read-Only: " + share.READ_ONLY);
      System.out.println("Maximum Users: " + (share.getMaximumNumberOfUsers() == -1 ? "Unlimited" : share.getMaximumNumberOfUsers()));
      System.out.println("Current Users: " + share.getCurrentNumberOfUsers());
      System.out.println("---------------------");
    }
  }
}
