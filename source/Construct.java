public class Construct{

  public static void main(String[] args){
    Recatangle rect = new Recatangle();
    System.out.println(rect.lowerLeft.x);
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

class Recatangle {
  public Point lowerLeft;
  public Point upperRight;

  Recatangle(){
    lowerLeft   = new Point();
    upperRight  = new Point();
  }

}