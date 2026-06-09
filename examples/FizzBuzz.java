public class FizzBuzz {

    public static void main(String[] args) {
        for (int i = 1; i <= 30; i++) {
            int byThree = i % 3;
            int byFive = i % 5;
            if (byThree == 0 && byFive == 0) {
                System.out.println("FizzBuzz");
            } else if (byThree == 0) {
                System.out.println("Fizz");
            } else if (byFive == 0) {
                System.out.println("Buzz");
            } else {
                System.out.println(i);
            }
        }
    }
}
