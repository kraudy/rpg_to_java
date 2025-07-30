package com.example;

import com.ibm.as400.access.*;

public class JavaISeriesNetServer {
  public static void main( String[] args ){
    // Create a NetServer object for a specific system.
  AS400 system = new AS400();
  ISeriesNetServer ns = new ISeriesNetServer(system);
 
  try{
    // Get the name of the NetServer.
    System.out.println("Name: " + ns.getName());
 
    // Get the CCSID of the NetServer.
    System.out.println("CCSID: " + ns.getCCSID());
 
    // Get the "allow system name" value of the NetServer.
    System.out.println("'Allow system name': " +  ns.isAllowSystemName());

    //changeAttributes(ns);
 
  }
  catch (AS400Exception e) {
    AS400Message[] messageList = e.getAS400MessageList();
    for (int i=0; <messageList.length; i++) {
      System.out.println(messageList[i].getText());
    }
  }
  catch (Exception e) {
    e.printStackTrace();
  }
  finally {
    if (system != null) system.disconnectAllServices();
  }
  }

  private static void changeAttributes(ISeriesNetServer ns) throws AS400Exception{
    // Set the description of the NetServer.
    // Note: Changes will take effect after next start of NetServer.
    ns.setDescription("The NetServer");
    ns.commitChanges();
 
    // Set the CCSID of the NetServer to 13488.
    ns.setCCSID(13488);
 
    // Set the "allow system name" value of the NetServer to true.
    ns.setAllowSystemName(true);
 
    // Commit the attribute changes (send them to the system).
    ns.commitChanges();
  }
}
