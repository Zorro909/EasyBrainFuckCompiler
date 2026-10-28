public class Recursion {
    static int calls;

    static int fib(int n) {
        calls++;
        return n < 2 ? n : fib(n - 1) + fib(n - 2);
    }

    static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static int ackermann(int m, int n) {
        if (m == 0) {
            return n + 1;
        }
        if (n == 0) {
            return ackermann(m - 1, 1);
        }
        return ackermann(m - 1, ackermann(m, n - 1));
    }

    static void hanoi(int n, char from, char to, char via) {
        if (n == 0) {
            return;
        }
        hanoi(n - 1, from, via, to);
        System.out.print(from + "->" + to + " ");
        hanoi(n - 1, via, to, from);
    }

    static boolean isEven(int n) {
        return n == 0 || isOdd(n - 1);
    }

    static boolean isOdd(int n) {
        return n != 0 && isEven(n - 1);
    }

    static long factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }

    static int sumDigits(int n) {
        if (n < 10) {
            return n;
        }
        return n % 10 + sumDigits(n / 10);
    }

    public static void main(String[] args) {
        System.out.println(fib(17) + " in " + calls + " calls");
        System.out.println(gcd(1071, 462) + " " + gcd(17, 5));
        System.out.println(ackermann(2, 3));
        hanoi(4, 'A', 'C', 'B');
        System.out.println();
        System.out.println(isEven(10) + " " + isOdd(7) + " " + isEven(7));
        System.out.println(factorial(12));
        System.out.println(sumDigits(987654321));
    }
}
