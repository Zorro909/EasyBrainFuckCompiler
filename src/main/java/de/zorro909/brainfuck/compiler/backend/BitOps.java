package de.zorro909.brainfuck.compiler.backend;

/**
 * Bit-serial algorithms for the operations that are too slow on bytes: multiplication,
 * division, bitwise logic, shifts and decimal printing.
 *
 * <p>Words are unpacked into one cell per bit. Bits of several operands are interleaved in
 * <em>groups</em> of {@link #g} cells (group k holds bit k of every field plus per-bit
 * temporaries), so a ripple-carry step only moves the pointer a few cells. With bits instead of
 * bytes every arithmetic step is a small constant-time switch instead of an O(value) loop.
 *
 * <p>Layout of the work area (all cells zero on entry and on exit):
 * <pre>
 * base+0          decoder input byte
 * base+1..8       decoder output bits
 * base+9..        byte buffer (2 words)
 * ...             counters / flags
 * groups          g cells per bit, n = 8 * wordBytes groups
 * </pre>
 */
final class BitOps {

    final Emitter e;
    final int w;
    final int n;
    final int g;

    private final int decIn;
    private final int decOut;
    private final int buf;
    final int counter;
    final int flagA;
    final int flagB;
    final int flagC;
    private final int groups;

    BitOps(Emitter e, int wordBytes, int base, int groupSize) {
        this.e = e;
        this.w = wordBytes;
        this.n = 8 * wordBytes;
        this.g = groupSize;
        this.decIn = base;
        this.decOut = base + 1;
        this.buf = base + 9;
        this.counter = buf + 2 * wordBytes;
        this.flagA = counter + 1;
        this.flagB = counter + 2;
        this.flagC = counter + 3;
        this.groups = counter + 4;
    }

    /** Cell of {@code field} in bit group {@code k}. */
    int bit(int field, int k) {
        return groups + k * g + field;
    }

    /** Number of cells used above {@code base}. */
    int extent() {
        return groups + n * g + g;
    }

    // ---------------------------------------------------------------- pack / unpack

