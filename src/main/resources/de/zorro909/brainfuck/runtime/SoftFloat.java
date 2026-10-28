/**
 * IEEE 754 binary floating point in software, round to nearest even, no exception flags.
 *
 * <p>This file is a template: the compiler instantiates it as {@code FloatOps} for {@code float}
 * and as {@code DoubleOps} for {@code double} (binary64 with 8-byte words, otherwise binary32),
 * replacing the dollar placeholders. Values are bit patterns held in a {@code long}. The sign
 * bit is only ever touched with shifts and masks, and all intermediate values of the binary32
 * instance fit into 31 bits, so the code is exact on any word size of at least 32 bits.
 *
 * <p>Significands are handled with three extra low bits (guard, round, sticky): a normalized
 * working significand has its leading one at bit {@code MANT + 3}.
 */
final class $NAME {
    static final int MANT = $MANT;
    static final int EXP = $EXP;
    /** Bits of the integer types this format converts from and to. */
    static final int WORD = $WORD;
    static final int BIAS = (1 << (EXP - 1)) - 1;
    static final int EMAX = (1 << EXP) - 1;
    static final int WIDTH = 1 + EXP + MANT;
    static final long FRAC = (1L << MANT) - 1;
    static final long IMPLICIT = 1L << MANT;
    static final long LEAD = 1L << (MANT + 3);
    static final long INF = (long) EMAX << MANT;
    static final long NAN = INF | (1L << (MANT - 1));
    static final long ONE = (long) BIAS << MANT;
    static final long HALF = (long) (BIAS - 1) << MANT;
    static final long MIN_WORD = 1L << (WORD - 1);
    static final long MAX_WORD = MIN_WORD - 1;
    /** Split point of significands for the double-width product. */
    static final int H = (MANT + 2) / 2;

    static int sign(long x) {
        return (int) ((x >>> (WIDTH - 1)) & 1);
    }

    static int exponent(long x) {
        return (int) ((x >>> MANT) & EMAX);
    }

    static long pack(int sign, long exp, long frac) {
        return ((long) sign << (WIDTH - 1)) | (exp << MANT) | frac;
    }

    static boolean isNaN(long x) {
        return exponent(x) == EMAX && (x & FRAC) != 0;
    }

    static boolean isInfinite(long x) {
        return exponent(x) == EMAX && (x & FRAC) == 0;
    }

    static boolean isZero(long x) {
        return exponent(x) == 0 && (x & FRAC) == 0;
    }

    /** {@code x >>> n}, ORing every shifted-out bit into bit 0. */
    static long shiftRightSticky(long x, int n) {
        if (n <= 0) {
            return x;
        }
        if (n >= WORD - 1) {
            // shift counts wrap at the word size, so huge shifts must not reach the shifter
            return x != 0 ? 1 : 0;
        }
        long lost = x & ((1L << n) - 1);
        return (x >>> n) | (lost != 0 ? 1 : 0);
    }

    /**
     * Rounds and encodes {@code sig * 2^(exp - BIAS - MANT - 3)}. {@code sig} has its leading
     * one at bit {@code MANT + 3}, or lower if {@code exp <= 1} (subnormal results).
     */
    static long roundPack(int sign, int exp, long sig) {
        if (exp <= 0) {
            sig = shiftRightSticky(sig, 1 - exp);
            exp = 0;
        }
        int low = (int) (sig & 7);
        sig = sig >>> 3;
        if (low > 4 || low == 4 && (sig & 1) != 0) {
            sig++;
        }
        if (exp == 0) {
            // a carry into bit MANT turns the subnormal into the smallest normal number
            return pack(sign, sig >>> MANT, sig & FRAC);
        }
        if (sig > FRAC + IMPLICIT) {
            sig = sig >>> 1;
            exp++;
        }
        if (exp >= EMAX) {
            return pack(sign, EMAX, 0);
        }
        return pack(sign, exp, sig & FRAC);
    }

    /** Normalizes {@code sig * 2^(exp - BIAS - MANT - 3)} (any {@code sig > 0}) and rounds. */
    static long normalizeRoundPack(int sign, int exp, long sig) {
        while (sig >= LEAD + LEAD) {
            sig = (sig >>> 1) | (sig & 1);
            exp++;
        }
        while (sig < LEAD) {
            sig = sig + sig;
            exp--;
        }
        return roundPack(sign, exp, sig);
    }

