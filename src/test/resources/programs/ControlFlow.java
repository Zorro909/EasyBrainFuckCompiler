public class ControlFlow {
    static String classify(int n) {
        switch (n % 4) {
            case 0:
                return "zero";
            case 1:
            case 2:
                return "small";
            default:
                return "big";
        }
    }

    static int days(String month) {
        return switch (month) {
            case "feb" -> 28;
            case "apr", "jun", "sep", "nov" -> 30;
            default -> {
                int d = 31;
                yield d;
            }
        };
    }

    public static void main(String[] args) {
        outer:
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (j == 3) {
                    continue outer;
                }
                if (i == 3) {
                    break outer;
                }
                System.out.print(i * 10 + j + " ");
            }
        }
        System.out.println();
        int k = 0;
        do {
            k += 3;
        } while (k < 20);
        System.out.println(k);
        int n = 27;
        int steps = 0;
        while (n != 1) {
            n = n % 2 == 0 ? n / 2 : 3 * n + 1;
            steps++;
        }
        System.out.println("collatz steps: " + steps);
        for (int i = 0; i < 6; i++) {
            System.out.print(classify(i) + ",");
        }
        System.out.println();
        System.out.println(days("feb") + days("jun") + days("jan"));
        int count = 0;
        for (int i = 0; i < 100; i++) {
            if (i % 3 == 0 || i % 5 == 0) {
                if (i % 2 == 0 && !(i > 50)) {
                    count++;
                }
            }
        }
        System.out.println(count);
        char grade = 'B';
        switch (grade) {
            case 'A' -> System.out.println("excellent");
            case 'B', 'C' -> System.out.println("good");
            default -> System.out.println("hmm");
        }
        int fallthrough = 0;
        switch (2) {
            case 1:
                fallthrough += 1;
            case 2:
                fallthrough += 2;
            case 3:
                fallthrough += 3;
                break;
            case 4:
                fallthrough += 4;
        }
        System.out.println(fallthrough);
        for (int i = 0, j = 10; i < j; i += 2, j--) {
            System.out.print(i + ":" + j + " ");
        }
        System.out.println();
        block:
        {
            if (count > 5) {
                break block;
            }
            System.out.println("not printed");
        }
        System.out.println("done");
    }
}
