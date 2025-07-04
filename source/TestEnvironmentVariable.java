import com.ibm.as400.access.*;

public class TestEnvironmentVariable {
  public static void main(String... args){
    AS400 system = new AS400();
    EnvironmentVariable path = new EnvironmentVariable(system, "PATH");
    CommandCall cmd = new CommandCall(system);

    try{
      System.out.println(path.getValue());
    } catch (ObjectDoesNotExistException e) {
      // Try adding the env var here with the cmd object
      System.out.println("Environment variable 'PATH' does not exist on the system.");
      if(!cmd.run("ADDENVVAR ENVVAR(PATH) VALUE('/home/robrkaudy') LEVEL(*JOB)")){
        System.out.println("Could not add envvar PATH to job");
      }
      System.out.println(path.getValue());
    }catch (Exception e){
      e.printStackTrace();
    }

    system.disconnectService(AS400.COMMAND);
  }
}
