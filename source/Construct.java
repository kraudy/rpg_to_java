public class Construct{

  public static void main(String[] args){
    Point a = new Point(3.0, 5.0);
    System.out.println(a.x);
  }

}

class Point {
  public double x;
  public double y;

  Point(){
    x = 0.0;
    y = 0.0;
  }

  Point(double x, double y){
    this.x = x;
    this.y = y;
  }


}