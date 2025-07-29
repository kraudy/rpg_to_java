


public class DataStructure{
  private static final int SIZE = 15;
  private int[] arrayOfInts = new int[SIZE];

  // constructor
  public DataStructure(){
    for(int i=0; i<SIZE; i++){
      arrayOfInts[i] = i;
    }
  }

  // This defines the class must implement some methods like next()
  interface DataStructureIterator extends java.util.Iterator<Integer> {}

  private class EvenIterator implements DataStructureIterator{
    private int nextIndex = 0;

    public boolean hasNext(){
      return (nextIndex < SIZE);
    }

    public Integer next(){
      Integer retValue = Integer.valueOf(arrayOfInts[nextIndex]);
      nextIndex += 2;
      return retValue;
    }

  }

  public void printEven() {
    // Print out values of even indices of the array
    DataStructureIterator iterator = this.new EvenIterator();
    while (iterator.hasNext()) {
        System.out.print(iterator.next() + " ");
    }
    System.out.println();
  }

  public static void main(String... args){
    DataStructure ds = new DataStructure();
    ds.printEven();
  }

}
