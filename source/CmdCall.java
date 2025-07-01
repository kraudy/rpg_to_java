import com.ibm.as400.access.*; 

public class CmdCall {
  public static void main(String... args){
    // Like other Java classes, IBM Toolbox for Java classes
    // throw exceptions when something goes wrong. These must
    // be caught by programs that use IBM Toolbox for Java.
    AS400 system = new AS400();
    CommandCall command = new CommandCall(system);
    try{
      if(!command.run("CHGCURLIB CURLIB(ROBKRAUDY2)")){
        System.out.println("Command failed!");
      }
      
      AS400Message[] messageList = command.getMessageList(); 
      for (int i=0; i<messageList.length; i++){
        // Show messages
        System.out.println(messageList[i].getText());
        // Load additional information
        messageList[i].load();
        // show help
        System.out.println(messageList[i].getHelp());
      }
    }
    catch (Exception e){
      System.out.println("Command " + command.getCommand() + " issued an exception!");
      e.printStackTrace();
    }
  
    system.disconnectService(AS400.COMMAND);
  }
   
}
