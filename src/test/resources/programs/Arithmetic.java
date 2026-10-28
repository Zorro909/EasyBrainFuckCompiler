public class Arithmetic {
    public static void main(String[] args) {
        int[] values = {0, 1, -1, 7, -7, 100, -100, 12345, -98765, 2147483647, -2147483648};
        for (int a : values) {
            for (int b : new int[] {1, -1, 3, -3, 10, 256, -1000}) {
                System.out.println(a + " " + b + ": " + (a + b) + " " + (a - b) + " " + (a * b)
                                + " " + (a / b) + " " + (a % b));
            }
        }
        int x = 2147483647;
        x++;
        System.out.println(x);
        System.out.println(Integer.MAX_VALUE + 1);
        System.out.println(-(-2147483648));
        System.out.println((5 & 3) + " " + (5 | 3) + " " + (5 ^ 3) + " " + (~5));
        System.out.println((1 << 10) + " " + (-1024 >> 3) + " " + (-1024 >>> 28) + " " + (7 << 31));
        int s = 3;
        System.out.println((100 << s) + " " + (100 >> s) + " " + (-100 >>> s));
        char c = 'A';
        c += 2;
        c++;
        System.out.println(c);
        System.out.println((int) c);
        System.out.println((char) (c + 1));
        System.out.println(c + 1);
        byte by = (byte) 200;
        short sh = (short) 40000;
        System.out.println(by + " " + sh + " " + (byte) -129 + " " + (char) 65 + (char) 97);
        long big = 1000000L * 1000;
        System.out.println(big / 1000);
        int m = 17;
        m *= 3;
        m -= 5;
        m /= 2;
        m %= 7;
        m <<= 4;
        m >>= 1;
        m |= 3;
        m &= 0xFE;
        m ^= 0x55;
        System.out.println(m);
        System.out.println(Math.abs(-5) + Math.max(3, 9) + Math.min(-2, 4));
        System.out.println((int) Math.pow(3, 5) + " " + (int) Math.sqrt(1000000));
        System.out.println(Integer.toBinaryString(42) + " " + Integer.toHexString(255) + " "
                        + Integer.toHexString(-1));
        System.out.println(Integer.parseInt("-1234") + Integer.parseInt("99"));
        System.out.println(0x7f + 0b101 + 017 + 1_000);
        boolean t = true, f = false;
        System.out.println((t & f) + " " + (t | f) + " " + (t ^ f) + " " + (!t) + " " + (t && !f));
    }
}
