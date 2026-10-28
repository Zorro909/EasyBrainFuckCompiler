package de.zorro909.brainfuck.bf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brainfuck source compiled into a compact instruction array for {@link BfInterpreter}.
 *
 * <p>The compiler folds the usual idioms: runs of {@code +-<>} become offset-addressed adds
 * with a single pointer move, {@code [-]} becomes a clear, balanced copy/multiply loops such as
 * {@code [->+>++<<]} become multiply-adds, and {@code [<]}/{@code [>>]} become scans. Generated
 * code relies heavily on these, so they turn O(value) loops into O(1) operations.
 */
public final class BfProgram {

    static final int ADD = 0;      // tape[p+a] += b
    static final int MOVE = 1;     // p += a
    static final int CLEAR = 2;    // tape[p+a] = 0
    static final int MULADD = 3;   // tape[p+a] += tape[p] * b   (always followed by CLEAR 0)
    static final int OPEN = 4;     // if tape[p] == 0 jump to a
    static final int CLOSE = 5;    // if tape[p] != 0 jump to a
    static final int SCAN = 6;     // while tape[p] != 0: p += a
    static final int OUT = 7;      // write tape[p+a]
    static final int IN = 8;       // read into tape[p+a]

    final int[] op;
    final int[] a;
    final int[] b;
    /** Source offset of each instruction (for profiling). */
    final int[] src;
    final int length;

    private BfProgram(int[] op, int[] a, int[] b, int[] src, int length) {
        this.op = op;
        this.a = a;
        this.b = b;
        this.src = src;
        this.length = length;
    }

    /** Source character offset at which instruction {@code i} was generated. */
    public int sourceOffset(int i) {
        return src[i];
    }

    /** Number of compiled instructions (after idiom folding). */
    public int size() {
        return length;
    }

    /** Compiles Brainfuck source; every character except {@code +-<>[].,} is ignored. */
    public static BfProgram compile(CharSequence source) {
        return new Builder().build(source);
    }

    private static final class Builder {
        private int[] op = new int[1024];
        private int[] a = new int[1024];
        private int[] b = new int[1024];
        private int[] src = new int[1024];
        private int n;
        private int at;

        // pending straight-line segment: offset -> delta (insertion ordered), pointer offset
        private final Map<Integer, Integer> pending = new LinkedHashMap<>();
        private int pendingMove;

        BfProgram build(CharSequence source) {
            CharSequence src = source;
            Deque<Integer> open = new ArrayDeque<>();
            for (int i = 0; i < src.length(); i++) {
                at = i;
                switch (src.charAt(i)) {
                    case '+' -> pending.merge(pendingMove, 1, Integer::sum);
                    case '-' -> pending.merge(pendingMove, -1, Integer::sum);
                    case '>' -> pendingMove++;
                    case '<' -> pendingMove--;
                    case '.' -> {
                        flushAdds();
                        emit(OUT, pendingMove, 0);
                    }
                    case ',' -> {
                        flushAdds();
                        emit(IN, pendingMove, 0);
                    }
                    case '[' -> {
                        flush();
                        open.push(n);
                        emit(OPEN, -1, 0);
                    }
                    case ']' -> {
                        if (open.isEmpty()) {
                            throw new BfSyntaxException("Unbalanced ']' at character " + i);
                        }
                        flush();
                        int start = open.pop();
                        if (!foldLoop(start)) {
                            emit(CLOSE, start, 0);
                            a[start] = n - 1;
                        }
                    }
                    default -> {
                        // comment character
                    }
                }
            }
            if (!open.isEmpty()) {
                throw new BfSyntaxException("Unbalanced '[' (" + open.size() + " unclosed)");
            }
            flush();
            return new BfProgram(op, a, b, this.src, n);
        }

        /** Replaces a just-finished simple loop (body = instructions after start) with an idiom. */
        private boolean foldLoop(int start) {
            int bodyStart = start + 1;
            int bodyLen = n - bodyStart;
            if (bodyLen == 1 && op[bodyStart] == MOVE) {
                int stride = a[bodyStart];
                n = start;
                emit(SCAN, stride, 0);
                return true;
            }
            // balanced loop: only ADDs, no pointer movement, cell 0 decremented by exactly 1
            int selfDelta = 0;
            for (int i = bodyStart; i < n; i++) {
                if (op[i] != ADD) {
                    return false;
                }
                if (a[i] == 0) {
                    selfDelta = (byte) b[i];
                }
            }
            if (selfDelta == -1 || (selfDelta == 1 && bodyLen == 1)) {
                List<int[]> targets = new ArrayList<>();
                for (int i = bodyStart; i < n; i++) {
                    if (a[i] != 0) {
                        targets.add(new int[] {a[i], b[i]});
                    }
                }
                n = start;
                for (int[] t : targets) {
                    emit(MULADD, t[0], t[1]);
                }
                emit(CLEAR, 0, 0);
                return true;
            }
            return false;
        }

        private void flushAdds() {
            for (var e : pending.entrySet()) {
                int delta = e.getValue() & 0xFF;
                if (delta != 0) {
                    emit(ADD, e.getKey(), delta);
                }
            }
            pending.clear();
        }

        private void flush() {
            flushAdds();
            if (pendingMove != 0) {
                emit(MOVE, pendingMove, 0);
                pendingMove = 0;
            }
        }

        private void emit(int code, int x, int y) {
            if (n == op.length) {
                op = java.util.Arrays.copyOf(op, n * 2);
                a = java.util.Arrays.copyOf(a, n * 2);
                b = java.util.Arrays.copyOf(b, n * 2);
                src = java.util.Arrays.copyOf(src, n * 2);
            }
            src[n] = at;
            op[n] = code;
            a[n] = x;
            b[n] = y;
            n++;
        }
    }
}
