import de.zorro909.brainfuck.transpiler.Bf;

public class Countdown {

    public static void main(String[] args) {
        int start = Bf.readInt();
        while (start > 0) {
            System.out.println(start);
            start--;
        }
        System.out.println("Liftoff!");
    }
}