    /**
     * Encodes {@code sig * 2^e2} (plus a nonzero tail below it if {@code sticky}). {@code sig}
     * must be positive and below {@code 2^(WORD - 2)}.
     */
    static long fromParts(int sign, int e2, long sig, boolean sticky) {
        // working exponent such that value = sig * 2^(exp - BIAS - MANT - 3)
        sig = sig + sig;
        if (sticky) {
            sig = sig | 1;
        }
        int exp = e2 - 1 + BIAS + MANT + 3;
        while (sig >= LEAD + LEAD) {
            sig = (sig >>> 1) | (sig & 1);
            exp++;
        }
        while (sig < LEAD) {
            sig = sig + sig;
            exp--;
        }
        if (exp >= EMAX) {
            return pack(sign, EMAX, 0);
        }
        return roundPack(sign, exp, sig);
    }

    static long neg(long a) {
        return a ^ (1L << (WIDTH - 1));
    }

    static long abs(long a) {
        return a & ~(1L << (WIDTH - 1));
    }

    static long add(long a, long b) {
        int ea = exponent(a);
        int eb = exponent(b);
        int sa = sign(a);
        int sb = sign(b);
        long fa = a & FRAC;
        long fb = b & FRAC;
        if (ea == EMAX || eb == EMAX) {
            if (ea == EMAX && fa != 0 || eb == EMAX && fb != 0) {
                return NAN;
            }
            if (ea == EMAX && eb == EMAX) {
                return sa == sb ? a : NAN;
            }
            return ea == EMAX ? a : b;
        }
        long ma = ea == 0 ? fa : fa | IMPLICIT;
        long mb = eb == 0 ? fb : fb | IMPLICIT;
        if (ma == 0 && mb == 0) {
            return pack(sa & sb, 0, 0);
        }
        if (ea == 0) {
            ea = 1;
        }
        if (eb == 0) {
            eb = 1;
        }
        if (ea < eb || ea == eb && ma < mb) {
            int t = ea;
            ea = eb;
            eb = t;
            long u = ma;
            ma = mb;
            mb = u;
            t = sa;
            sa = sb;
            sb = t;
        }
        ma = ma << 3;
        mb = shiftRightSticky(mb << 3, ea - eb);
        long m = sa == sb ? ma + mb : ma - mb;
        if (m == 0) {
            return 0;
        }
        return normalizeRoundPack(sa, ea, m);
    }

    static long sub(long a, long b) {
        return add(a, neg(b));
    }

    static long mul(long a, long b) {
        int ea = exponent(a);
        int eb = exponent(b);
        int s = sign(a) ^ sign(b);
        long ma = a & FRAC;
        long mb = b & FRAC;
        if (ea == EMAX || eb == EMAX) {
            if (ea == EMAX && ma != 0 || eb == EMAX && mb != 0) {
                return NAN;
            }
            if (isZero(a) || isZero(b)) {
                return NAN;
            }
            return pack(s, EMAX, 0);
        }
        if (ea == 0 && ma == 0 || eb == 0 && mb == 0) {
            return pack(s, 0, 0);
        }
        if (ea == 0) {
            ea = 1;
            while (ma < IMPLICIT) {
                ma = ma + ma;
                ea--;
            }
        } else {
            ma = ma | IMPLICIT;
        }
        if (eb == 0) {
            eb = 1;
            while (mb < IMPLICIT) {
                mb = mb + mb;
                eb--;
            }
        } else {
            mb = mb | IMPLICIT;
        }
        // 2(MANT+1)-bit product from H-bit halves: hi * 2^(2H) + lo
        long lowMask = (1L << H) - 1;
        long a0 = ma & lowMask;
        long a1 = ma >>> H;
        long b0 = mb & lowMask;
        long b1 = mb >>> H;
        long mid = a1 * b0 + a0 * b1;
        long lo = a0 * b0 + ((mid & lowMask) << H);
        long wide = (1L << (2 * H)) - 1;
        long hi = a1 * b1 + (mid >>> H) + (lo >>> (2 * H));
        lo = lo & wide;
        // keep MANT + 4 or MANT + 5 bits: product >> (MANT - 3), sticky from the rest
        int k = MANT - 3;
        long sig = (hi << (2 * H - k)) | (lo >>> k);
        if ((lo & ((1L << k) - 1)) != 0) {
            sig = sig | 1;
        }
        int exp = ea + eb - BIAS;
        if (sig >= LEAD + LEAD) {
            sig = (sig >>> 1) | (sig & 1);
            exp++;
        }
        if (exp >= EMAX) {
            return pack(s, EMAX, 0);
        }
        return roundPack(s, exp, sig);
    }

