import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Reads one number per line from standard input and prints summary statistics. Lines that
 * are not numbers are reported and skipped.
 */
public class Statistics {
    static double mean(List<Double> xs) {
        double sum = 0;
        for (double x : xs) {
            sum += x;
        }
        return sum / xs.size();
    }

    static double median(List<Double> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    static double stddev(List<Double> xs, double mean) {
        double sq = 0;
        for (double x : xs) {
            sq += (x - mean) * (x - mean);
        }
        return Math.sqrt(sq / xs.size());
    }

    public static void main(String[] args) {
        List<Double> values = new ArrayList<>();
        Scanner in = new Scanner(System.in);
        int line = 0;
        while (in.hasNextLine()) {
            String text = in.nextLine().trim();
            line++;
            if (text.isEmpty()) {
                continue;
            }
            try {
                values.add(Double.parseDouble(text));
            } catch (NumberFormatException e) {
                System.out.println("line " + line + ": not a number: " + text);
            }
        }
        if (values.isEmpty()) {
            System.out.println("no numbers");
            return;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double m = mean(values);
        System.out.printf("count  %d%n", values.size());
        System.out.printf("min    %.3f%n", sorted.get(0));
        System.out.printf("max    %.3f%n", sorted.get(sorted.size() - 1));
        System.out.printf("mean   %.3f%n", m);
        System.out.printf("median %.3f%n", median(sorted));
        System.out.printf("stddev %.3f%n", stddev(values, m));
    }
}
