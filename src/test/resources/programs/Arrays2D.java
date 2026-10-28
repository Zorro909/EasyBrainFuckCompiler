import java.util.Arrays;

public class Arrays2D {
    static void quicksort(int[] a, int lo, int hi) {
        if (lo >= hi) {
            return;
        }
        int p = a[(lo + hi) / 2];
        int i = lo, j = hi;
        while (i <= j) {
            while (a[i] < p) i++;
            while (a[j] > p) j--;
            if (i <= j) {
                int t = a[i];
                a[i] = a[j];
                a[j] = t;
                i++;
                j--;
            }
        }
        quicksort(a, lo, j);
        quicksort(a, i, hi);
    }

    public static void main(String[] args) {
        int[] data = new int[30];
        int seed = 12345;
        for (int i = 0; i < data.length; i++) {
            seed = seed * 1103515245 + 12345;
            data[i] = (seed >>> 16) % 1000;
        }
        int[] copy = data.clone();
        quicksort(data, 0, data.length - 1);
        System.out.println(Arrays.toString(data));
        Arrays.sort(copy);
        System.out.println(Arrays.equals(data, copy));
        int[][] grid = new int[4][5];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                grid[r][c] = r * c;
            }
        }
        int total = 0;
        for (int[] row : grid) {
            for (int v : row) {
                total += v;
            }
        }
        System.out.println(total + " " + grid[3][4] + " " + grid.length + " " + grid[0].length);
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{5, 6}, {7, 8}};
        int[][] p = new int[2][2];
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                for (int k = 0; k < 2; k++)
                    p[i][j] += m1[i][k] * m2[k][j];
        System.out.println(Arrays.deepToString(p));
        String[] words = {"pear", "apple", "fig"};
        Arrays.sort(words);
        System.out.println(String.join("|", Arrays.asList(words)));
        char[] cs = "hello".toCharArray();
        cs[0] = 'j';
        System.out.println(new String(cs) + " " + cs.length);
        boolean[] flags = new boolean[3];
        flags[1] = true;
        System.out.println(Arrays.toString(flags));
        int[][][] cube = new int[2][3][4];
        cube[1][2][3] = 42;
        System.out.println(cube[1][2][3] + cube[0][0][0]);
        long[] ls = new long[3];
        Arrays.fill(ls, 7L);
        System.out.println(Arrays.toString(ls));
        int[] grow = Arrays.copyOf(new int[] {1, 2, 3}, 5);
        System.out.println(Arrays.toString(grow) + " " + Arrays.binarySearch(data, data[7]));
    }
}
