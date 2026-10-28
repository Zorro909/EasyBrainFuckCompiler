public class DynamicProgramming {
    static int lcs(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1) ? dp[i - 1][j - 1] + 1
                                : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[a.length()][b.length()];
    }

    static int knapsack(int[] w, int[] v, int cap) {
        int[] best = new int[cap + 1];
        for (int i = 0; i < w.length; i++) {
            for (int c = cap; c >= w[i]; c--) {
                best[c] = Math.max(best[c], best[c - w[i]] + v[i]);
            }
        }
        return best[cap];
    }

    static long[] memo = new long[60];

    static long fibMemo(int n) {
        if (n < 2) return n;
        if (memo[n] != 0) return memo[n];
        return memo[n] = fibMemo(n - 1) + fibMemo(n - 2);
    }

    public static void main(String[] args) {
        System.out.println(lcs("AGGTAB", "GXTXAYB") + " " + lcs("brainfuck", "javafunc"));
        System.out.println(knapsack(new int[] {1, 3, 4, 5}, new int[] {1, 4, 5, 7}, 7));
        System.out.println(fibMemo(40) + " " + fibMemo(45));
        int[] coins = {1, 5, 10, 25};
        int[] ways = new int[101];
        ways[0] = 1;
        for (int coin : coins)
            for (int s = coin; s <= 100; s++)
                ways[s] += ways[s - coin];
        System.out.println("ways to make 100: " + ways[100]);
    }
}
