public class ReverseString {
  public static void main(String... args) {
  int i, len = args[0].length();
  StringBuffer dest = new StringBuffer(len);
  
  for (i = (len - 1); i >= 0; i--) {
    dest.append(args[0].charAt(i));
  }

  System.out.println(dest.toString());
  }
}