    static long div(long a, long b) {
        int ea = exponent(a);
        int eb = exponent(b);
        int s = sign(a) ^ sign(b);
        long ma = a & FRAC;
        long mb = b & FRAC;
        if (ea == EMAX && ma != 0 || eb == EMAX && mb != 0) {
            return NAN;
        }
        if (ea == EMAX) {
            return eb == EMAX ? NAN : pack(s, EMAX, 0);
        }
        if (eb == EMAX) {
            return pack(s, 0, 0);
        }
        if (eb == 0 && mb == 0) {
            return ea == 0 && ma == 0 ? NAN : pack(s, EMAX, 0);
        }
        if (ea == 0 && ma == 0) {
            return pack(s, 0, 0);
        }
        if (ea == 0) {
            ea = 1;
            while (ma < IMPLICIT) {
                ma = ma + ma;
                ea--;
            }
        } else {
            ma = ma | IMPLICIT;
        }
        if (eb == 0) {
            eb = 1;
            while (mb < IMPLICIT) {
                mb = mb + mb;
                eb--;
            }
        } else {
            mb = mb | IMPLICIT;
        }
        int exp = ea - eb + BIAS;
        if (ma < mb) {
            ma = ma + ma;
            exp--;
        }
        long q = 0;
        long r = ma;
        for (int i = 0; i < MANT + 4; i++) {
            q = q + q;
            if (r >= mb) {
                r = r - mb;
                q = q | 1;
            }
            r = r + r;
        }
        if (r != 0) {
            q = q | 1;
        }
        if (exp >= EMAX) {
            return pack(s, EMAX, 0);
        }
        return roundPack(s, exp, q);
    }

    /** Java's {@code %}: the exact remainder of truncating division, with the dividend's sign. */
    static long rem(long a, long b) {
        int ea = exponent(a);
        int eb = exponent(b);
        int s = sign(a);
        long ma = a & FRAC;
        long mb = b & FRAC;
        if (ea == EMAX || eb == EMAX && mb != 0 || eb == 0 && mb == 0) {
            return NAN;
        }
        if (eb == EMAX || ea == 0 && ma == 0) {
            return a;
        }
        if (ea == 0) {
            ea = 1;
            while (ma < IMPLICIT) {
                ma = ma + ma;
                ea--;
            }
        } else {
            ma = ma | IMPLICIT;
        }
        if (eb == 0) {
            eb = 1;
            while (mb < IMPLICIT) {
                mb = mb + mb;
                eb--;
            }
        } else {
            mb = mb | IMPLICIT;
        }
        if (ea < eb) {
            return a;
        }
        while (ea > eb) {
            if (ma >= mb) {
                ma = ma - mb;
            }
            ma = ma + ma;
            ea--;
        }
        if (ma >= mb) {
            ma = ma - mb;
        }
        if (ma == 0) {
            return pack(s, 0, 0);
        }
        return normalizeRoundPack(s, eb, ma << 3);
    }

    static long sqrt(long a) {
        int e = exponent(a);
        long m = a & FRAC;
        if (e == EMAX) {
            return m != 0 || sign(a) != 0 ? NAN : a;
        }
        if (e == 0 && m == 0) {
            return a;
        }
        if (sign(a) != 0) {
            return NAN;
        }
        if (e == 0) {
            e = 1;
            while (m < IMPLICIT) {
                m = m + m;
                e--;
            }
        } else {
            m = m | IMPLICIT;
        }
        int unbiased = e - BIAS;
        if ((unbiased & 1) != 0) {
            m = m + m;
            unbiased--;
        }
        // digit-by-digit square root of m * 2^(MANT + 6), two bits of the radicand per step
        long q = 0;
        long r = 0;
        for (int j = 0; j < MANT + 4; j++) {
            int shift = MANT - 2 * j;
            long two;
            if (shift >= 0) {
                two = (m >>> shift) & 3;
            } else if (shift == -1) {
                two = (m + m) & 3;
            } else {
                two = 0;
            }
            r = (r << 2) | two;
            long t = (q << 2) | 1;
            if (r >= t) {
                r = r - t;
                q = q + q + 1;
            } else {
                q = q + q;
            }
        }
        if (r != 0) {
            q = q | 1;
        }
        return roundPack(0, unbiased / 2 + BIAS, q);
    }

