import java.util.ArrayList;
import java.util.List;

/** float arithmetic, conversions, comparisons and formatting (exact at every word size). */
public class FloatMath {
    static float average(float[] xs) {
        float sum = 0;
        for (float x : xs) {
            sum += x;
        }
        return sum / xs.length;
    }

    public static void main(String[] args) {
        float a = 1.5f;
        float b = a * 3 - 0.25f;
        System.out.println(a + " " + b + " " + (a / b) + " " + (b % a));
        System.out.println(0.1f + 0.2f);
        System.out.println(1.0f / 3 + " " + 2.0f / 3 + " " + 1e10f + " " + 1.25e-5f);
        System.out.println(-0.0f + " " + (1 / 0.0f) + " " + (0.0f / 0.0f));
        float[] data = {3.5f, 2.25f, -1.0f, 8.0f};
        System.out.println("avg " + average(data));
        int i = (int) 7.9f;
        int j = (int) -7.9f;
        long big = (long) 1e30f;
        char c = (char) 65.7f;
        System.out.println(i + " " + j + " " + (big > 0) + " " + c);
        float f = 7;
        f += 0.5;
        f++;
        f *= 2;
        System.out.println(f + " " + (f > 16) + " " + (f == 17.0f) + " " + (f != 17));
        float nan = 0.0f / 0;
        System.out.println((nan == nan) + " " + (nan < 1) + " " + Float.isNaN(nan) + " "
                        + Float.compare(-0.0f, 0.0f));
        System.out.println(Math.round(2.5f) + " " + Math.round(-2.5f) + " " + Math.abs(-3.75f)
                        + " " + Math.max(1.5f, -2f) + " " + Math.min(-0.0f, 0.0f));
        System.out.println(Float.parseFloat("3.14159") + " " + Float.valueOf("-2.5e3"));
        List<Float> boxed = new ArrayList<>();
        for (int k = 1; k <= 4; k++) {
            boxed.add(k / 8.0f);
        }
        float total = 0;
        for (float x : boxed) {
            total += x;
        }
        System.out.println(boxed + " sum " + total);
        System.out.printf("%.2f|%8.3f|%-8.1f|%e%n", 3.14159f, -2.5f, 10f, 12345.678f);
        String s = String.format("%d items at %.2f = %.2f", 3, 1.1f, 3 * 1.1f);
        System.out.println(s + " " + "x=%.1f".formatted(0.25f));
        float x = 1;
        int steps = 0;
        while (x + 1 != x) {
            x *= 2;
            steps++;
        }
        System.out.println("float precision " + steps + " bits, " + x);
    }
}
