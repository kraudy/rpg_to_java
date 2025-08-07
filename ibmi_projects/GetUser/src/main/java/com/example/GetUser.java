package com.example;

import com.ibm.as400.access.*;

public class GetUser {
  public static void main(String... args){
    AS400 system = new AS400();

    try{
      // Get current user
      User user = new User(system, system.getUserId());

      // Get user's home dir
      System.out.println("User's dir: " + user.getHomeDirectory());

      // Retrieve the current user library
      System.out.println("User's library: " + user.getCurrentLibraryName());

    } catch (Exception e){
      e.printStackTrace();
    } finally {
      if (system != null) system.disconnectAllServices();
    }
  }
}
