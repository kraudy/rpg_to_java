
public class BicycleDemo {
  public static void main(String... args){
    Bicycle bike1 = new Bicycle();
    Bicycle bike2 = new Bicycle();
    bike1.changeCadence(50);
    bike1.speedUp(10);
    bike1.changeGear(2);
    bike1.printStates();

    bike2.changeCadence(30);
    bike2.speedUp(20);
    bike2.changeGear(3);
    bike2.printStates();

    System.out.println("Total of bikes: " + Bicycle.getNumberOfBikes());
  }
}

/* This is like a prototype that allows some standarization among classes */
interface BikeInter {
  void changeCadence(int newValue);
  void changeGear(int newValue);
  void speedUp(int increment);
}

public class Bicycle implements BikeInter{
  /* Class states */
  private int cadence = 0;
  private int speed = 0;
  private int gear = 1;
  private int id;
  private static int numberOfBikes = 0;
  private static final int maxBikes = 10;

  public static int getNumberOfBikes(){
    return numberOfBikes;
  }

  public Bicycle(){
    this(0,0,1);
  }

  public Bicycle(int startCadence, int startSpeed, int startGear){
    this.cadence = startCadence;
    this.speed = startSpeed;
    this.gear = startGear;
    this.id = ++numberOfBikes;
  }

  /* Methods to change the object's states */
  public void changeCadence(int newValue){
    cadence = newValue;
  }

  public void changeGear(int newValue){
    gear = newValue;
  }
  
  public void speedUp(int increment){
    speed += increment; 
  }

  public int getId(){
    return this.id;
  }

  public void printStates() {
    System.out.println("cadence:" +
        cadence + " speed:" + 
        speed + " gear:" + gear +
        " id: " + this.getId() + " out of max: " + maxBikes);
  }

}
