package com.example;

import com.ibm.as400.access.*;
import com.ibm.as400.resource.*;

/*
  This is deprecated. The docs suggest using ISeriesNetServer.java
*/

public class JavaNetServer {
  public static void main(String... args){
    AS400 system = new AS400();
    NetServer ns = new NetServer(system);

    try{
       // Get the name of the NetServer.
      System.out.println("Name: " + (String)ns.getAttributeValue(NetServer.NAME));

      // Get the CCSID of the NetServer.
      System.out.println("CCSID: " + ((Integer)ns.getAttributeValue(NetServer.CCSID)).intValue());
   
      // Get the pending CCSID of the NetServer.
      System.out.println("Pending CCSID: " + ((Integer)ns.getAttributeValue(NetServer.CCSID_PENDING)).intValue());
   
      // Get the "allow system name" value of the NetServer.
      System.out.println("'Allow system name': " + ((Boolean)ns.getAttributeValue(NetServer.ALLOW_SYSTEM_NAME)).booleanValue());  
   
      // No authority
      //changeAttributes(ns);

      // Print all the attribute values of the NetServer object.
      ResourceMetaData[] attributeMetaData = ns.getAttributeMetaData();
      for(int i = 0; i < attributeMetaData.length; i++){
        Object attributeID = attributeMetaData[i].getID();
        Object value = ns.getAttributeValue(attributeID); 
        System.out.println("Attribute " + attributeID + " = " + value);
      }
    }
    catch (ResourceException e) {
      e.printStackTrace();
    }
    finally {
      if (system != null) system.disconnectAllServices();
    }
  }

  private static void changeAttributes(NetServer ns) throws ResourceException{
    // Set the (pending) description of the NetServer.
    // Note: Changes to "pending" attributes take effect after the NetServer
    // is ended and restarted.
    ns.setAttributeValue(NetServer.DESCRIPTION_PENDING, "The NetServer");
    ns.commitAttributeChanges();
  
    // Set the (pending) CCSID of the NetServer to 13488.
    ns.setAttributeValue(NetServer.CCSID_PENDING, new Integer(13488));
  
    // Set the (pending) "allow system name" value of the NetServer to true.
    ns.setAttributeValue(NetServer.ALLOW_SYSTEM_NAME_PENDING, Boolean.TRUE);
  
    // Commit the attribute changes (send them to the system).
    ns.commitAttributeChanges();
}
}
