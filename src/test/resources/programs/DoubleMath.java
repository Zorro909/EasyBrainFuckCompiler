// word-bytes: 8
import java.util.ArrayList;
import java.util.List;

/** double arithmetic, conversions, formatting and Math (run with 8-byte words: exact binary64). */
public class DoubleMath {
    record Stats(double mean, double stddev) {
    }

    static Stats stats(List<Double> xs) {
        double sum = 0;
        for (double x : xs) {
            sum += x;
        }
        double mean = sum / xs.size();
        double sq = 0;
        for (double x : xs) {
            sq += (x - mean) * (x - mean);
        }
        return new Stats(mean, Math.sqrt(sq / xs.size()));
    }

    static double newtonSqrt(double v) {
        double x = v;
        for (int i = 0; i < 30; i++) {
            x = (x + v / x) / 2;
        }
        return x;
    }

    public static void main(String[] args) {
        System.out.println(0.1 + 0.2);
        System.out.println(1.0 / 3 + " " + 2.0 / 3 + " " + 100.0 / 7);
        System.out.println(1e21 + " " + 1e-7 + " " + 123456789.0 + " " + 0.001 + " " + 1234567.0);
        System.out.println(Double.MAX_VALUE + " " + Double.MIN_VALUE + " " + (-Double.MAX_VALUE * 2));
        System.out.println(Math.sqrt(2) + " " + newtonSqrt(2) + " " + Math.sqrt(-1));
        System.out.println(Math.floor(-2.5) + " " + Math.ceil(-2.5) + " " + Math.rint(2.5) + " "
                        + Math.round(-2.5) + " " + Math.round(2.4999999999999996));
        System.out.println(Math.pow(2, 10) + " " + Math.pow(1.5, 3) + " " + Math.pow(2, -3)
                        + " " + (long) Math.pow(10, 15));
        long big = 1L << 53;
        double d = big + 1;
        System.out.println(big + " " + (long) d + " " + (long) 3.99e9 + " " + (long) -1e30);
        float f = (float) 0.1;
        double back = f;
        System.out.println(f + " " + back + " " + (float) 1e40 + " " + (double) 1.1f);
        List<Double> xs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            xs.add(i * 1.5);
        }
        Stats s = stats(xs);
        System.out.println(s);
        System.out.printf("mean=%.3f sd=%.4f%n", s.mean(), s.stddev());
        System.out.println(String.format("%10.2f|%-10.2f|%.0f|%e", Math.PI, -Math.E, 2.5, 6.02214076e23));
        System.out.println(Double.parseDouble("1e-5") + " " + Double.valueOf("  -12.5  ")
                        + " " + Double.compare(0.0, -0.0) + " " + Double.isInfinite(1 / 0.0));
        try {
            Double.parseDouble("abc");
        } catch (NumberFormatException e) {
            System.out.println("bad: " + e.getMessage());
        }
        double h = 0;
        for (int n = 1; n <= 1000; n++) {
            h += 1.0 / n;
        }
        System.out.println("H(1000) = " + h);
        System.out.printf("sin %.6f cos %.6f tan %.6f atan2 %.6f%n", Math.sin(1), Math.cos(1),
                        Math.tan(0.5), Math.atan2(-1, -1));
        System.out.printf("exp %.6f log %.6f log10 %.6f cbrt %.6f%n", Math.exp(2.5),
                        Math.log(10), Math.log10(2), Math.cbrt(-27));
        System.out.println(Math.log10(1000) + " " + Math.exp(0) + " " + Math.log(1));
        Object o = 2.5;
        if (o instanceof Double dd && dd > 2) {
            System.out.println("boxed " + dd + " " + dd.intValue() + " " + o.equals(2.5));
        }
        double celsius = 36.6;
        System.out.println(celsius + "C = " + (celsius * 9 / 5 + 32) + "F");
    }
}
