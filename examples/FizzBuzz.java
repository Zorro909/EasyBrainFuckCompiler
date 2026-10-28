/** The classic, written the way you would write it on the JVM. */
public class FizzBuzz {
    public static void main(String[] args) {
        for (int i = 1; i <= 30; i++) {
            String s = "";
            if (i % 3 == 0) {
                s += "Fizz";
            }
            if (i % 5 == 0) {
                s += "Buzz";
            }
            System.out.println(s.isEmpty() ? String.valueOf(i) : s);
        }
    }
}
