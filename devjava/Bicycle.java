
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
  int cadence = 0;
  int speed = 0;
  int gear = 1;

  public Bicycle(){
    gear = 0;
    cadence = 0;
    speed = 1;
  }

  public Bicycle(int startCadence, int startSpeed, int startGear){
    gear = startGear;
    cadence = startCadence;
    speed = startSpeed;
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

  public void printStates() {
    System.out.println("cadence:" +
        cadence + " speed:" + 
        speed + " gear:" + gear);
  }

}
