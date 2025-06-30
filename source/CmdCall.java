import com.ibm.as400.access.*; 

public class CmdCall {
  public static void main(String... args){
    // Like other Java classes, IBM Toolbox for Java classes
    // throw exceptions when something goes wrong. These must
    // be caught by programs that use IBM Toolbox for Java.
    try{
      AS400 system = new AS400();
      CommandCall cc = new CommandCall(system);
    
      //cc.run("CRTLIB MYLIB");
      //cc.run("DSPLIBL");
      cc.run("CHGCURLIB CURLIB(ROBKRAUDY2)");
      cc.run("CRTSRCPF FILE(ROBKRAUDY2/JAVASRC)");
      AS400Message[] ml = cc.getMessageList(); 
      for (int i=0; i<ml.length; i++){
        System.out.println(ml[i].getText());
      }
    }
    catch (Exception e){
      e.printStackTrace();
    }
  
    System.exit(0);
  }
   
}
