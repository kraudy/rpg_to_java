public class Construct{

  public static void main(String[] args){
    Recatangle rect = new Recatangle();
    System.out.println(rect.lowerLeft.x);
  }

}

class Point {
  private double x;
  private double y;

  Point(){
    x = 0.0;
    y = 0.0;
  }

  Point(double x, double y){
    this.x = x;
    this.y = y;
  }

  public void Set_x(double x){
    this.x = x;
  }

  public void Set_y(double y){
    this.y = y;
  }

  public double Get_x(){
    return x;
  }

  public double Get_y(){
    return y;
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