    static boolean eq(long a, long b) {
        if (isNaN(a) || isNaN(b)) {
            return false;
        }
        if (isZero(a) && isZero(b)) {
            return true;
        }
        return abs(a) == abs(b) && sign(a) == sign(b);
    }

    static boolean lt(long a, long b) {
        if (isNaN(a) || isNaN(b)) {
            return false;
        }
        if (isZero(a) && isZero(b)) {
            return false;
        }
        int sa = sign(a);
        int sb = sign(b);
        if (sa != sb) {
            return sa == 1;
        }
        long xa = abs(a);
        long xb = abs(b);
        return sa == 0 ? xa < xb : xa > xb;
    }

    static boolean le(long a, long b) {
        return lt(a, b) || eq(a, b);
    }

    /** {@code Double.compare}: total order with -0 < +0 and NaN above everything. */
    static int compare(long a, long b) {
        boolean na = isNaN(a);
        boolean nb = isNaN(b);
        if (na || nb) {
            return na && nb ? 0 : na ? 1 : -1;
        }
        int sa = sign(a);
        int sb = sign(b);
        if (sa != sb) {
            return sa == 1 ? -1 : 1;
        }
        long xa = abs(a);
        long xb = abs(b);
        if (xa == xb) {
            return 0;
        }
        return (xa < xb) == (sa == 0) ? -1 : 1;
    }

    static long fromLong(long v) {
        if (v == 0) {
            return 0;
        }
        if (v == MIN_WORD) {
            return pack(1, BIAS + WORD - 1, 0);
        }
        int s = 0;
        if (v < 0) {
            s = 1;
            v = -v;
        }
        int len = 1;
        long t = 2;
        while (len < WORD - 1 && t <= v) {
            t = t + t;
            len++;
        }
        long sig;
        if (len > MANT + 4) {
            sig = shiftRightSticky(v, len - (MANT + 4));
        } else {
            sig = v << (MANT + 4 - len);
        }
        return roundPack(s, BIAS + len - 1, sig);
    }

    /** Java's narrowing to an integer of {@code WORD} bits: truncates, saturates, NaN is 0. */
    static long toLong(long a) {
        int e = exponent(a);
        if (e == EMAX && (a & FRAC) != 0) {
            return 0;
        }
        int unbiased = e - BIAS;
        if (unbiased < 0) {
            return 0;
        }
        if (unbiased >= WORD - 1) {
            return sign(a) != 0 ? MIN_WORD : MAX_WORD;
        }
        long m = (a & FRAC) | IMPLICIT;
        long v = unbiased >= MANT ? m << (unbiased - MANT) : m >>> (MANT - unbiased);
        return sign(a) != 0 ? -v : v;
    }

    /** Mask of the fraction bits below the units digit, or -1 if {@code a} has no such bits. */
    private static long belowOne(long a) {
        int unbiased = exponent(a) - BIAS;
        if (unbiased >= MANT) {
            return -1;
        }
        return FRAC >>> unbiased;
    }

    static long floor(long a) {
        int unbiased = exponent(a) - BIAS;
        if (unbiased >= MANT || isZero(a)) {
            return a;
        }
        if (unbiased < 0) {
            return sign(a) == 0 ? 0 : pack(1, BIAS, 0);
        }
        long mask = belowOne(a);
        if ((a & mask) == 0) {
            return a;
        }
        long base = a & ~mask;
        return sign(a) == 0 ? base : base + mask + 1;
    }

    static long ceil(long a) {
        return neg(floor(neg(a)));
    }

    /** {@code Math.rint}: nearest integer, ties to even. */
    static long rint(long a) {
        int unbiased = exponent(a) - BIAS;
        if (unbiased >= MANT || isZero(a)) {
            return a;
        }
        int s = sign(a);
        if (unbiased < -1) {
            return pack(s, 0, 0);
        }
        if (unbiased == -1) {
            return (a & FRAC) == 0 ? pack(s, 0, 0) : pack(s, BIAS, 0);
        }
        long mask = belowOne(a);
        long frac = a & mask;
        long half = (mask + 1) >>> 1;
        long base = a & ~mask;
        if (frac > half || frac == half && (base & (mask + 1)) != 0) {
            base = base + mask + 1;
        }
        return base;
    }

