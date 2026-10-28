package de.zorro909.brainfuck.compiler.backend;

/**
 * Low-level Brainfuck writer that tracks the data pointer at compile time.
 *
 * <p>All cells are addressed by an offset relative to a moving origin (normally the base of the
 * current stack frame). {@link #rebase(int)} shifts that origin after the pointer physically
 * moved to another frame, and {@link #movingLoop} emits loops whose body moves the pointer by a
 * fixed stride per iteration, so code after such a loop keeps using the same offsets.
 */
final class Emitter {

    private final StringBuilder out;
    private int pos;

    Emitter() {
        this(new StringBuilder());
    }

    Emitter(StringBuilder out) {
        this.out = out;
    }

    StringBuilder output() {
        return out;
    }

    int position() {
        return pos;
    }

    /** Overrides the tracked position after raw code with a statically known net movement. */
    void assumePosition(int cell) {
        pos = cell;
    }

    void moveTo(int cell) {
        int d = cell - pos;
        char c = d > 0 ? '>' : '<';
        for (int i = Math.abs(d); i > 0; i--) {
            out.append(c);
        }
        pos = cell;
    }

    /** Declares that the origin moved by {@code delta} cells: the same physical cell now has
     * offset {@code old - delta}. */
    void rebase(int delta) {
        pos -= delta;
    }

    void add(int cell, int delta) {
        delta = Math.floorMod(delta, 256);
        if (delta == 0) {
            return;
        }
        moveTo(cell);
        if (delta <= 128) {
            out.repeat('+', delta);
        } else {
            out.repeat('-', 256 - delta);
        }
    }

    void clear(int cell) {
        moveTo(cell);
        out.append("[-]");
    }

    void output(int cell) {
        moveTo(cell);
        out.append('.');
    }

    void input(int cell) {
        moveTo(cell);
        out.append(',');
    }

    /** {@code while (cell != 0) body}; the body may move freely but must keep the origin. */
    void loop(int cell, Runnable body) {
        moveTo(cell);
        out.append('[');
        body.run();
        moveTo(cell);
        out.append(']');
    }

    /**
     * Loop whose body moves the origin by {@code shift} per iteration: the body is emitted in
     * the coordinates of the iteration start, and the loop test after each iteration happens at
     * {@code cell + shift}, which becomes {@code cell} again via {@link #rebase(int)}.
     */
    void movingLoop(int cell, int shift, Runnable body) {
        moveTo(cell);
        out.append('[');
        body.run();
        moveTo(cell + shift);
        rebase(shift);
        out.append(']');
    }

    /** {@code if (flag) { flag = 0; body }} for a 0/1 flag cell. */
    void ifFlag(int flag, Runnable body) {
        loop(flag, () -> {
            add(flag, -1);
            body.run();
        });
    }

    /** {@code if (cell != 0) { cell = 0; body }} - destroys the tested cell. */
    void ifNonZeroDestructive(int cell, Runnable body) {
        loop(cell, () -> {
            clear(cell);
            body.run();
        });
    }

    /** {@code dst += src * factor; src = 0} for every target. */
    void moveAdd(int src, int[] dsts, int[] factors) {
        loop(src, () -> {
            add(src, -1);
            for (int i = 0; i < dsts.length; i++) {
                add(dsts[i], factors[i]);
            }
        });
    }

    void move(int src, int dst) {
        moveAdd(src, new int[] {dst}, new int[] {1});
    }

    void moveSub(int src, int dst) {
        moveAdd(src, new int[] {dst}, new int[] {-1});
    }

    /** {@code dst += src} keeping src, using a zero temp. */
    void copyAdd(int src, int dst, int tmp) {
        moveAdd(src, new int[] {dst, tmp}, new int[] {1, 1});
        move(tmp, src);
    }

    /** {@code dst -= src} keeping src, using a zero temp. */
    void copySub(int src, int dst, int tmp) {
        moveAdd(src, new int[] {dst, tmp}, new int[] {-1, 1});
        move(tmp, src);
    }

    void raw(String bf) {
        out.append(bf);
    }
}
