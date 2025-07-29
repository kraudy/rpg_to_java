package com.example;

import com.ibm.as400.access.*;
import com.ibm.as400.resource.*;

public class JavaNetServer {
  public static void main(String... args){
    AS400 system = new AS400();
    NetServer ns = new NetServer(system);

    try{
       // Get the name of the NetServer.
      System.out.println("Name: " + (String)ns.getAttributeValue(NetServer.NAME));

    }
    catch (ResourceException e) {
      e.printStackTrace();
    }
    finally {
      if (system != null) system.disconnectAllServices();
    }
  }
}
