
public class NestedClass{
  public String outerField = "Instanced Outer field";
  public static String staticOuterFiled = "Static outer field"; 

  /* This class needs to be instantiaded */
  class InnerClass{
    void accessMembers(){
      System.out.println(outerField);
      System.out.println(staticOuterFiled);
    }
  }

  public static void main(String... args){
    System.out.println("Inner class:");
    System.out.println("------------");
    NestedClass outerObject = new NestedClass(); 
    NestedClass.InnerClass innerObject = outerObject.new InnerClass();
    innerObject.accessMembers();
  }
}