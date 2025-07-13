package com.example;

import com.ibm.as400.access.*;

public class ReadDataQueue 
{
    public static void main(String... args){
    AS400 system = new AS400();

    try {
      // ObjectDoesNotExistException
      DataQueue dq = new DataQueue(system, "/QSYS.LIB/ROBKRAUDY2.LIB/MYQUEUE.DTAQ");

      DataQueueEntry dqData = dq.read();
      byte[] data = dqData.getData();
    }catch (Exception e){
      e.printStackTrace();
    }

    system.disconnectService(AS400.DATAQUEUE);
  }
}