    /**
     * Unpacks words into bit fields. {@code words[j]} is consumed (left zero) and its bits land
     * in field {@code fields[j]}. One shared decoder handles all bytes in a single loop.
     */
    void unpack(int[] words, int[] fields) {
        int k = words.length;
        int bytes = k * w;
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < w; i++) {
                e.move(words[j] + i, buf + j * w + i);
            }
        }
        e.add(counter, bytes);
        e.loop(counter, () -> {
            e.add(counter, -1);
            // take the highest remaining byte, rotate the buffer up
            e.move(buf + bytes - 1, decIn);
            for (int i = bytes - 1; i > 0; i--) {
                e.move(buf + i - 1, buf + i);
            }
            decode();
            // shift the virtual bit string (field 0 lowest) up by 8 and insert the new byte
            int total = k * n;
            for (int m = total - 1; m >= 8; m--) {
                e.move(virtualBit(fields, m - 8), virtualBit(fields, m));
            }
            for (int b = 0; b < 8; b++) {
                e.move(decOut + b, virtualBit(fields, b));
            }
        });
    }

    private int virtualBit(int[] fields, int m) {
        return bit(fields[m / n], m % n);
    }

    /** Converts decIn (0..255) into 8 bits at decOut with a 255-level nested countdown. */
    private void decode() {
        for (int v = 1; v <= 255; v++) {
            e.moveTo(decIn);
            e.raw("[");
            e.add(decIn, -1);
            int prev = v - 1;
            for (int b = 0; b < 8; b++) {
                int before = (prev >> b) & 1;
                int after = (v >> b) & 1;
                if (before != after) {
                    e.add(decOut + b, after - before);
                }
            }
        }
        for (int v = 1; v <= 255; v++) {
            e.moveTo(decIn);
            e.raw("]");
        }
    }

    /** {@code dst += value of field} for a zero word; the field is consumed. */
    void pack(int field, int dst) {
        for (int k = 0; k < n; k++) {
            e.moveAdd(bit(field, k), new int[] {dst + k / 8}, new int[] {1 << (k % 8)});
        }
    }

    void clearField(int field) {
        for (int k = 0; k < n; k++) {
            e.clear(bit(field, k));
        }
    }

    // ---------------------------------------------------------------- arithmetic

    /**
     * {@code dst += src (+ carryIn)} over all bits; {@code src} preserved when
     * {@code complementSrc} is false, otherwise {@code dst += ~src}. Uses fields {@code sum},
     * {@code tmp} and {@code carry}. Returns nothing; the final carry is left in
     * {@code carryOut} if >= 0 and discarded otherwise.
     */
    void addField(int dst, int src, boolean complementSrc, boolean carryIn, int sum, int tmp,
                    int carry, int carryOut) {
        if (carryIn) {
            e.add(bit(carry, 0), 1);
        }
        for (int k = 0; k < n; k++) {
            int s = bit(sum, k);
            int d = bit(dst, k);
            e.move(d, s);
            if (complementSrc) {
                e.add(s, 1);
                e.copySub(bit(src, k), s, bit(tmp, k));
            } else {
                e.copyAdd(bit(src, k), s, bit(tmp, k));
            }
            e.move(bit(carry, k), s);
            int c = k + 1 < n ? bit(carry, k + 1) : (carryOut >= 0 ? carryOut : bit(tmp, k));
            // s in 0..3: d = s & 1, c = s >> 1
            e.loop(s, () -> {
                e.add(s, -1);
                e.add(d, 1);
                e.loop(s, () -> {
                    e.add(s, -1);
                    e.add(d, -1);
                    e.add(c, 1);
                    e.loop(s, () -> {
                        e.add(s, -1);
                        e.add(d, 1);
                    });
                });
            });
            if (k + 1 == n && carryOut < 0) {
                e.clear(bit(tmp, k));
            }
        }
    }

    /** In-place two's complement negation of a field. */
    void negateField(int field, int sum, int carry) {
        for (int k = 0; k < n; k++) {
            int b = bit(field, k);
            int s = bit(sum, k);
            e.add(s, 1);
            e.moveSub(b, s);
            e.move(s, b);
        }
        // + 1 with ripple carry
        e.add(bit(carry, 0), 1);
        for (int k = 0; k < n; k++) {
            int b = bit(field, k);
            int s = bit(sum, k);
            e.move(b, s);
            e.move(bit(carry, k), s);
            int c = k + 1 < n ? bit(carry, k + 1) : -1;
            e.loop(s, () -> {
                e.add(s, -1);
                e.add(b, 1);
                e.loop(s, () -> {
                    e.add(s, -1);
                    e.add(b, -1);
                    if (c >= 0) {
                        e.add(c, 1);
                    }
                });
            });
        }
    }

    /** Shifts a field one bit towards the top; bit 0 becomes 0, the top bit is dropped. */
    void shiftUp(int field) {
        e.clear(bit(field, n - 1));
        for (int k = n - 1; k > 0; k--) {
            e.move(bit(field, k - 1), bit(field, k));
        }
    }

    /** Shifts a field one bit towards bit 0; the top bit is cleared, or kept for arithmetic
     * shifts. Bit 0 must already be zero (moved out by the caller) or is dropped. */
    void shiftDown(int field, boolean arithmetic, int tmpField) {
        e.clear(bit(field, 0));
        for (int k = 1; k < n; k++) {
            if (arithmetic && k == n - 1) {
                e.copyAdd(bit(field, k), bit(field, k - 1), bit(tmpField, k));
            } else {
                e.move(bit(field, k), bit(field, k - 1));
            }
        }
    }

    /** {@code flag += (field != 0)}, using {@code tmpField} as temporaries. */
    void addFieldNonZero(int field, int flag, int tmpField, int acc) {
        for (int k = 0; k < n; k++) {
            e.copyAdd(bit(field, k), acc, bit(tmpField, k));
        }
        e.ifNonZeroDestructive(acc, () -> e.add(flag, 1));
    }
}