    /** {@code Math.round}: nearest integer, ties toward positive infinity. */
    static long round(long a) {
        if (exponent(a) - BIAS >= MANT) {
            return toLong(a);
        }
        long f = floor(a);
        long r = toLong(f);
        if (!lt(sub(a, f), HALF) && r != MAX_WORD) {
            r++;
        }
        return r;
    }

    /** Unbiased exponent of the lowest significand bit: {@code |a| = significand * 2^this}. */
    static int lowExponent(long a) {
        int e = exponent(a);
        return (e == 0 ? 1 : e) - BIAS - MANT;
    }

    static long significand(long a) {
        int e = exponent(a);
        return e == 0 ? a & FRAC : (a & FRAC) | IMPLICIT;
    }

    /** Converts from another format given as its sign, significand and exponent. */
    static long convert(int sign, long sig, int e2, boolean isNaN, boolean isInfinite) {
        if (isNaN) {
            return NAN;
        }
        if (isInfinite) {
            return pack(sign, EMAX, 0);
        }
        if (sig == 0) {
            return pack(sign, 0, 0);
        }
        // narrow significands (F32 at most 24 bits) can go straight in; wide ones are trimmed
        boolean sticky = false;
        while (sig >= MIN_WORD >>> 2) {
            sticky = sticky || (sig & 1) != 0;
            sig = sig >>> 1;
            e2++;
        }
        return fromParts(sign, e2, sig, sticky);
    }

    /** Converts a value of the other floating point type (float <-> double). */
    static long fromOther(long x) {
        return convert($OTHER.sign(x), $OTHER.significand(x), $OTHER.lowExponent(x),
                        $OTHER.isNaN(x), $OTHER.isInfinite(x));
    }

    /** {@code Math.scalb}: {@code a * 2^n}, rounded once. */
    static long scalb(long a, int n) {
        int e = exponent(a);
        if (e == EMAX || isZero(a)) {
            return a;
        }
        if (n > 3 * BIAS) {
            n = 3 * BIAS;
        } else if (n < -3 * BIAS) {
            n = -3 * BIAS;
        }
        return fromParts(sign(a), lowExponent(a) + n, significand(a), false);
    }

    // ------------------------------------------------------------------ decimal conversion

    /** Limbs needed by the exact conversions (base 256). */
    static final int CAP = (BIAS + MANT + 320) / 8 + 8;
    static Big br;
    static Big bs;
    static Big bp;
    static Big bm;
    static Big bt;
    /** Result of {@link #digits}: {@code 0.D[0]D[1]...D[count-1] * 10^decExp}. */
    static char[] digitBuf;
    static int count;
    static int decExp;
    static char[] text;

    static void initBuffers() {
        if (br == null) {
            br = new Big(CAP);
            bs = new Big(CAP);
            bp = new Big(CAP);
            bm = new Big(CAP);
            bt = new Big(CAP);
            digitBuf = new char[MANT / 3 + 8];
            // room for %.Nf of the largest finite value
            text = new char[BIAS * 302 / 1000 + MANT / 3 + 48];
        }
    }

