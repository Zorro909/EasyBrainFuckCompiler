import java.util.*;
import java.util.function.*;

public class Lambdas {
    interface Op {
        int apply(int a, int b);
    }

    static int fold(int[] xs, int start, Op op) {
        int acc = start;
        for (int x : xs) {
            acc = op.apply(acc, x);
        }
        return acc;
    }

    static int twice(IntUnaryOperator f, int x) {
        return f.applyAsInt(f.applyAsInt(x));
    }

    static int square(int x) {
        return x * x;
    }

    private int base = 100;

    int addBase(int x) {
        Function<Integer, Integer> f = y -> y + base;
        return f.apply(x);
    }

    public static void main(String[] args) {
        int[] xs = {1, 2, 3, 4, 5};
        System.out.println(fold(xs, 0, (a, b) -> a + b) + " " + fold(xs, 1, (a, b) -> a * b));
        int offset = 7;
        System.out.println(twice(x -> x + offset, 1) + " " + twice(Lambdas::square, 3));
        Function<String, Integer> len = String::length;
        Supplier<List<String>> make = ArrayList::new;
        List<String> list = make.get();
        list.add("lambda");
        list.add("in");
        list.add("brainfuck");
        int total = 0;
        for (String s : list) {
            total += len.apply(s);
        }
        System.out.println(total);
        list.sort((a, b) -> a.length() - b.length());
        System.out.println(list);
        Predicate<Integer> even = n -> n % 2 == 0;
        BiFunction<Integer, Integer, Integer> max = (a, b) -> a > b ? a : b;
        System.out.println(even.test(4) + " " + even.test(5) + " " + max.apply(3, 9));
        Runnable r = () -> System.out.println("run!");
        r.run();
        Comparator<String> byLast = new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.charAt(a.length() - 1) - b.charAt(b.length() - 1);
            }
        };
        list.sort(byLast);
        System.out.println(list);
        list.sort(byLast.reversed());
        System.out.println(list);
        System.out.println(new Lambdas().addBase(5));
        Consumer<String> printer = System.out::println;
        printer.accept("consumed");
        Map<String, Integer> counts = new HashMap<>();
        for (String w : "a b a c b a".split(" ")) {
            counts.merge(w, 1, Integer::sum);
        }
        System.out.println(counts);
    }
}
