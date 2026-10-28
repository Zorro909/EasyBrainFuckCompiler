public class GameOfLife {
    public static void main(String[] args) {
        int h = 8, w = 10;
        boolean[][] g = new boolean[h][w];
        g[1][2] = g[2][3] = g[3][1] = g[3][2] = g[3][3] = true;
        for (int gen = 0; gen < 4; gen++) {
            StringBuilder sb = new StringBuilder();
            for (boolean[] row : g) {
                for (boolean cell : row) {
                    sb.append(cell ? '#' : '.');
                }
                sb.append('\n');
            }
            System.out.print(sb);
            System.out.println("--");
            boolean[][] next = new boolean[h][w];
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    int n = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            int rr = (r + dr + h) % h, cc = (c + dc + w) % w;
                            if (g[rr][cc]) n++;
                        }
                    }
                    next[r][c] = n == 3 || n == 2 && g[r][c];
                }
            }
            g = next;
        }
    }
}