    /**
     * Shortest decimal digits of the finite, non-zero {@code a} that read back as {@code a}
     * (Steele and White / Dragon4 free format), chosen like {@code Double.toString}: the
     * closest such decimal, preferring an even last digit, and the closest two-digit decimal
     * when a single digit would do.
     */
    static void digits(long a) {
        initBuffers();
        long f = significand(a);
        int e = lowExponent(a);
        boolean even = (f & 1) == 0;
        // the gap to the lower neighbour is halved at a power of two (except the smallest normal)
        boolean boundary = (a & FRAC) == 0 && exponent(a) > 1;
        Big r = br;
        Big s = bs;
        Big mp = bp;
        Big mm = bm;
        // r / s = v, mp / s and mm / s = half the gaps to the neighbours
        r.set(f);
        if (e >= 0) {
            s.set(boundary ? 4 : 2);
            r.shiftLeft(e + (boundary ? 2 : 1));
            mp.set(1);
            mp.shiftLeft(e + (boundary ? 1 : 0));
            mm.set(1);
            mm.shiftLeft(e);
        } else {
            r.shiftLeft(boundary ? 2 : 1);
            s.set(1);
            s.shiftLeft((boundary ? 2 : 1) - e);
            mp.set(boundary ? 2 : 1);
            mm.set(1);
        }
        int len = 0;
        for (long t = f; t != 0; t = t >>> 1) {
            len++;
        }
        // floor(log10(2^(e + len - 1))) + 1 never exceeds the true exponent
        int k = (((e + len - 1) * 78913) >> 18) + 1;
        if (k >= 0) {
            s.mulPow10(k);
        } else {
            r.mulPow10(-k);
            mp.mulPow10(-k);
            mm.mulPow10(-k);
        }
        while (even ? r.compareSum(mp, s) >= 0 : r.compareSum(mp, s) > 0) {
            s.mul(10);
            k++;
        }
        bt.copy(r);
        int n = 0;
        while (true) {
            r.mul(10);
            mp.mul(10);
            mm.mul(10);
            int dgt = 0;
            while (r.compare(s) >= 0) {
                r.sub(s);
                dgt++;
            }
            int lowCmp = r.compare(mm);
            boolean tc1 = even ? lowCmp <= 0 : lowCmp < 0;
            int highCmp = r.compareSum(mp, s);
            boolean tc2 = even ? highCmp >= 0 : highCmp > 0;
            if (!tc1 && !tc2) {
                digitBuf[n] = (char) ('0' + dgt);
                n++;
                continue;
            }
            if (tc1 && tc2) {
                int half = r.compareSum(r, s);
                if (half > 0 || half == 0 && (dgt & 1) != 0) {
                    dgt++;
                }
            } else if (tc2) {
                dgt++;
            }
            digitBuf[n] = (char) ('0' + dgt);
            n++;
            break;
        }
        if (n == 1) {
            // Java picks the closest decimal of length 1 or 2
            r.copy(bt);
            r.mul(10);
            int d1 = 0;
            while (r.compare(s) >= 0) {
                r.sub(s);
                d1++;
            }
            r.mul(10);
            int d2 = 0;
            while (r.compare(s) >= 0) {
                r.sub(s);
                d2++;
            }
            int half = r.compareSum(r, s);
            if (half > 0 || half == 0 && (d2 & 1) != 0) {
                d2++;
                if (d2 == 10) {
                    d2 = 0;
                    d1++;
                    if (d1 == 10) {
                        d1 = 1;
                        k++;
                    }
                }
            }
            digitBuf[0] = (char) ('0' + d1);
            n = 1;
            if (d2 != 0) {
                digitBuf[1] = (char) ('0' + d2);
                n = 2;
            }
        }
        count = n;
        decExp = k;
    }

