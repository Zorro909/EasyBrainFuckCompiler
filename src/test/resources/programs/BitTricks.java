public class BitTricks {
    static int popcount(int x) {
        int c = 0;
        while (x != 0) {
            x &= x - 1;
            c++;
        }
        return c;
    }

    static boolean isPowerOfTwo(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }

    static int reverseBits(int x) {
        int r = 0;
        for (int i = 0; i < 32; i++) {
            r = (r << 1) | (x & 1);
            x >>>= 1;
        }
        return r;
    }

    public static void main(String[] args) {
        for (int v : new int[] {0, 1, 7, 8, 255, 1024, -1, 0x55555555}) {
            System.out.println(v + " pop=" + popcount(v) + " pow2=" + isPowerOfTwo(v) + " rev="
                            + reverseBits(v) + " hex=" + Integer.toHexString(v));
        }
        int flags = 0;
        flags |= 1 << 3;
        flags |= 1 << 5;
        flags &= ~(1 << 3);
        flags ^= 0xF0;
        System.out.println(flags + " " + Integer.toBinaryString(flags) + " " + (flags >> 2) + " "
                        + Integer.bitCount(flags));
        long mask = (1L << 20) - 1;
        System.out.println(mask + " " + (123456789 & mask));
        char c = 'a';
        System.out.println((char) (c ^ 32));
    }
}
