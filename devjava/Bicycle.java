
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

public class Bicycle {
  /* Class states */
  int cadence = 0;
  int speed = 0;
  int gear = 1;

  /* Methods to change the object's states */
  void changeCadence(int newValue){
    cadence = newValue;
  }

  void changeGear(int newValue){
    gear = newValue;
  }
  
  void speedUp(int increment){
    speed += increment; 
  }

  void printStates() {
    System.out.println("cadence:" +
        cadence + " speed:" + 
        speed + " gear:" + gear);
  }

}
