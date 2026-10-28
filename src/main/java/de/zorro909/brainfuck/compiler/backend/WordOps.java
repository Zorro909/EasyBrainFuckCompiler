package de.zorro909.brainfuck.compiler.backend;

/**
 * Arithmetic on little-endian multi-byte words made of 8-bit cells.
 *
 * <p>A word is addressed by the offset of its lowest byte. Unless stated otherwise operations
 * preserve their inputs and require the destination to be distinct from the inputs. Costs are
 * data dependent: carries are detected by stepping through the addend one unit at a time, so
 * adding or comparing small numbers is cheap while large ones take up to 255 iterations per
 * byte. Multiplication, division and bitwise operations live in {@link BitOps}.
 */
final class WordOps {

    final Emitter e;
    final Scratch s;
    final int w;

    WordOps(Emitter e, Scratch s, int wordBytes) {
        this.e = e;
        this.s = s;
        this.w = wordBytes;
    }

    static int byteOf(long value, int i) {
        return (int) (value >>> (8 * i)) & 0xFF;
    }

    // ---------------------------------------------------------------- byte helpers

    /** {@code flag += (x == 0)}, x preserved. */
    void addIsZero(int x, int flag) {
        int t = s.alloc();
        int t2 = s.alloc();
        e.add(flag, 1);
        e.copyAdd(x, t, t2);
        e.ifNonZeroDestructive(t, () -> e.add(flag, -1));
        s.free(t2);
        s.free(t);
    }

    /** {@code flag += (x != 0)}, x preserved. */
    void addIsNonZero(int x, int flag) {
        int t = s.alloc();
        int t2 = s.alloc();
        e.copyAdd(x, t, t2);
        e.ifNonZeroDestructive(t, () -> e.add(flag, 1));
        s.free(t2);
        s.free(t);
    }

    /** {@code x++; if (x wrapped to 0) flag++}. */
    private void incWrap(int x, int flag) {
        e.add(x, 1);
        addIsZero(x, flag);
    }

    /** {@code if (x == 0) flag++; x--}. */
    private void decWrap(int x, int flag) {
        addIsZero(x, flag);
        e.add(x, -1);
    }

    /**
     * Unsigned byte comparison; destroys {@code x} and {@code y}, leaves them zero.
     * {@code lt}/{@code gt} (if >= 0) receive +1 when {@code x < y} / {@code x > y}.
     * Runs {@code min(x, y) + 1} iterations.
     */
    void compareBytesDestructive(int x, int y, int lt, int gt) {
        int g = gt >= 0 ? gt : s.alloc();
        e.loop(x, () -> {
            e.add(x, -1);
            int el = s.alloc();
            int t = s.alloc();
            int t2 = s.alloc();
            e.add(el, 1);
            e.copyAdd(y, t, t2);
            e.ifNonZeroDestructive(t, () -> {
                e.add(el, -1);
                e.add(y, -1);
            });
            e.ifFlag(el, () -> {
                e.add(g, 1);
                e.clear(x);
            });
            s.free(t2);
            s.free(t);
            s.free(el);
        });
        if (lt >= 0) {
            e.ifNonZeroDestructive(y, () -> e.add(lt, 1));
        } else {
            e.clear(y);
        }
        if (gt < 0) {
            e.clear(g);
            s.free(g);
        }
    }

    // ---------------------------------------------------------------- moves

    void clearWord(int dst) {
        for (int i = 0; i < w; i++) {
            e.clear(dst + i);
        }
    }

    /** {@code dst += value} byte-wise without carries; use on a zero destination. */
    void setConst(int dst, long value) {
        for (int i = 0; i < w; i++) {
            e.add(dst + i, byteOf(value, i));
        }
    }

    /** {@code dst = src} for a zero destination. */
    void copyWord(int src, int dst) {
        copyBytes(src, dst, w);
    }

    void copyBytes(int src, int dst, int n) {
        int t = s.alloc();
        for (int i = 0; i < n; i++) {
            e.copyAdd(src + i, dst + i, t);
        }
        s.free(t);
    }

    /** {@code dst = src; src = 0} for a zero destination. */
    void moveWord(int src, int dst) {
        for (int i = 0; i < w; i++) {
            e.move(src + i, dst + i);
        }
    }

    /** Assigns {@code dst = src} where dst may hold garbage. */
    void assign(int dst, Val src) {
        if (!src.isImm() && src.base() == dst) {
            return;
        }
        clearWord(dst);
        load(dst, src);
    }

