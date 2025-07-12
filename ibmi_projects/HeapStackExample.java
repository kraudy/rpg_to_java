public class HeapStackExample {
  public static void main(String[] args){
    // stack
    int x = 10;
    // S references the isntance of the String object in the heap
    String s = new String("Test");
    MyClass obj = new MyClass();

    System.out.println(x);
    System.out.println(s);
    System.out.println(obj);
    System.out.println(obj.value);
  }
}

// Stored on the heap
class MyClass{
  int value = 42;
}

/*    Output
10
Test
MyClass@493c726c
42
*/