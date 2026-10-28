import java.util.Scanner;

public class InputSum {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int n = in.nextInt();
        long sum = 0;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int v = in.nextInt();
            sum += v;
            max = Math.max(max, v);
        }
        String name = in.next();
        in.nextLine();
        String line = in.nextLine();
        System.out.println("Hello " + name + ", sum=" + sum + " max=" + max);
        System.out.println("line: [" + line + "]");
        while (in.hasNextInt()) {
            System.out.print(in.nextInt() * 2 + " ");
        }
        System.out.println();
    }
}
