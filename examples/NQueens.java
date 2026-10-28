public class NQueens {
    static int solutions = 0;
    static int[] cols;

    static boolean safe(int row, int col) {
        for (int r = 0; r < row; r++) {
            int c = cols[r];
            if (c == col || Math.abs(c - col) == row - r) {
                return false;
            }
        }
        return true;
    }

    static void solve(int row, int n) {
        if (row == n) {
            solutions++;
            if (solutions == 1) {
                for (int r = 0; r < n; r++) {
                    StringBuilder line = new StringBuilder();
                    for (int c = 0; c < n; c++) {
                        line.append(cols[r] == c ? 'Q' : '.');
                    }
                    System.out.println(line);
                }
            }
            return;
        }
        for (int c = 0; c < n; c++) {
            if (safe(row, c)) {
                cols[row] = c;
                solve(row + 1, n);
            }
        }
    }

    public static void main(String[] args) {
        int n = 6;
        cols = new int[n];
        solve(0, n);
        System.out.println(solutions + " solutions");
    }
}
