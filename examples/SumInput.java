import de.zorro909.brainfuck.transpiler.Bf;

public class SumInput {

    public static void main(String[] args) {
        int a = Bf.readInt();
        int b = Bf.readInt();
        System.out.print(a);
        System.out.print(" + ");
        System.out.print(b);
        System.out.print(" = ");
        System.out.println(a + b);
    }
}
