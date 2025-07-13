import java.util.stream.Collectors;
import java.util.*;

public class Concatenate {
  public static void main (String... args){
    if (args.length < 2){
      System.out.println("2 Arguments are needed. " + args.length + " were provided");
      throw new UnsupportedOperationException("2 Lists as arguments are needed");
    }

    List<Integer> firstSeries = List.of(args[0].split(",")).stream()
      .map(Integer::valueOf).collect(Collectors.toList());

    List<Integer> secondSeries = List.of(args[1].split(",")).stream()
              .map(Integer::valueOf).collect(Collectors.toList());

    List<Integer> elements = extractCommonElements(firstSeries, secondSeries);

    System.out.println(elements);
  }

  public static List<Integer> extractCommonElements(List<Integer> list1, List<Integer> list2){
    Set<Integer> intersection = new HashSet<>(list1);

    intersection.retainAll(list2);
    if (list1.getFirst().equals(list2.getFirst())){
      intersection.add(list1.getFirst());
    }
    if (list1.getLast().equals(list2.getLast())){
      intersection.add(list1.getLast());
    }

    return intersection.stream().toList();

  }

}
