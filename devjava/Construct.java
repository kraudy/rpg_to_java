public class Construct{

  public static void main(String[] args){
    Point a = new Point();
    a.Set_x(10.0); 
    a.Set_y(25.7);
  }

}

class Point {
  protected double x;
  protected double y;

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

class ThreePoint extends Point{
  protected double z;
  ThreePoint (){
    x = 0.0;
    y = 0.0;
    z = 0.0;
  }
  ThreePoint (double x, double y, double z){
    this.x = x;
    this.y = y;
    this.z = z;
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