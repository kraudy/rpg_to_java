import java.util.Scanner;
import java.util.regex.MatchResult;

public class ScannerExample {
  public static void main(String... args){
    String text = """
                Longing rusted furnace
                daybreak 17 benign 
                9 homecoming 1 
                freight car
                """;

    try(Scanner scanner = new Scanner(text)){
      scanner.findAll("benign").map(MatchResult::group).forEach(System.out::println);
    }
  }
}
