public class Primes {
    public static void main(String[] args) {
        int n = 500;
        boolean[] composite = new boolean[n + 1];
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i <= n; i++) {
            if (!composite[i]) {
                count++;
                if (i < 60) {
                    sb.append(i).append(' ');
                }
                for (int j = i * i; j <= n; j += i) {
                    composite[j] = true;
                }
            }
        }
        System.out.println(sb.toString().trim());
        System.out.println(count + " primes below " + n);
    }
}
