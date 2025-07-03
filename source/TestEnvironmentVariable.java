import com.ibm.as400.access.*;

public class TestEnvironmentVariable {
  public static void main(String... args){
    AS400 system = new AS400();
    EnvironmentVariable path = new EnvironmentVariable(system, "PATH");

    try{
      System.out.println(path.getValue());
    } catch (Exception e){
      e.printStackTrace();
    }

    system.disconnectService(AS400.COMMAND);
  }
}