    /** {@code dst = src} into a zero destination. */
    void load(int dst, Val src) {
        if (src.isImm()) {
            setConst(dst, src.imm());
        } else {
            copyWord(src.base(), dst);
        }
    }

    // ---------------------------------------------------------------- add / sub

    /** {@code dst += src}, both words of {@code n <= w} bytes; src preserved, src != dst. */
    void add(int dst, Val src, int n) {
        if (src.isImm()) {
            addConst(dst, src.imm(), n);
            return;
        }
        int carry = -1;
        for (int i = 0; i < n; i++) {
            int di = dst + i;
            int si = src.base() + i;
            if (i == n - 1) {
                if (carry >= 0) {
                    e.move(carry, di);
                    s.free(carry);
                }
                int t = s.alloc();
                e.copyAdd(si, di, t);
                s.free(t);
                break;
            }
            int next = s.alloc();
            if (carry >= 0) {
                int c = carry;
                e.ifFlag(c, () -> incWrap(di, next));
                s.free(c);
            }
            int cnt = s.alloc();
            int t = s.alloc();
            e.copyAdd(si, cnt, t);
            s.free(t);
            e.loop(cnt, () -> {
                e.add(cnt, -1);
                incWrap(di, next);
            });
            s.free(cnt);
            carry = next;
        }
    }

    void add(int dst, Val src) {
        add(dst, src, w);
    }

    /** {@code dst -= src}; src preserved, src != dst. */
    void sub(int dst, Val src, int n) {
        if (src.isImm()) {
            addConst(dst, -src.imm(), n);
            return;
        }
        int borrow = -1;
        for (int i = 0; i < n; i++) {
            int di = dst + i;
            int si = src.base() + i;
            if (i == n - 1) {
                if (borrow >= 0) {
                    e.moveSub(borrow, di);
                    s.free(borrow);
                }
                int t = s.alloc();
                e.copySub(si, di, t);
                s.free(t);
                break;
            }
            int next = s.alloc();
            if (borrow >= 0) {
                int b = borrow;
                e.ifFlag(b, () -> decWrap(di, next));
                s.free(b);
            }
            int cnt = s.alloc();
            int t = s.alloc();
            e.copyAdd(si, cnt, t);
            s.free(t);
            e.loop(cnt, () -> {
                e.add(cnt, -1);
                decWrap(di, next);
            });
            s.free(cnt);
            borrow = next;
        }
    }

    void sub(int dst, Val src) {
        sub(dst, src, w);
    }

    /** {@code dst += value} for a constant. */
    void addConst(int dst, long value, int n) {
        long mask = n >= 8 ? -1L : (1L << (8 * n)) - 1;
        long v = value & mask;
        if (v == 0) {
            return;
        }
        long neg = (-value) & mask;
        // unsigned: with 8-byte words the masked values span the whole long range
        if (Long.compareUnsigned(v, 4) <= 0) {
            for (int k = 0; k < v; k++) {
                incFrom(dst, 0, n);
            }
            return;
        }
        if (Long.compareUnsigned(neg, 4) <= 0) {
            for (int k = 0; k < neg; k++) {
                decFrom(dst, 0, n);
            }
            return;
        }
        // general case: add or subtract a materialized constant, whichever has fewer units
        boolean subtract = Long.compareUnsigned(neg, v) < 0;
        long c = subtract ? neg : v;
        int tmp = s.allocRun(n);
        for (int i = 0; i < n; i++) {
            e.add(tmp + i, byteOf(c, i));
        }
        if (subtract) {
            sub(dst, Val.reg(tmp), n);
        } else {
            add(dst, Val.reg(tmp), n);
        }
        for (int i = 0; i < n; i++) {
            e.clear(tmp + i);
        }
        s.freeRun(tmp, n);
    }

    void inc(int dst) {
        incFrom(dst, 0, w);
    }

    void dec(int dst) {
        decFrom(dst, 0, w);
    }

    private void incFrom(int dst, int i, int n) {
        e.add(dst + i, 1);
        if (i < n - 1) {
            int z = s.alloc();
            addIsZero(dst + i, z);
            e.ifFlag(z, () -> incFrom(dst, i + 1, n));
            s.free(z);
        }
    }

    private void decFrom(int dst, int i, int n) {
        if (i < n - 1) {
            int z = s.alloc();
            addIsZero(dst + i, z);
            e.ifFlag(z, () -> decFrom(dst, i + 1, n));
            s.free(z);
        }
        e.add(dst + i, -1);
    }

