public class InnerClass{
  public static void main(String... args){
    OuterClass oc = new OuterClass();
    OuterClass.InsideClass ic = oc.new InsideClass();
  }
}

public class OuterClass{
  public int i = 0;
  public class InsideClass{
    public int j = 1;
  }
}