    /** Writes {@code Double.toString(a)} into {@link #text}; returns its length. */
    static int format(long a) {
        initBuffers();
        char[] out = text;
        int p = 0;
        int e = exponent(a);
        if (e == EMAX) {
            if ((a & FRAC) != 0) {
                out[0] = 'N';
                out[1] = 'a';
                out[2] = 'N';
                return 3;
            }
            if (sign(a) != 0) {
                out[p] = '-';
                p++;
            }
            String inf = "Infinity";
            for (int i = 0; i < 8; i++) {
                out[p] = inf.charAt(i);
                p++;
            }
            return p;
        }
        if (sign(a) != 0) {
            out[p] = '-';
            p++;
        }
        if (isZero(a)) {
            out[p] = '0';
            out[p + 1] = '.';
            out[p + 2] = '0';
            return p + 3;
        }
        digits(a);
        int n = count;
        int sci = decExp - 1;
        if (sci >= -3 && sci < 7) {
            if (sci >= 0) {
                for (int i = 0; i <= sci; i++) {
                    out[p] = i < n ? digitBuf[i] : '0';
                    p++;
                }
                out[p] = '.';
                p++;
                if (n <= sci + 1) {
                    out[p] = '0';
                    p++;
                } else {
                    for (int i = sci + 1; i < n; i++) {
                        out[p] = digitBuf[i];
                        p++;
                    }
                }
            } else {
                out[p] = '0';
                out[p + 1] = '.';
                p += 2;
                for (int i = -1; i > sci; i--) {
                    out[p] = '0';
                    p++;
                }
                for (int i = 0; i < n; i++) {
                    out[p] = digitBuf[i];
                    p++;
                }
            }
            return p;
        }
        out[p] = digitBuf[0];
        out[p + 1] = '.';
        p += 2;
        if (n == 1) {
            out[p] = '0';
            p++;
        }
        for (int i = 1; i < n; i++) {
            out[p] = digitBuf[i];
            p++;
        }
        out[p] = 'E';
        p++;
        if (sci < 0) {
            out[p] = '-';
            p++;
            sci = -sci;
        }
        int start = p;
        do {
            out[p] = (char) ('0' + sci % 10);
            p++;
            sci = sci / 10;
        } while (sci != 0);
        for (int i = start, j = p - 1; i < j; i++, j--) {
            char t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return p;
    }

    static String toString(long a) {
        int len = format(a);
        return new String(text, 0, len);
    }

    static void print(long a) {
        int len = format(a);
        for (int i = 0; i < len; i++) {
            Bf.putChar(text[i]);
        }
    }

    /**
     * {@code String.format("%.<prec>f", a)} into {@link #text}: like {@code java.util.Formatter},
     * the shortest decimal digits are rounded half up to {@code prec} fraction digits.
     */
    static int formatFixed(long a, int prec) {
        initBuffers();
        int e = exponent(a);
        if (e == EMAX) {
            return format(a);
        }
        char[] out = text;
        int p = 0;
        if (sign(a) != 0) {
            out[p] = '-';
            p++;
        }
        int n = 0;
        int k = 1;
        if (!isZero(a)) {
            digits(a);
            n = count;
            k = decExp;
        }
        // digits D[0..n) with the decimal point after position k; keep k + prec of them
        int keep = k + prec;
        if (keep < 0) {
            n = 0;
        } else if (keep < n) {
            boolean up = digitBuf[keep] >= '5';
            n = keep;
            if (up) {
                int i = n - 1;
                while (i >= 0 && digitBuf[i] == '9') {
                    digitBuf[i] = '0';
                    i--;
                }
                if (i >= 0) {
                    digitBuf[i]++;
                } else {
                    // all nines: 99.96 -> 100.0
                    for (int j = n; j > 0; j--) {
                        digitBuf[j] = digitBuf[j - 1];
                    }
                    digitBuf[0] = '1';
                    n++;
                    k++;
                }
            }
        }
        if (k <= 0) {
            out[p] = '0';
            p++;
        } else {
            for (int i = 0; i < k; i++) {
                out[p] = i < n ? digitBuf[i] : '0';
                p++;
            }
        }
        if (prec > 0) {
            out[p] = '.';
            p++;
            for (int i = 0; i < prec; i++) {
                int idx = k + i;
                out[p] = idx >= 0 && idx < n ? digitBuf[idx] : '0';
                p++;
            }
        }
        return p;
    }

    /** {@code String.format("%.<prec>e", a)}: d.ddde+XX, digits rounded half up. */
    static String toScientific(long a, int prec) {
        initBuffers();
        if (exponent(a) == EMAX) {
            return toString(a);
        }
        if (prec > MANT / 3 + 20) {
            prec = MANT / 3 + 20;
        }
        char[] out = text;
        int p = 0;
        if (sign(a) != 0) {
            out[p] = '-';
            p++;
        }
        int n = 0;
        int k = 1;
        if (!isZero(a)) {
            digits(a);
            n = count;
            k = decExp;
        }
        int keep = prec + 1;
        if (keep < n) {
            boolean up = digitBuf[keep] >= '5';
            n = keep;
            if (up) {
                int i = n - 1;
                while (i >= 0 && digitBuf[i] == '9') {
                    digitBuf[i] = '0';
                    i--;
                }
                if (i >= 0) {
                    digitBuf[i]++;
                } else {
                    digitBuf[0] = '1';
                    k++;
                }
            }
        }
        out[p] = n > 0 ? digitBuf[0] : '0';
        p++;
        if (prec > 0) {
            out[p] = '.';
            p++;
            for (int i = 1; i <= prec; i++) {
                out[p] = i < n ? digitBuf[i] : '0';
                p++;
            }
        }
        int ex = isZero(a) ? 0 : k - 1;
        out[p] = 'e';
        out[p + 1] = ex < 0 ? '-' : '+';
        p += 2;
        if (ex < 0) {
            ex = -ex;
        }
        if (ex >= 100) {
            out[p] = (char) ('0' + ex / 100);
            p++;
        }
        out[p] = (char) ('0' + ex / 10 % 10);
        out[p + 1] = (char) ('0' + ex % 10);
        p += 2;
        return new String(out, 0, p);
    }

    static String toFixed(long a, int prec) {
        if (prec > MANT / 3 + 20) {
            prec = MANT / 3 + 20;
        }
        int len = formatFixed(a, prec);
        return new String(text, 0, len);
    }

    /** {@code Double.parseDouble}, correctly rounded; throws {@code NumberFormatException}. */
    static long parse(String str) {
        initBuffers();
        String s = str.trim();
        int len = s.length();
        int i = 0;
        int sg = 0;
        if (i < len && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            sg = s.charAt(i) == '-' ? 1 : 0;
            i++;
        }
        if (s.startsWith("NaN", i) && i + 3 == len) {
            return NAN;
        }
        if (s.startsWith("Infinity", i) && i + 8 == len) {
            return pack(sg, EMAX, 0);
        }
        Big num = br;
        num.set(0);
        int maxDigits = MANT * 3 / 10 + 25;
        int digitsSeen = 0;
        int kept = 0;
        int q = 0;
        boolean sticky = false;
        boolean point = false;
        boolean any = false;
        while (i < len) {
            char c = s.charAt(i);
            if (c == '.' && !point) {
                point = true;
            } else if (c >= '0' && c <= '9') {
                any = true;
                if (point) {
                    q--;
                }
                if (kept < maxDigits) {
                    if (kept > 0 || c != '0') {
                        num.mul(10);
                        if (num.isZero()) {
                            num.set(c - '0');
                        } else {
                            Big digit = bt;
                            digit.set(c - '0');
                            num.add(digit);
                        }
                        kept++;
                    }
                } else {
                    q++;
                    sticky = sticky || c != '0';
                }
                digitsSeen++;
            } else {
                break;
            }
            i++;
        }
        if (!any) {
            throw new NumberFormatException("For input string: \"" + str + "\"");
        }
        if (i < len && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            int es = 1;
            if (i < len && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                es = s.charAt(i) == '-' ? -1 : 1;
                i++;
            }
            int ev = 0;
            boolean expDigits = false;
            while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                if (ev < 100000) {
                    ev = ev * 10 + (s.charAt(i) - '0');
                }
                expDigits = true;
                i++;
            }
            if (!expDigits) {
                throw new NumberFormatException("For input string: \"" + str + "\"");
            }
            q += es * ev;
        }
        if (i < len && (s.charAt(i) == 'f' || s.charAt(i) == 'F' || s.charAt(i) == 'd'
                        || s.charAt(i) == 'D')) {
            i++;
        }
        if (i != len) {
            throw new NumberFormatException("For input string: \"" + str + "\"");
        }
        if (num.isZero()) {
            return pack(sg, 0, 0);
        }
        // decimal magnitude: 10^(mag-1) <= value < 10^mag
        int mag = kept + q;
        if (mag > (BIAS + 2) * 302 / 1000 + 1) {
            return pack(sg, EMAX, 0);
        }
        if (mag < -((BIAS + MANT + 2) * 302 / 1000) - 1) {
            return pack(sg, 0, 0);
        }
        int want = MANT + 5;
        if (q >= 0) {
            num.mulPow10(q);
            int bits = num.bitLength();
            if (bits <= want) {
                long v = 0;
                for (int j = num.n - 1; j >= 0; j--) {
                    v = v * 256 + num.d[j];
                }
                return fromParts(sg, 0, v, sticky);
            }
            // align the cut to a limb boundary, then read the top limbs
            int cut = bits - want;
            int pad = (8 - (cut & 7)) & 7;
            num.shiftLeft(pad);
            int lowLimbs = (cut + pad) >>> 3;
            long v = 0;
            for (int j = num.n - 1; j >= lowLimbs; j--) {
                v = v * 256 + num.d[j];
            }
            for (int j = 0; j < lowLimbs; j++) {
                sticky = sticky || num.d[j] != 0;
            }
            return fromParts(sg, cut, v, sticky);
        }
        Big den = bs;
        den.set(1);
        den.mulPow10(-q);
        // scale so that the quotient has want or want + 1 bits
        int shift = want + den.bitLength() - num.bitLength();
        if (shift >= 0) {
            num.shiftLeft(shift);
        } else {
            den.shiftLeft(-shift);
        }
        // restoring division: the quotient has at most want + 2 bits
        int qbits = num.bitLength() - den.bitLength() + 1;
        Big top = bp;
        top.copy(den);
        top.shiftLeft(qbits - 1);
        long quotient = 0;
        for (int j = 0; j < qbits; j++) {
            quotient = quotient + quotient;
            if (num.compare(top) >= 0) {
                num.sub(top);
                quotient = quotient | 1;
            }
            num.add(num);
        }
        sticky = sticky || !num.isZero();
        return fromParts(sg, -shift, quotient, sticky);
    }
}
