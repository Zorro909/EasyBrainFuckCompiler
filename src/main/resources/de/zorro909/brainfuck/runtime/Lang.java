// Runtime library: java.lang subset. Compiled together with every program; only the parts a
// program actually reaches end up in the generated Brainfuck. Written in the same Java subset
// the compiler accepts, on top of the intrinsics in class Bf.

final class Bf {
    static native void putChar(int c);
    static native int getChar();
    static native void printInt(int v);
    static native void halt();
    static native int load(int ref, int offset);
    static native void store(int ref, int offset, int value);
    static native int heapStart();
    static native int heapLimit();
    static native int maxInt();
    static native int minInt();
    static native int ref(Object o);
    static native <T> T obj(int ref);
    static native String className(Object o);
    /** Bit pattern of a float or double (one word, no conversion). */
    static native long bits(double d);
    static native long bits(float f);
    static native float toFloat(long bits);
    static native double toDouble(long bits);
    /** Limits of double, which is binary32 below 8-byte words. */
    static native double maxDouble();
    static native double minDouble();
    static native double minNormalDouble();
}

final class Runtime {
    static int hp = Bf.heapStart();
    /** The exception being thrown, from the throw until a catch clause picks it up. */
    static Throwable pending;

    static int alloc(int words) {
        int r = hp;
        hp = hp + words;
        if (hp > Bf.heapLimit() || hp < r) {
            outOfMemory();
        }
        return r;
    }

    static void outOfMemory() {
        printRaw("Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space\n");
        Bf.halt();
    }

    static int newArray(int n) {
        int r = alloc(n + 1);
        Bf.store(r, 0, n);
        return r;
    }

    static int newArray2(int a, int b) {
        int outer = newArray(a);
        for (int i = 1; i <= a; i++) {
            Bf.store(outer, i, newArray(b));
        }
        return outer;
    }

    static int cloneArray(int a) {
        int n = Bf.load(a, 0);
        int r = alloc(n + 1);
        for (int i = 0; i <= n; i++) {
            Bf.store(r, i, Bf.load(a, i));
        }
        return r;
    }

    static void printString(String s) {
        if (s == null) {
            printRaw("null");
            return;
        }
        char[] v = s.value;
        int n = v.length;
        for (int i = 0; i < n; i++) {
            printChar(v[i]);
        }
    }

    static void printChars(char[] v) {
        for (int i = 0; i < v.length; i++) {
            printChar(v[i]);
        }
    }

    /** Writes a char as UTF-8. */
    static void printChar(int c) {
        if (c < 0x80) {
            Bf.putChar(c);
        } else if (c < 0x800) {
            Bf.putChar(0xC0 | (c >> 6));
            Bf.putChar(0x80 | (c & 0x3F));
        } else {
            Bf.putChar(0xE0 | (c >> 12));
            Bf.putChar(0x80 | ((c >> 6) & 0x3F));
            Bf.putChar(0x80 | (c & 0x3F));
        }
    }

    static void printBoolean(boolean b) {
        if (b) {
            printRaw("true");
        } else {
            printRaw("false");
        }
    }

    static void printObject(Object o) {
        printString(String.valueOf(o));
    }

    static native void printRaw(String literal);

    static void classCast(Object o, String target) {
        throw new ClassCastException("class " + o.getClass().getName()
                        + " cannot be cast to class " + target);
    }

    static void divideByZero() {
        throw new ArithmeticException("/ by zero");
    }

    static void uncaught(Throwable t) {
        printRaw("Exception in thread \"main\" ");
        printString(t.toString());
        Bf.putChar('\n');
        Bf.halt();
    }

    static void exit(int status) {
        Bf.halt();
    }
}

class Object {
    public Object() {
    }

    public boolean equals(Object o) {
        return this == o;
    }

    public int hashCode() {
        return Bf.ref(this);
    }

    public String toString() {
        return Bf.className(this) + "@" + Integer.toHexString(hashCode());
    }

    public final Class<?> getClass() {
        return new Class<Object>(Bf.className(this));
    }
}

final class Class<T> {
    final String name;

    Class(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getSimpleName() {
        int i = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
        return name.substring(i + 1);
    }

    public String toString() {
        return "class " + name;
    }
}

interface Comparable<T> {
    int compareTo(T o);
}

interface CharSequence {
    int length();

    char charAt(int index);
}

interface AutoCloseable {
    void close() throws Exception;
}

interface Runnable {
    void run();
}

interface Iterable<T> {
    Iterator<T> iterator();
}

interface Iterator<T> {
    boolean hasNext();

    T next();
}

final class String implements Comparable<String>, CharSequence {
    final char[] value;

    public String() {
        value = new char[0];
    }

    public String(char[] chars) {
        value = new char[chars.length];
        for (int i = 0; i < chars.length; i++) {
            value[i] = chars[i];
        }
    }

    public String(char[] chars, int offset, int count) {
        value = new char[count];
        for (int i = 0; i < count; i++) {
            value[i] = chars[offset + i];
        }
    }

    public String(String s) {
        value = s.value;
    }

    /** Takes ownership of the array (internal). */
    String(char[] chars, boolean shared) {
        value = chars;
    }

    public int length() {
        return value.length;
    }

    public boolean isEmpty() {
        return value.length == 0;
    }

    public boolean isBlank() {
        for (int i = 0; i < value.length; i++) {
            if (!Character.isWhitespace(value[i])) {
                return false;
            }
        }
        return true;
    }

