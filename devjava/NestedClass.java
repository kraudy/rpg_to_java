
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

  static class StaticNestedClass{
    void accessMembers(NestedClass outer){
      System.out.println(outer.outerField);
      System.out.println(staticOuterFiled);
    }
  }

  public static void main(String... args){
    System.out.println("Inner class:");
    System.out.println("------------");
    NestedClass outerObject = new NestedClass(); 
    NestedClass.InnerClass innerObject = outerObject.new InnerClass();
    innerObject.accessMembers();

    System.out.println("\nStatic nested class:");
    System.out.println("--------------------");
    StaticNestedClass staticNestedObject = new StaticNestedClass();
    staticNestedObject.accessMembers(outerObject);

    System.out.println("\nTop-level class:");
    System.out.println("--------------------");
    TopLevelClass topLevelObject = new TopLevelClass();
    topLevelObject.accessMembers(outerObject);

  }
}

public class TopLevelClass {
  void accessMembers(NestedClass outer) {
      System.out.println(outer.outerField);
      System.out.println(NestedClass.staticOuterFiled);
  }
}