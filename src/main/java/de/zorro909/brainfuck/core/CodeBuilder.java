package de.zorro909.brainfuck.core;

/**
 * Low-level Brainfuck emitter that tracks the data pointer position at compile time.
 *
 * <p>All higher-level code generation goes through this class so that cell addresses can
 * be absolute: {@link #moveTo(int)} emits the minimal {@code <}/{@code >} sequence and
 * {@link #loopAt(int, Runnable)} guarantees the pointer-position discipline that
 * {@code [}/{@code ]} require, no matter how the loop body moves around.
 */
public class CodeBuilder {

    private final StringBuilder code = new StringBuilder();
    private int pointer = 0;

    /** Returns the generated Brainfuck code. */
    public String code() {
        return code.toString();
    }

    /** Returns the compile-time data pointer position. */
    public int pointer() {
        return pointer;
    }

    /** Moves the data pointer to an absolute cell. */
    public void moveTo(int cell) {
        if (cell < 0) {
            throw new IllegalArgumentException("Cell must not be negative: " + cell);
        }
        move(cell - pointer);
    }

    /** Moves the data pointer relative to its current position. */
    public void move(int delta) {
        if (delta > 0) {
            code.append(">".repeat(delta));
        } else if (delta < 0) {
            code.append("<".repeat(-delta));
        }
        pointer += delta;
    }

    /** Increments the current cell {@code n} times. */
    public void inc(int n) {
        repeat('+', n);
    }

    /** Decrements the current cell {@code n} times. */
    public void dec(int n) {
        repeat('-', n);
    }

    /** Emits {@code '.'} (write the current cell to output). */
    public void output() {
        code.append('.');
    }

    /** Emits {@code ','} (read one byte into the current cell). */
    public void input() {
        code.append(',');
    }

    /** Emits {@code [-]} (set the current cell to zero). */
    public void clear() {
        code.append("[-]");
    }

    /**
     * Emits a loop that tests {@code cell}: the pointer is moved to {@code cell} before
     * {@code [}, the body runs, and the pointer is moved back to {@code cell} before
     * {@code ]}. The body may move the pointer freely.
     */
    public void loopAt(int cell, Runnable body) {
        moveTo(cell);
        code.append('[');
        body.run();
        moveTo(cell);
        code.append(']');
    }

    /**
     * Emits a loop at the current cell. The body must end with the pointer back on the
     * cell it started on; this is verified at compile time.
     */
    public void loop(Runnable body) {
        int start = pointer;
        code.append('[');
        body.run();
        if (pointer != start) {
            throw new IllegalStateException(
                            "Loop body moved the pointer from " + start + " to " + pointer);
        }
        code.append(']');
    }

    /**
     * Appends a hand-written snippet whose net pointer movement is known. The caller is
     * responsible for {@code netPointerDelta} being correct for every possible cell
     * state the snippet runs on.
     */
    public void raw(String snippet, int netPointerDelta) {
        code.append(snippet);
        pointer += netPointerDelta;
    }

    /** Emits {@code c} repeated {@code n} times. */
    public void repeat(char c, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Repeat count must not be negative: " + n);
        }
        code.append(String.valueOf(c).repeat(n));
    }
}
