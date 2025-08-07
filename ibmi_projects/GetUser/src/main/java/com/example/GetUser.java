package com.example;

import com.ibm.as400.access.*;

public class GetUser {
  public static void main(String... args){
    AS400 system = new AS400();

    try{
      // Get current user
      User user = new User(system, system.getUserId());
      System.out.println("User dir: " + user.getHomeDirectory());

    } catch (Exception e){
      e.printStackTrace();
    } finally {
      if (system != null) system.disconnectAllServices();
    }
  }
}