    /** In-place bitwise complement. */
    void not(int dst) {
        int t = s.alloc();
        for (int i = 0; i < w; i++) {
            e.add(t, -1);
            e.moveSub(dst + i, t);
            e.move(t, dst + i);
        }
        s.free(t);
    }

    /** In-place two's complement negation. */
    void negate(int dst) {
        not(dst);
        inc(dst);
    }

    // ---------------------------------------------------------------- comparisons

    /** {@code flag += (a != b)} (0 or 1). */
    void addNotEqual(Val a, Val b, int flag) {
        int ne = s.alloc();
        for (int i = 0; i < w; i++) {
            int d = s.alloc();
            loadByte(d, a, i, 1);
            loadByte(d, b, i, -1);
            e.ifNonZeroDestructive(d, () -> e.add(ne, 1));
            s.free(d);
        }
        e.ifNonZeroDestructive(ne, () -> e.add(flag, 1));
        s.free(ne);
    }

    /** {@code flag += (a == b)}. */
    void addEqual(Val a, Val b, int flag) {
        int ne = s.alloc();
        addNotEqual(a, b, ne);
        e.add(flag, 1);
        e.ifFlag(ne, () -> e.add(flag, -1));
        s.free(ne);
    }

    /** {@code flag += (word != 0)}. */
    void addIsNonZeroWord(Val a, int flag) {
        addNotEqual(a, Val.imm(0), flag);
    }

    /**
     * Signed comparison: {@code lt += (a < b)}, {@code gt += (a > b)}; pass -1 to skip one.
     * Bytes are compared from the top; equal bytes cost O(1), the first unequal byte pair is
     * resolved with an O(min) countdown.
     */
    void compare(Val a, Val b, int lt, int gt) {
        int decided = s.alloc();
        for (int i = w - 1; i >= 0; i--) {
            boolean top = i == w - 1;
            int x = s.alloc();
            int y = s.alloc();
            loadByte(x, a, i, 1);
            loadByte(y, b, i, 1);
            if (top) {
                e.add(x, 128);
                e.add(y, 128);
            }
            int go = s.alloc();
            int t = s.alloc();
            int d = s.alloc();
            e.copyAdd(x, d, t);
            e.copySub(y, d, t);
            e.ifNonZeroDestructive(d, () -> e.add(go, 1));
            s.free(d);
            if (!top) {
                // skip when a higher byte already decided
                e.loop(decided, () -> {
                    e.add(decided, -1);
                    e.add(t, 1);
                    e.clear(go);
                });
                e.move(t, decided);
            }
            s.free(t);
            e.ifFlag(go, () -> {
                e.add(decided, 1);
                compareBytesDestructive(x, y, lt, gt);
            });
            s.free(go);
            e.clear(x);
            e.clear(y);
            s.free(y);
            s.free(x);
        }
        e.clear(decided);
        s.free(decided);
    }

    /** {@code dst += sign * byte i of v}, sign = +1/-1. */
    void loadByte(int dst, Val v, int i, int sign) {
        if (v.isImm()) {
            e.add(dst, sign * byteOf(v.imm(), i));
        } else {
            int t = s.alloc();
            if (sign > 0) {
                e.copyAdd(v.base() + i, dst, t);
            } else {
                e.copySub(v.base() + i, dst, t);
            }
            s.free(t);
        }
    }

    // ---------------------------------------------------------------- narrowing

    /** Sign-extends byte {@code from} into all higher bytes (dst is a word). */
    void signExtendFrom(int dst, int from) {
        for (int i = from + 1; i < w; i++) {
            e.clear(dst + i);
        }
        int x = s.alloc();
        int y = s.alloc();
        int neg = s.alloc();
        int t = s.alloc();
        e.copyAdd(dst + from, x, t);
        s.free(t);
        e.add(y, 127);
        // x > 127 means the sign bit is set
        compareBytesDestructive(x, y, -1, neg);
        e.ifFlag(neg, () -> {
            for (int i = from + 1; i < w; i++) {
                e.add(dst + i, 255);
            }
        });
        s.free(neg);
        s.free(y);
        s.free(x);
    }

    /** Word operand: a register (offset of its low byte) or an immediate. */
    record Val(int base, long imm, boolean isImm) {
        static Val reg(int base) {
            return new Val(base, 0, false);
        }

        static Val imm(long value) {
            return new Val(-1, value, true);
        }
    }
}