    public char charAt(int index) {
        if (index < 0 || index >= value.length) {
            throw new StringIndexOutOfBoundsException("index " + index + ", length " + value.length);
        }
        return value[index];
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof String)) {
            return false;
        }
        char[] other = ((String) o).value;
        char[] v = value;
        int n = v.length;
        if (other.length != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (v[i] != other[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean equalsIgnoreCase(String o) {
        if (o == null || o.value.length != value.length) {
            return false;
        }
        for (int i = 0; i < value.length; i++) {
            if (Character.toLowerCase(value[i]) != Character.toLowerCase(o.value[i])) {
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        int h = 0;
        char[] v = value;
        for (int i = 0; i < v.length; i++) {
            h = 31 * h + v[i];
        }
        return h;
    }

    public int compareTo(String o) {
        char[] a = value;
        char[] b = o.value;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        return a.length - b.length;
    }

    public int compareToIgnoreCase(String o) {
        return toLowerCase().compareTo(o.toLowerCase());
    }

    public String substring(int begin) {
        return substring(begin, value.length);
    }

    public String substring(int begin, int end) {
        if (begin < 0 || end > value.length || begin > end) {
            throw new StringIndexOutOfBoundsException("begin " + begin + ", end " + end
                            + ", length " + value.length);
        }
        return new String(value, begin, end - begin);
    }

    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    public int indexOf(int ch, int from) {
        for (int i = Math.max(from, 0); i < value.length; i++) {
            if (value[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(String s) {
        return indexOf(s, 0);
    }

    public int indexOf(String s, int from) {
        char[] p = s.value;
        int last = value.length - p.length;
        for (int i = Math.max(from, 0); i <= last; i++) {
            int j = 0;
            while (j < p.length && value[i + j] == p[j]) {
                j++;
            }
            if (j == p.length) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(int ch) {
        for (int i = value.length - 1; i >= 0; i--) {
            if (value[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(CharSequence s) {
        return indexOf(s.toString()) >= 0;
    }

    public boolean startsWith(String p) {
        return startsWith(p, 0);
    }

    public boolean startsWith(String p, int from) {
        if (from < 0 || p.value.length > value.length - from) {
            return false;
        }
        for (int i = 0; i < p.value.length; i++) {
            if (value[from + i] != p.value[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean endsWith(String p) {
        int start = value.length - p.value.length;
        if (start < 0) {
            return false;
        }
        for (int i = 0; i < p.value.length; i++) {
            if (value[start + i] != p.value[i]) {
                return false;
            }
        }
        return true;
    }

    public String concat(String s) {
        char[] r = new char[value.length + s.value.length];
        for (int i = 0; i < value.length; i++) {
            r[i] = value[i];
        }
        for (int i = 0; i < s.value.length; i++) {
            r[value.length + i] = s.value[i];
        }
        return new String(r, true);
    }

    public String toUpperCase() {
        char[] r = new char[value.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = Character.toUpperCase(value[i]);
        }
        return new String(r, true);
    }

    public String toLowerCase() {
        char[] r = new char[value.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = Character.toLowerCase(value[i]);
        }
        return new String(r, true);
    }

    public String trim() {
        int b = 0;
        int e = value.length;
        while (b < e && value[b] <= ' ') {
            b++;
        }
        while (e > b && value[e - 1] <= ' ') {
            e--;
        }
        return substring(b, e);
    }

    public String strip() {
        return trim();
    }

    public char[] toCharArray() {
        char[] r = new char[value.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = value[i];
        }
        return r;
    }

    public String replace(char from, char to) {
        char[] r = new char[value.length];
        for (int i = 0; i < r.length; i++) {
            char c = value[i];
            r[i] = c == from ? to : c;
        }
        return new String(r, true);
    }

    public String replace(CharSequence target, CharSequence replacement) {
        String t = target.toString();
        String rep = replacement.toString();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (true) {
            int j = t.isEmpty() ? -1 : indexOf(t, i);
            if (j < 0) {
                break;
            }
            sb.append(value, i, j - i);
            sb.append(rep);
            i = j + t.length();
        }
        sb.append(value, i, value.length - i);
        return sb.toString();
    }

    public String repeat(int count) {
        char[] r = new char[value.length * count];
        int k = 0;
        for (int c = 0; c < count; c++) {
            for (int i = 0; i < value.length; i++) {
                r[k] = value[i];
                k++;
            }
        }
        return new String(r, true);
    }

    /**
     * Splits around a literal separator. The regex forms {@code " +"} and {@code "\\s+"} are
     * understood as "runs of whitespace"; other patterns are treated literally.
     */
    public String[] split(String sep) {
        ArrayList<String> parts = new ArrayList<String>();
        boolean whitespace = sep.equals("\\s+") || sep.equals(" +") || sep.equals("\\s");
        int i = 0;
        int n = value.length;
        if (whitespace) {
            while (i < n) {
                int start = i;
                while (i < n && !Character.isWhitespace(value[i])) {
                    i++;
                }
                if (i > start || start == 0) {
                    parts.add(substring(start, i));
                }
                while (i < n && Character.isWhitespace(value[i])) {
                    i++;
                }
            }
        } else if (sep.isEmpty()) {
            for (int k = 0; k < n; k++) {
                parts.add(String.valueOf(value[k]));
            }
        } else {
            while (true) {
                int j = indexOf(sep, i);
                if (j < 0) {
                    break;
                }
                parts.add(substring(i, j));
                i = j + sep.length();
            }
            parts.add(substring(i, n));
        }
        int size = parts.size();
        while (size > 0 && parts.get(size - 1).isEmpty()) {
            size--;
        }
        String[] r = new String[size];
        for (int k = 0; k < size; k++) {
            r[k] = parts.get(k);
        }
        return r;
    }

    public String toString() {
        return this;
    }

    public static String valueOf(int i) {
        return Integer.toString(i);
    }

    public static String valueOf(long l) {
        return Integer.toString((int) l);
    }

    public static String valueOf(char c) {
        char[] r = new char[1];
        r[0] = c;
        return new String(r, true);
    }

    public static String valueOf(boolean b) {
        return b ? "true" : "false";
    }

    public static String valueOf(double d) {
        return DoubleOps.toString(Bf.bits(d));
    }

    public static String valueOf(float f) {
        return FloatOps.toString(Bf.bits(f));
    }

    public static String valueOf(char[] data) {
        return new String(data);
    }

    public static String valueOf(Object o) {
        return o == null ? "null" : o.toString();
    }

    public static String join(CharSequence sep, Iterable<String> parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (!first) {
                sb.append(sep.toString());
            }
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }
}

final class StringBuilder implements CharSequence {
    char[] buf;
    int len;

    public StringBuilder() {
        buf = new char[16];
    }

    public StringBuilder(int capacity) {
        buf = new char[Math.max(capacity, 1)];
    }

    public StringBuilder(String s) {
        buf = new char[s.length() + 16];
        append(s);
    }

    private void ensure(int extra) {
        int need = len + extra;
        if (need > buf.length) {
            int cap = buf.length * 2;
            if (cap < need) {
                cap = need;
            }
            char[] nb = new char[cap];
            for (int i = 0; i < len; i++) {
                nb[i] = buf[i];
            }
            buf = nb;
        }
    }

    public StringBuilder append(String s) {
        if (s == null) {
            s = "null";
        }
        char[] v = s.value;
        return append(v, 0, v.length);
    }

    public StringBuilder append(char[] v, int offset, int count) {
        ensure(count);
        for (int i = 0; i < count; i++) {
            buf[len] = v[offset + i];
            len++;
        }
        return this;
    }

    public StringBuilder append(char[] v) {
        return append(v, 0, v.length);
    }

    public StringBuilder append(char c) {
        ensure(1);
        buf[len] = c;
        len++;
        return this;
    }

    public StringBuilder append(int i) {
        return append(Integer.toString(i));
    }

    public StringBuilder append(long l) {
        return append(Integer.toString((int) l));
    }

    public StringBuilder append(boolean b) {
        return append(b ? "true" : "false");
    }

    public StringBuilder append(double d) {
        int n = DoubleOps.format(Bf.bits(d));
        ensure(n);
        for (int i = 0; i < n; i++) {
            buf[len] = DoubleOps.text[i];
            len++;
        }
        return this;
    }

    public StringBuilder append(float f) {
        int n = FloatOps.format(Bf.bits(f));
        ensure(n);
        for (int i = 0; i < n; i++) {
            buf[len] = FloatOps.text[i];
            len++;
        }
        return this;
    }

    public StringBuilder append(Object o) {
        return append(String.valueOf(o));
    }

    public StringBuilder append(CharSequence s) {
        return append(String.valueOf(s));
    }

    public StringBuilder insert(int offset, String s) {
        String tail = substring(offset);
        len = offset;
        append(s);
        append(tail);
        return this;
    }

    public StringBuilder insert(int offset, char c) {
        return insert(offset, String.valueOf(c));
    }

    public StringBuilder insert(int offset, int i) {
        return insert(offset, Integer.toString(i));
    }

    public int length() {
        return len;
    }

    public boolean isEmpty() {
        return len == 0;
    }

    public char charAt(int i) {
        if (i < 0 || i >= len) {
            throw new StringIndexOutOfBoundsException("index " + i + ",length " + len);
        }
        return buf[i];
    }

    public void setCharAt(int i, char c) {
        buf[i] = c;
    }

    public void setLength(int n) {
        ensure(n - len);
        for (int i = len; i < n; i++) {
            buf[i] = 0;
        }
        len = n;
    }

    public StringBuilder deleteCharAt(int i) {
        for (int k = i; k < len - 1; k++) {
            buf[k] = buf[k + 1];
        }
        len--;
        return this;
    }

    public StringBuilder delete(int start, int end) {
        if (end > len) {
            end = len;
        }
        int d = end - start;
        for (int k = start; k + d < len; k++) {
            buf[k] = buf[k + d];
        }
        len -= d;
        return this;
    }

    public StringBuilder reverse() {
        for (int i = 0, j = len - 1; i < j; i++, j--) {
            char t = buf[i];
            buf[i] = buf[j];
            buf[j] = t;
        }
        return this;
    }

    public int indexOf(String s) {
        return toString().indexOf(s);
    }

    public String substring(int begin) {
        return new String(buf, begin, len - begin);
    }

    public String substring(int begin, int end) {
        return new String(buf, begin, end - begin);
    }

    public String toString() {
        return new String(buf, 0, len);
    }
}

abstract class Number {
    public abstract int intValue();

    public long longValue() {
        return intValue();
    }

    public double doubleValue() {
        return longValue();
    }

    public float floatValue() {
        return longValue();
    }
}

final class Double extends Number implements Comparable<Double> {
    public static final double POSITIVE_INFINITY = 1.0 / 0.0;
    public static final double NEGATIVE_INFINITY = -1.0 / 0.0;
    public static final double NaN = 0.0d / 0.0;
    public static final double MAX_VALUE = Bf.maxDouble();
    public static final double MIN_VALUE = Bf.minDouble();
    public static final double MIN_NORMAL = Bf.minNormalDouble();

    final double value;

    public Double(double value) {
        this.value = value;
    }

    public static Double valueOf(double d) {
        return new Double(d);
    }

    public static Double valueOf(String s) {
        return new Double(parseDouble(s));
    }

    public static double parseDouble(String s) {
        return Bf.toDouble(DoubleOps.parse(s));
    }

    public static String toString(double d) {
        return DoubleOps.toString(Bf.bits(d));
    }

    public static boolean isNaN(double d) {
        return d != d;
    }

    public static boolean isInfinite(double d) {
        return DoubleOps.isInfinite(Bf.bits(d));
    }

    public static boolean isFinite(double d) {
        return DoubleOps.exponent(Bf.bits(d)) != DoubleOps.EMAX;
    }

    public static int compare(double a, double b) {
        return DoubleOps.compare(Bf.bits(a), Bf.bits(b));
    }

    public static long doubleToRawLongBits(double d) {
        return Bf.bits(d);
    }

    public static long doubleToLongBits(double d) {
        return d != d ? DoubleOps.NAN : Bf.bits(d);
    }

    public static double longBitsToDouble(long bits) {
        return Bf.toDouble(bits);
    }

    public static int hashCode(double d) {
        long bits = doubleToLongBits(d);
        if (Bf.maxInt() == 2147483647) {
            return (int) bits;
        }
        return (int) (bits ^ (bits >>> 32));
    }

    public static double sum(double a, double b) {
        return a + b;
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    public boolean isNaN() {
        return value != value;
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return (long) value;
    }

    public double doubleValue() {
        return value;
    }

    public float floatValue() {
        return (float) value;
    }

    public int compareTo(Double o) {
        return compare(value, o.value);
    }

    public boolean equals(Object o) {
        return o instanceof Double d && compare(value, d.value) == 0;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public String toString() {
        return toString(value);
    }
}

final class Float extends Number implements Comparable<Float> {
    public static final float POSITIVE_INFINITY = 1.0f / 0.0f;
    public static final float NEGATIVE_INFINITY = -1.0f / 0.0f;
    public static final float NaN = 0.0f / 0.0f;
    public static final float MAX_VALUE = 3.4028235e+38f;
    public static final float MIN_VALUE = 1.4e-45f;
    public static final float MIN_NORMAL = 1.17549435E-38f;

    final float value;

    public Float(float value) {
        this.value = value;
    }

    public static Float valueOf(float f) {
        return new Float(f);
    }

    public static Float valueOf(String s) {
        return new Float(parseFloat(s));
    }

    public static float parseFloat(String s) {
        return Bf.toFloat(FloatOps.parse(s));
    }

    public static String toString(float f) {
        return FloatOps.toString(Bf.bits(f));
    }

    public static boolean isNaN(float f) {
        return f != f;
    }

    public static boolean isInfinite(float f) {
        return FloatOps.isInfinite(Bf.bits(f));
    }

    public static boolean isFinite(float f) {
        return FloatOps.exponent(Bf.bits(f)) != FloatOps.EMAX;
    }

    public static int compare(float a, float b) {
        return FloatOps.compare(Bf.bits(a), Bf.bits(b));
    }

    /** The bit pattern as Java's 32-bit int (sign-extended on wider words). */
    public static int floatToRawIntBits(float f) {
        long bits = Bf.bits(f);
        if (Bf.maxInt() != 2147483647 && bits >= 2147483648L) {
            bits = bits - 4294967296L;
        }
        return (int) bits;
    }

    public static int floatToIntBits(float f) {
        return f != f ? 0x7fc00000 : floatToRawIntBits(f);
    }

    public static float intBitsToFloat(int bits) {
        return Bf.toFloat(bits & 0xFFFFFFFFL);
    }

    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    public static float sum(float a, float b) {
        return a + b;
    }

    public boolean isNaN() {
        return value != value;
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return (long) value;
    }

    public double doubleValue() {
        return value;
    }

    public float floatValue() {
        return value;
    }

    public int compareTo(Float o) {
        return compare(value, o.value);
    }

    public boolean equals(Object o) {
        return o instanceof Float f && compare(value, f.value) == 0;
    }

    public int hashCode() {
        return floatToIntBits(value);
    }

    public String toString() {
        return toString(value);
    }
}

final class Integer extends Number implements Comparable<Integer> {
    public static final int MAX_VALUE = Bf.maxInt();
    public static final int MIN_VALUE = Bf.minInt();

    final int value;

    public Integer(int value) {
        this.value = value;
    }

    /** Boxes of -128..127, created on first use; keeps {@code ==} on small boxes Java-like. */
    static Integer[] cache;

    public static Integer valueOf(int i) {
        if (i < -128 || i > 127) {
            return new Integer(i);
        }
        if (cache == null) {
            cache = new Integer[256];
        }
        Integer c = cache[i + 128];
        if (c == null) {
            c = new Integer(i);
            cache[i + 128] = c;
        }
        return c;
    }

    public static Integer valueOf(String s) {
        return new Integer(parseInt(s));
    }

    public int intValue() {
        return value;
    }

    public boolean equals(Object o) {
        return o instanceof Integer && ((Integer) o).value == value;
    }

    public int hashCode() {
        return value;
    }

    public int compareTo(Integer o) {
        return compare(value, o.value);
    }

    public String toString() {
        return toString(value);
    }

    public static int compare(int a, int b) {
        return a < b ? -1 : (a == b ? 0 : 1);
    }

    public static int signum(int i) {
        return i > 0 ? 1 : (i < 0 ? -1 : 0);
    }

    public static int max(int a, int b) {
        return a >= b ? a : b;
    }

    public static int min(int a, int b) {
        return a <= b ? a : b;
    }

    public static int sum(int a, int b) {
        return a + b;
    }

    public static int abs(int a) {
        return a < 0 ? -a : a;
    }

    public static int parseInt(String s) {
        return parseInt(s, 10);
    }

    public static int parseInt(String s, int radix) {
        if (s == null || s.isEmpty()) {
            throw new NumberFormatException("For input string: \"" + s + "\"");
        }
        char[] v = s.value;
        int i = 0;
        boolean neg = false;
        if (v[0] == '-' || v[0] == '+') {
            neg = v[0] == '-';
            i = 1;
            if (v.length == 1) {
                throw new NumberFormatException("For input string: \"" + s + "\"");
            }
        }
        int r = 0;
        for (; i < v.length; i++) {
            int d = Character.digit(v[i], radix);
            if (d < 0) {
                throw new NumberFormatException("For input string: \"" + s + "\"");
            }
            r = r * radix - d;
        }
        return neg ? r : -r;
    }

    public static String toString(int i) {
        if (i == 0) {
            return "0";
        }
        char[] buf = new char[12];
        int pos = 12;
        boolean neg = i < 0;
        // work with negative numbers so MIN_VALUE needs no special case
        if (!neg) {
            i = -i;
        }
        while (i != 0) {
            int q = i / 10;
            pos--;
            buf[pos] = (char) ('0' + (q * 10 - i));
            i = q;
        }
        if (neg) {
            pos--;
            buf[pos] = '-';
        }
        return new String(buf, pos, 12 - pos);
    }

    public static String toString(int i, int radix) {
        if (radix == 10) {
            return toString(i);
        }
        if (i == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        boolean neg = i < 0;
        if (!neg) {
            i = -i;
        }
        while (i != 0) {
            int q = i / radix;
            sb.append(Character.forDigit(q * radix - i, radix));
            i = q;
        }
        if (neg) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }

    public static String toBinaryString(int i) {
        return toUnsigned(i, 1);
    }

    public static String toHexString(int i) {
        return toUnsigned(i, 4);
    }

    public static String toOctalString(int i) {
        return toUnsigned(i, 3);
    }

    private static String toUnsigned(int i, int shift) {
        if (i == 0) {
            return "0";
        }
        int mask = (1 << shift) - 1;
        StringBuilder sb = new StringBuilder();
        while (i != 0) {
            sb.append(Character.forDigit(i & mask, 16));
            i = i >>> shift;
        }
        return sb.reverse().toString();
    }

    public static int bitCount(int i) {
        int c = 0;
        while (i != 0) {
            i = i & (i - 1);
            c++;
        }
        return c;
    }
}

final class Long extends Number implements Comparable<Long> {
    public static final long MAX_VALUE = Bf.maxInt();
    public static final long MIN_VALUE = Bf.minInt();

    final long value;

    public Long(long value) {
        this.value = value;
    }

    public static Long valueOf(long l) {
        return new Long(l);
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return value;
    }

    public static long parseLong(String s) {
        return Integer.parseInt(s);
    }

    public boolean equals(Object o) {
        return o instanceof Long && ((Long) o).value == value;
    }

    public int hashCode() {
        return (int) value;
    }

    public int compareTo(Long o) {
        return compare(value, o.value);
    }

    public static int compare(long a, long b) {
        return a < b ? -1 : (a == b ? 0 : 1);
    }

    public String toString() {
        return Integer.toString((int) value);
    }

    public static String toString(long l) {
        return Integer.toString((int) l);
    }

    public static long max(long a, long b) {
        return a >= b ? a : b;
    }

    public static long min(long a, long b) {
        return a <= b ? a : b;
    }
}

final class Short extends Number {
    final short value;

    public Short(short value) {
        this.value = value;
    }

    public static Short valueOf(short s) {
        return new Short(s);
    }

    public int intValue() {
        return value;
    }

    public boolean equals(Object o) {
        return o instanceof Short && ((Short) o).value == value;
    }

    public int hashCode() {
        return value;
    }

    public String toString() {
        return Integer.toString(value);
    }
}

final class Byte extends Number {
    final byte value;

    public Byte(byte value) {
        this.value = value;
    }

    public static Byte valueOf(byte b) {
        return new Byte(b);
    }

    public int intValue() {
        return value;
    }

    public boolean equals(Object o) {
        return o instanceof Byte && ((Byte) o).value == value;
    }

    public int hashCode() {
        return value;
    }

    public String toString() {
        return Integer.toString(value);
    }
}

final class Character implements Comparable<Character> {
    public static final char MIN_VALUE = 0;
    public static final char MAX_VALUE = 65535;

    final char value;

    public Character(char value) {
        this.value = value;
    }

    static Character[] cache;

    public static Character valueOf(char c) {
        if (c > 127) {
            return new Character(c);
        }
        if (cache == null) {
            cache = new Character[128];
        }
        Character b = cache[c];
        if (b == null) {
            b = new Character(c);
            cache[c] = b;
        }
        return b;
    }

    public char charValue() {
        return value;
    }

    public boolean equals(Object o) {
        return o instanceof Character && ((Character) o).value == value;
    }

    public int hashCode() {
        return value;
    }

    public int compareTo(Character o) {
        return value - o.value;
    }

    public static int compare(char a, char b) {
        return a - b;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(char c) {
        return String.valueOf(c);
    }

    public static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static boolean isLetter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= 0xC0 && c != 0xD7 && c != 0xF7;
    }

    public static boolean isAlphabetic(int c) {
        return isLetter((char) c);
    }

    public static boolean isLetterOrDigit(char c) {
        return isLetter(c) || isDigit(c);
    }

    public static boolean isUpperCase(char c) {
        return c >= 'A' && c <= 'Z' || c >= 0xC0 && c <= 0xDE && c != 0xD7;
    }

    public static boolean isLowerCase(char c) {
        return c >= 'a' && c <= 'z' || c >= 0xDF && c <= 0xFF && c != 0xF7;
    }

    public static boolean isWhitespace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\f' || c == 11;
    }

    public static boolean isSpaceChar(char c) {
        return c == ' ';
    }

    public static char toUpperCase(char c) {
        if (c >= 'a' && c <= 'z' || c >= 0xE0 && c <= 0xFE && c != 0xF7) {
            return (char) (c - 32);
        }
        return c;
    }

    public static char toLowerCase(char c) {
        if (c >= 'A' && c <= 'Z' || c >= 0xC0 && c <= 0xDE && c != 0xD7) {
            return (char) (c + 32);
        }
        return c;
    }

    public static int getNumericValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        return -1;
    }

    public static int digit(char c, int radix) {
        int d = getNumericValue(c);
        return d < radix ? d : -1;
    }

    public static char forDigit(int d, int radix) {
        if (d < 0 || d >= radix) {
            return 0;
        }
        return (char) (d < 10 ? '0' + d : 'a' + d - 10);
    }
}

final class Boolean implements Comparable<Boolean> {
    public static final Boolean TRUE = new Boolean(true);
    public static final Boolean FALSE = new Boolean(false);

    final boolean value;

    public Boolean(boolean value) {
        this.value = value;
    }

    public static Boolean valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static Boolean valueOf(String s) {
        return parseBoolean(s) ? TRUE : FALSE;
    }

    public boolean booleanValue() {
        return value;
    }

    public static boolean parseBoolean(String s) {
        return s != null && s.equalsIgnoreCase("true");
    }

    public boolean equals(Object o) {
        return o instanceof Boolean && ((Boolean) o).value == value;
    }

    public int hashCode() {
        return value ? 1231 : 1237;
    }

    public int compareTo(Boolean o) {
        return (value ? 1 : 0) - (o.value ? 1 : 0);
    }

    public String toString() {
        return value ? "true" : "false";
    }

    public static String toString(boolean b) {
        return b ? "true" : "false";
    }
}

final class Math {
    public static int abs(int a) {
        return a < 0 ? -a : a;
    }

    public static int max(int a, int b) {
        return a >= b ? a : b;
    }

    public static int min(int a, int b) {
        return a <= b ? a : b;
    }

    public static int floorDiv(int a, int b) {
        int q = a / b;
        if ((a % b != 0) && ((a < 0) != (b < 0))) {
            q--;
        }
        return q;
    }

    public static int floorMod(int a, int b) {
        int m = a % b;
        if (m != 0 && ((m < 0) != (b < 0))) {
            m += b;
        }
        return m;
    }

    public static int signum(int a) {
        return a > 0 ? 1 : (a < 0 ? -1 : 0);
    }

    public static final double PI = 3.141592653589793;
    public static final double E = 2.718281828459045;
    /** double is binary32 below 8-byte words: argument reductions need other splits. */
    static final boolean SINGLE = Bf.maxDouble() < 1e39;
    static final double LN2_HI = SINGLE ? 0.693138122558593750 : 6.93147180369123816490e-01;
    static final double LN2_LO = SINGLE ? 9.0580006145e-06 : 1.90821492927058770002e-10;
    static final double PIO2_1 = SINGLE ? 1.5707855225 : 1.57079632673412561417e+00;
    static final double PIO2_2 = SINGLE ? 1.0804273188e-05 : 6.07710050650619224932e-11;
    static final double PIO2_3 = SINGLE ? 6.0770999344e-11 : 2.02226624871116645580e-21;
    static Random random;

    public static double abs(double a) {
        return Bf.toDouble(DoubleOps.abs(Bf.bits(a)));
    }

    public static float abs(float a) {
        return Bf.toFloat(FloatOps.abs(Bf.bits(a)));
    }

    public static double max(double a, double b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0 && b == 0.0) {
            return Bf.bits(a) != 0 ? b : a;
        }
        return a >= b ? a : b;
    }

    public static double min(double a, double b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0 && b == 0.0) {
            return Bf.bits(a) != 0 ? a : b;
        }
        return a <= b ? a : b;
    }

    public static float max(float a, float b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0f && b == 0.0f) {
            return Bf.bits(a) != 0 ? b : a;
        }
        return a >= b ? a : b;
    }

    public static float min(float a, float b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0f && b == 0.0f) {
            return Bf.bits(a) != 0 ? a : b;
        }
        return a <= b ? a : b;
    }

    public static double sqrt(double a) {
        return Bf.toDouble(DoubleOps.sqrt(Bf.bits(a)));
    }

    public static double cbrt(double a) {
        if (a == 0.0 || a != a || Double.isInfinite(a)) {
            return a;
        }
        double x = a < 0 ? -a : a;
        double y = exp(log(x) / 3);
        for (int i = 0; i < 4; i++) {
            y = y - (y * y * y - x) / (3 * y * y);
        }
        return a < 0 ? -y : y;
    }

    public static double floor(double a) {
        return Bf.toDouble(DoubleOps.floor(Bf.bits(a)));
    }

    public static double ceil(double a) {
        return Bf.toDouble(DoubleOps.ceil(Bf.bits(a)));
    }

    public static double rint(double a) {
        return Bf.toDouble(DoubleOps.rint(Bf.bits(a)));
    }

    public static long round(double a) {
        return DoubleOps.round(Bf.bits(a));
    }

    public static int round(float a) {
        return (int) FloatOps.round(Bf.bits(a));
    }

    public static double signum(double a) {
        return a > 0 ? 1.0 : a < 0 ? -1.0 : a;
    }

    public static double hypot(double x, double y) {
        return sqrt(x * x + y * y);
    }

    public static double toRadians(double deg) {
        return deg * 0.017453292519943295;
    }

    public static double toDegrees(double rad) {
        return rad * 57.29577951308232;
    }

    public static double random() {
        if (random == null) {
            random = new Random();
        }
        return random.nextDouble();
    }

    public static double scalb(double a, int n) {
        return Bf.toDouble(DoubleOps.scalb(Bf.bits(a), n));
    }

    public static double exp(double x) {
        if (x != x) {
            return x;
        }
        if (x > 1000) {
            return Double.POSITIVE_INFINITY;
        }
        if (x < -1000) {
            return 0.0;
        }
        // x = k ln2 + r with |r| <= ln2 / 2, exp(x) = 2^k exp(r)
        double k = rint(x * 1.4426950408889634);
        double r = x - k * LN2_HI - k * LN2_LO;
        double p = 1.0;
        for (int n = 17; n >= 1; n--) {
            p = 1.0 + r * p / n;
        }
        return scalb(p, (int) k);
    }

    public static double log(double x) {
        if (x != x || x < 0) {
            return Double.NaN;
        }
        if (x == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (Double.isInfinite(x)) {
            return x;
        }
        int e = 0;
        if (x < Double.MIN_NORMAL) {
            x = scalb(x, 60);
            e = -60;
        }
        long bits = Bf.bits(x);
        e += DoubleOps.exponent(bits) - DoubleOps.BIAS;
        double m = Bf.toDouble(DoubleOps.pack(0, DoubleOps.BIAS, bits & DoubleOps.FRAC));
        if (m > 1.4142135623730951) {
            m = m / 2;
            e++;
        }
        // log(m) = 2 atanh(s) = 2 (s + s^3/3 + s^5/5 + ...), s = (m - 1) / (m + 1)
        double s = (m - 1) / (m + 1);
        double z = s * s;
        double p = 0.0;
        for (int n = 23; n >= 1; n -= 2) {
            p = 1.0 / n + z * p;
        }
        return e * LN2_HI + (e * LN2_LO + 2 * s * p);
    }

    public static double log10(double x) {
        double r = log(x) * 0.4342944819032518;
        // exact results for powers of ten, like the JDK
        double n = rint(r);
        if (abs(r - n) < 1e-6 && n >= 0 && n <= 22) {
            double p = 1.0;
            for (int i = 0; i < (int) n; i++) {
                p = p * 10;
            }
            if (p == x) {
                return n;
            }
        }
        return r;
    }

    public static double log1p(double x) {
        return log(1 + x);
    }

    public static double expm1(double x) {
        return exp(x) - 1;
    }

    /** sin(r) for |r| <= pi/4. */
    static double sinKernel(double r) {
        double z = r * r;
        return r + r * z * (-1.66666666666666324348e-01 + z * (8.33333333332248946124e-03 + z
                        * (-1.98412698298579493134e-04 + z * (2.75573137070700676789e-06 + z
                                        * (-2.50507602534068634195e-08 + z
                                                        * 1.58969099521155010221e-10)))));
    }

    /** cos(r) for |r| <= pi/4. */
    static double cosKernel(double r) {
        double z = r * r;
        return 1.0 - 0.5 * z + z * z * (4.16666666666666019037e-02 + z
                        * (-1.38888888888741095749e-03 + z * (2.48015872894767294178e-05 + z
                                        * (-2.75573143513906633035e-07 + z
                                                        * (2.08757232129817482790e-09 + z
                                                                        * -1.13596475577881948265e-11)))));
    }

    /** Quadrant of x modulo pi/2 in the low two bits, remainder in {@code reduced}. */
    static double reduced;

    static int reduce(double x) {
        double q = rint(x * 0.6366197723675814);
        reduced = x - q * PIO2_1 - q * PIO2_2 - q * PIO2_3;
        return (int) q & 3;
    }

    public static double sin(double x) {
        if (x != x || Double.isInfinite(x)) {
            return Double.NaN;
        }
        int n = reduce(x);
        double r = reduced;
        return switch (n) {
            case 0 -> sinKernel(r);
            case 1 -> cosKernel(r);
            case 2 -> -sinKernel(r);
            default -> -cosKernel(r);
        };
    }

    public static double cos(double x) {
        if (x != x || Double.isInfinite(x)) {
            return Double.NaN;
        }
        int n = reduce(x);
        double r = reduced;
        return switch (n) {
            case 0 -> cosKernel(r);
            case 1 -> -sinKernel(r);
            case 2 -> -cosKernel(r);
            default -> sinKernel(r);
        };
    }

    public static double tan(double x) {
        return sin(x) / cos(x);
    }

    /** atan(z) for |z| <= tan(pi/8). */
    static double atanKernel(double z) {
        double w = z * z;
        double p = 0.0;
        for (int n = 47; n >= 1; n -= 2) {
            p = ((n & 2) == 0 ? 1.0 : -1.0) / n + w * p;
        }
        return z * p;
    }

    public static double atan(double x) {
        if (x != x) {
            return x;
        }
        boolean neg = x < 0;
        if (neg) {
            x = -x;
        }
        double r;
        if (x > 2.414213562373095) {
            r = 1.5707963267948966 - atanKernel(1 / x);
        } else if (x > 0.41421356237309503) {
            r = 0.7853981633974483 + atanKernel((x - 1) / (x + 1));
        } else {
            r = atanKernel(x);
        }
        return neg ? -r : r;
    }

    public static double atan2(double y, double x) {
        if (x != x || y != y) {
            return Double.NaN;
        }
        if (Double.isInfinite(x) || Double.isInfinite(y)) {
            if (Double.isInfinite(x) && Double.isInfinite(y)) {
                double a = x > 0 ? 0.7853981633974483 : 2.356194490192345;
                return y > 0 ? a : -a;
            }
            if (Double.isInfinite(y)) {
                return y > 0 ? 1.5707963267948966 : -1.5707963267948966;
            }
            boolean ny = Bf.bits(y) != 0 && y <= 0;
            if (x > 0) {
                return ny ? -0.0 : 0.0;
            }
            return ny ? -PI : PI;
        }
        boolean negY = DoubleOps.sign(Bf.bits(y)) != 0;
        boolean negX = DoubleOps.sign(Bf.bits(x)) != 0;
        if (x == 0) {
            if (y == 0) {
                return negX ? (negY ? -PI : PI) : y;
            }
            return negY ? -1.5707963267948966 : 1.5707963267948966;
        }
        double a = atan(y / x);
        if (!negX) {
            return a;
        }
        return negY ? a - PI : a + PI;
    }

    public static double asin(double x) {
        return atan2(x, sqrt((1 - x) * (1 + x)));
    }

    public static double acos(double x) {
        return atan2(sqrt((1 - x) * (1 + x)), x);
    }

    public static double sinh(double x) {
        double e = exp(x);
        return (e - 1 / e) / 2;
    }

    public static double cosh(double x) {
        double e = exp(x);
        return (e + 1 / e) / 2;
    }

    public static double tanh(double x) {
        if (x > 40) {
            return 1.0;
        }
        if (x < -40) {
            return -1.0;
        }
        double e = exp(2 * x);
        return (e - 1) / (e + 1);
    }

    public static double pow(double x, double y) {
        if (y == 0) {
            return 1.0;
        }
        if (y != y || x != x) {
            return Double.NaN;
        }
        if (y == 1) {
            return x;
        }
        if (y == rint(y) && abs(y) < 2147483647.0) {
            // integer exponent: repeated squaring
            long n = (long) abs(y);
            double r = 1.0;
            double b = x;
            while (n > 0) {
                if ((n & 1) != 0) {
                    r = r * b;
                }
                b = b * b;
                n = n >> 1;
            }
            return y < 0 ? 1.0 / r : r;
        }
        if (x == 0) {
            return y > 0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        if (Double.isInfinite(x)) {
            return y > 0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        if (x < 0) {
            return Double.NaN;
        }
        if (y == 0.5) {
            return sqrt(x);
        }
        return exp(y * log(x));
    }

    public static int addExact(int a, int b) {
        return a + b;
    }

    public static int multiplyExact(int a, int b) {
        return a * b;
    }
}

final class System {
    public static final InputStream in = null;

    public static void exit(int status) {
        Bf.halt();
    }

    public static long currentTimeMillis() {
        return 0;
    }

    public static long nanoTime() {
        return 0;
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        int s = Bf.ref(src);
        int d = Bf.ref(dest);
        if (s == d && srcPos < destPos) {
            for (int i = length - 1; i >= 0; i--) {
                Bf.store(d, destPos + 1 + i, Bf.load(s, srcPos + 1 + i));
            }
        } else {
            for (int i = 0; i < length; i++) {
                Bf.store(d, destPos + 1 + i, Bf.load(s, srcPos + 1 + i));
            }
        }
    }

    public static int identityHashCode(Object o) {
        return Bf.ref(o);
    }
}

final class Fmt {
    static String pad(String s, int width, boolean left, char fill) {
        int n = s.length();
        if (n >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        if (left) {
            sb.append(s);
            for (int i = n; i < width; i++) {
                sb.append(' ');
            }
            return sb.toString();
        }
        int start = 0;
        if (fill == '0' && n > 0 && s.charAt(0) == '-') {
            sb.append('-');
            start = 1;
        }
        for (int i = n; i < width; i++) {
            sb.append(fill);
        }
        sb.append(s.substring(start));
        return sb.toString();
    }
}

abstract class Enum<E> implements Comparable<E> {
    String name;
    int ordinal;

    public final String name() {
        return name;
    }

    public final int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    public int compareTo(E o) {
        return ordinal - ((Enum) o).ordinal;
    }

    public boolean equals(Object o) {
        return this == o;
    }

    public int hashCode() {
        return ordinal;
    }
}

abstract class Record {
}

class Throwable {
    String message;
    Throwable cause;

    public Throwable() {
    }

    public Throwable(String message) {
        this.message = message;
    }

    public Throwable(String message, Throwable cause) {
        this.message = message;
        this.cause = cause;
    }

    /** Suppression and stack traces do not exist here; the flags are accepted and ignored. */
    protected Throwable(String message, Throwable cause, boolean enableSuppression,
                    boolean writableStackTrace) {
        this.message = message;
        this.cause = cause;
    }

    public Throwable(Throwable cause) {
        this.message = cause == null ? null : cause.toString();
        this.cause = cause;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    public void printStackTrace() {
        Runtime.printString(toString());
        Bf.putChar('\n');
    }

    public String toString() {
        String n = Bf.className(this);
        return message == null ? n : n + ": " + message;
    }
}

class Exception extends Throwable {
    public Exception() {
    }

    public Exception(String m) {
        super(m);
    }

    public Exception(String m, Throwable c) {
        super(m, c);
    }

    protected Exception(String m, Throwable c, boolean suppression, boolean writable) {
        super(m, c, suppression, writable);
    }
}

class Error extends Throwable {
    public Error() {
    }

    public Error(String m) {
        super(m);
    }
}

class AssertionError extends Error {
    public AssertionError() {
    }

    public AssertionError(String m) {
        super(m);
    }
}

class RuntimeException extends Exception {
    public RuntimeException() {
    }

    public RuntimeException(String m) {
        super(m);
    }

    public RuntimeException(String m, Throwable c) {
        super(m, c);
    }

    protected RuntimeException(String m, Throwable c, boolean suppression, boolean writable) {
        super(m, c, suppression, writable);
    }
}

class IllegalArgumentException extends RuntimeException {
    public IllegalArgumentException() {
    }

    public IllegalArgumentException(String m) {
        super(m);
    }
}

class IllegalStateException extends RuntimeException {
    public IllegalStateException() {
    }

    public IllegalStateException(String m) {
        super(m);
    }
}

class NumberFormatException extends IllegalArgumentException {
    public NumberFormatException(String m) {
        super(m);
    }
}

class ArithmeticException extends RuntimeException {
    public ArithmeticException(String m) {
        super(m);
    }
}

class NullPointerException extends RuntimeException {
    public NullPointerException() {
    }

    public NullPointerException(String m) {
        super(m);
    }
}

class ClassCastException extends RuntimeException {
    public ClassCastException(String m) {
        super(m);
    }
}

class IndexOutOfBoundsException extends RuntimeException {
    public IndexOutOfBoundsException() {
    }

    public IndexOutOfBoundsException(String m) {
        super(m);
    }
}

class ArrayIndexOutOfBoundsException extends IndexOutOfBoundsException {
    public ArrayIndexOutOfBoundsException(String m) {
        super(m);
    }
}

class StringIndexOutOfBoundsException extends IndexOutOfBoundsException {
    public StringIndexOutOfBoundsException(String m) {
        super(m);
    }
}

class NegativeArraySizeException extends RuntimeException {
    public NegativeArraySizeException(String m) {
        super(m);
    }
}

class UnsupportedOperationException extends RuntimeException {
    public UnsupportedOperationException() {
    }

    public UnsupportedOperationException(String m) {
        super(m);
    }
}

class InterruptedException extends Exception {
}

class CloneNotSupportedException extends Exception {
}

/**
 * Fixed-capacity natural number for exact float/decimal conversion. Limbs are base 256 so
 * that splitting a limb product needs only {@code & 255} and {@code >>> 8}, which the
 * compiler emits inline. Operations work in place: there is no garbage collector.
 */
final class Big {
    final int[] d;
    int n;

    Big(int capacity) {
        d = new int[capacity];
    }

    void set(long v) {
        n = 0;
        while (v != 0) {
            d[n] = (int) (v & 255);
            v = v >>> 8;
            n++;
        }
    }

    void copy(Big b) {
        for (int i = 0; i < b.n; i++) {
            d[i] = b.d[i];
        }
        n = b.n;
    }

    boolean isZero() {
        return n == 0;
    }

    /** {@code this *= k} for {@code 0 < k < 2^22}. */
    void mul(int k) {
        int carry = 0;
        for (int i = 0; i < n; i++) {
            int t = d[i] * k + carry;
            d[i] = t & 255;
            carry = t >>> 8;
        }
        while (carry != 0) {
            d[n] = carry & 255;
            carry = carry >>> 8;
            n++;
        }
    }

    void mulPow10(int k) {
        while (k >= 6) {
            mul(1000000);
            k -= 6;
        }
        int p = 1;
        while (k > 0) {
            p = p * 10;
            k--;
        }
        if (p > 1) {
            mul(p);
        }
    }

    /** {@code this *= 2^bits}. */
    void shiftLeft(int bits) {
        if (n == 0) {
            return;
        }
        int limbs = bits >>> 3;
        if (limbs > 0) {
            for (int i = n - 1; i >= 0; i--) {
                d[i + limbs] = d[i];
            }
            for (int i = 0; i < limbs; i++) {
                d[i] = 0;
            }
            n += limbs;
        }
        int rest = bits & 7;
        if (rest > 0) {
            mul(1 << rest);
        }
    }

    void add(Big b) {
        int carry = 0;
        int i = 0;
        while (i < b.n || carry != 0) {
            int t = (i < n ? d[i] : 0) + (i < b.n ? b.d[i] : 0) + carry;
            d[i] = t & 255;
            carry = t >>> 8;
            i++;
        }
        if (i > n) {
            n = i;
        }
    }

    /** {@code this -= b}; requires {@code this >= b}. */
    void sub(Big b) {
        int borrow = 0;
        for (int i = 0; i < n; i++) {
            int t = d[i] - (i < b.n ? b.d[i] : 0) - borrow;
            borrow = 0;
            if (t < 0) {
                t += 256;
                borrow = 1;
            }
            d[i] = t;
        }
        while (n > 0 && d[n - 1] == 0) {
            n--;
        }
    }

    int compare(Big b) {
        if (n != b.n) {
            return n < b.n ? -1 : 1;
        }
        for (int i = n - 1; i >= 0; i--) {
            if (d[i] != b.d[i]) {
                return d[i] < b.d[i] ? -1 : 1;
            }
        }
        return 0;
    }

    /** Compares {@code this + b} with {@code c}. */
    int compareSum(Big b, Big c) {
        // limbwise from the top with a running difference; differences beyond +-1 decide
        int top = n > b.n ? n : b.n;
        if (c.n > top + 1) {
            return -1;
        }
        if (c.n > top) {
            top = c.n;
        } else {
            top = top + 1;
        }
        int diff = 0;
        for (int i = top - 1; i >= 0; i--) {
            diff = diff * 256 + (i < n ? d[i] : 0) + (i < b.n ? b.d[i] : 0) - (i < c.n ? c.d[i] : 0);
            if (diff > 1) {
                return 1;
            }
            if (diff < -1) {
                return -1;
            }
        }
        return diff > 0 ? 1 : diff < 0 ? -1 : 0;
    }

    int bitLength() {
        if (n == 0) {
            return 0;
        }
        int top = d[n - 1];
        int bits = 0;
        while (top != 0) {
            top = top >>> 1;
            bits++;
        }
        return (n - 1) * 8 + bits;
    }
}

/** The type of {@code null}-only results, as in {@code Visitor<Void>}. */
final class Void {
    private Void() {
    }
}
