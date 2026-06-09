package de.zorro909.brainfuck.core;

import java.nio.charset.StandardCharsets;

/**
 * Library of Brainfuck operations on absolute cells — the "instruction set" that the
 * Java-to-Brainfuck transpiler targets.
 *
 * <p>All operations work on 8-bit wrapping cells (values 0–255). Temporary cells are
 * taken from a dedicated scratch region handed to the constructor; the invariant is
 * that every scratch cell is zero between operations, and all operations restore it.
 * Unless documented otherwise, operations preserve their source operands and output
 * cells must not alias input cells.
 *
 * <p>Comparison and logic operations normalize their result to 0 (false) / 1 (true).
 */
public class BfOps {

    /**
     * Prints the current cell as a decimal number (same proven snippet as
     * {@link IntegerVariable}): requires the six cells right of the current one to be
     * zero, leaves them zero, preserves the value, net pointer movement 0.
     */
    private static final String DECIMAL_PRINT = "[>>+>+<<<-]>>>[<<<+>>>-]<<+>[<->[>++++++++++<"
                    + "[->-[>+>>]>[+[-<+>]>+>>]<<<<<]>[-]++++++++[<++++++>-]>[<<+>>-]>[<<+>>-]"
                    + "<<]>]<[->>++++++++[<++++++>-]]<[.[-]<]<";
    private static final int DECIMAL_PRINT_SCRATCH = 6;

    private final CodeBuilder cb;
    private final int scratchStart;
    private final int scratchSize;
    private int scratchDepth = 0;

    public BfOps(CodeBuilder builder, int scratchStart, int scratchSize) {
        if (scratchStart < 0 || scratchSize < DECIMAL_PRINT_SCRATCH + 2) {
            throw new IllegalArgumentException("Scratch region too small: " + scratchSize);
        }
        this.cb = builder;
        this.scratchStart = scratchStart;
        this.scratchSize = scratchSize;
    }

    public CodeBuilder builder() {
        return cb;
    }

    private int alloc() {
        if (scratchDepth >= scratchSize) {
            throw new BfMemoryException("Scratch region exhausted (" + scratchSize
                            + " cells); expression too deeply nested");
        }
        return scratchStart + scratchDepth++;
    }

    private void free(int cell) {
        if (cell != scratchStart + scratchDepth - 1) {
            throw new IllegalStateException("Scratch cells must be freed LIFO");
        }
        scratchDepth--;
    }

    /** {@code cell = 0} */
    public void clear(int cell) {
        cb.moveTo(cell);
        cb.clear();
    }

    /** {@code cell = value} (mod 256); the cell may hold anything beforehand. */
    public void set(int cell, int value) {
        clear(cell);
        cb.inc(Math.floorMod(value, 256));
    }

    /** {@code cell += n} (mod 256) */
    public void addConst(int cell, int n) {
        cb.moveTo(cell);
        cb.inc(Math.floorMod(n, 256));
    }

    /** {@code cell -= n} (mod 256) */
    public void subConst(int cell, int n) {
        cb.moveTo(cell);
        cb.dec(Math.floorMod(n, 256));
    }

    /** {@code dst += src; src = 0} */
    public void moveAdd(int src, int dst) {
        cb.loopAt(src, () -> {
            cb.dec(1);
            cb.moveTo(dst);
            cb.inc(1);
        });
    }

    /** {@code dst -= src; src = 0} */
    public void moveSub(int src, int dst) {
        cb.loopAt(src, () -> {
            cb.dec(1);
            cb.moveTo(dst);
            cb.dec(1);
        });
    }

    /** {@code dst = src}, preserving {@code src}. */
    public void copy(int src, int dst) {
        clear(dst);
        int tmp = alloc();
        cb.loopAt(src, () -> {
            cb.dec(1);
            cb.moveTo(dst);
            cb.inc(1);
            cb.moveTo(tmp);
            cb.inc(1);
        });
        moveAdd(tmp, src);
        free(tmp);
    }

    /** {@code dst += src}, preserving {@code src}. */
    public void addVar(int dst, int src) {
        int tmp = alloc();
        cb.loopAt(src, () -> {
            cb.dec(1);
            cb.moveTo(dst);
            cb.inc(1);
            cb.moveTo(tmp);
            cb.inc(1);
        });
        moveAdd(tmp, src);
        free(tmp);
    }

    /** {@code dst -= src}, preserving {@code src}. */
    public void subVar(int dst, int src) {
        int tmp = alloc();
        cb.loopAt(src, () -> {
            cb.dec(1);
            cb.moveTo(dst);
            cb.dec(1);
            cb.moveTo(tmp);
            cb.inc(1);
        });
        moveAdd(tmp, src);
        free(tmp);
    }

    /** {@code result = a * b} (mod 256), preserving {@code a} and {@code b}. */
    public void mul(int a, int b, int result) {
        clear(result);
        int counter = alloc();
        copy(a, counter);
        cb.loopAt(counter, () -> {
            cb.dec(1);
            addVar(result, b);
        });
        free(counter);
    }

    /**
     * {@code quotient = n / d; remainder = n % d}, preserving {@code n} and {@code d}.
     * Implemented as repeated subtraction; {@code d == 0} loops until the interpreter's
     * step limit aborts the program.
     */
    public void divmod(int n, int d, int quotient, int remainder) {
        clear(quotient);
        copy(n, remainder);
        int flag = alloc();
        whileLoop(flag, () -> ge(remainder, d, flag), () -> {
            subVar(remainder, d);
            addConst(quotient, 1);
        });
        free(flag);
    }

    /** {@code cell = (cell != 0) ? 1 : 0}, in place. */
    public void toBool(int cell) {
        int tmp = alloc();
        cb.loopAt(cell, () -> {
            cb.moveTo(tmp);
            cb.inc(1);
            cb.moveTo(cell);
            cb.clear();
        });
        moveAdd(tmp, cell);
        free(tmp);
    }

    /** {@code cell = (cell == 0) ? 1 : 0}, in place (works for any cell value). */
    public void not(int cell) {
        int tmp = alloc();
        set(tmp, 1);
        cb.loopAt(cell, () -> {
            cb.moveTo(tmp);
            cb.dec(1);
            cb.moveTo(cell);
            cb.clear();
        });
        moveAdd(tmp, cell);
        free(tmp);
    }

    /** {@code out = (a != 0) && (b != 0)}, preserving both. Not short-circuit. */
    public void and(int a, int b, int out) {
        clear(out);
        int ta = alloc();
        int tb = alloc();
        copy(a, ta);
        toBool(ta);
        copy(b, tb);
        toBool(tb);
        cb.loopAt(ta, () -> {
            cb.dec(1);
            moveAdd(tb, out);
        });
        clear(tb);
        free(tb);
        free(ta);
    }

    /** {@code out = (a != 0) || (b != 0)}, preserving both. Not short-circuit. */
    public void or(int a, int b, int out) {
        clear(out);
        int tmp = alloc();
        copy(a, tmp);
        toBool(tmp);
        moveAdd(tmp, out);
        copy(b, tmp);
        toBool(tmp);
        moveAdd(tmp, out);
        toBool(out);
        free(tmp);
    }

    /** {@code out = (a == b)}, preserving both. */
    public void eq(int a, int b, int out) {
        int tmp = alloc();
        copy(a, tmp);
        subVar(tmp, b);
        not(tmp);
        clear(out);
        moveAdd(tmp, out);
        free(tmp);
    }

    /** {@code out = (a != b)}, preserving both. */
    public void neq(int a, int b, int out) {
        int tmp = alloc();
        copy(a, tmp);
        subVar(tmp, b);
        toBool(tmp);
        clear(out);
        moveAdd(tmp, out);
        free(tmp);
    }

    /** {@code out = (a < b)}, preserving both. */
    public void lt(int a, int b, int out) {
        int x = alloc();
        int y = alloc();
        int flag = alloc();
        copy(a, x);
        copy(b, y);
        clear(out);
        // while y != 0: if x != 0 then decrement both, else a < b — record and exit
        whileLoop(flag, () -> {
            copy(y, flag);
            toBool(flag);
        }, () -> {
            int xNonZero = alloc();
            copy(x, xNonZero);
            toBool(xNonZero);
            ifElse(xNonZero, () -> {
                subConst(x, 1);
                subConst(y, 1);
            }, () -> {
                set(out, 1);
                clear(y);
            });
            clear(xNonZero);
            free(xNonZero);
        });
        clear(x);
        free(flag);
        free(y);
        free(x);
    }

    /** {@code out = (a > b)}, preserving both. */
    public void gt(int a, int b, int out) {
        lt(b, a, out);
    }

    /** {@code out = (a <= b)}, preserving both. */
    public void le(int a, int b, int out) {
        lt(b, a, out);
        not(out);
    }

    /** {@code out = (a >= b)}, preserving both. */
    public void ge(int a, int b, int out) {
        lt(a, b, out);
        not(out);
    }

    /**
     * Runs {@code thenBlock} if {@code cond} is non-zero, otherwise {@code elseBlock}.
     * {@code cond} is preserved; the blocks may emit arbitrary code (including nested
     * control flow) and may leave the pointer anywhere.
     */
    public void ifElse(int cond, Runnable thenBlock, Runnable elseBlock) {
        int thenFlag = alloc();
        int elseFlag = alloc();
        copy(cond, thenFlag);
        toBool(thenFlag);
        set(elseFlag, 1);
        cb.loopAt(thenFlag, () -> {
            thenBlock.run();
            subConst(elseFlag, 1);
            clear(thenFlag);
        });
        cb.loopAt(elseFlag, () -> {
            elseBlock.run();
            clear(elseFlag);
        });
        free(elseFlag);
        free(thenFlag);
    }

    /** Runs {@code thenBlock} if {@code cond} is non-zero; {@code cond} is preserved. */
    public void ifThen(int cond, Runnable thenBlock) {
        ifElse(cond, thenBlock, () -> {
        });
    }

    /**
     * Emits a while loop. {@code condition} must emit code that stores the loop
     * condition (0/1) into {@code flagCell}; it is evaluated before the loop and again
     * after every iteration of {@code body}.
     */
    public void whileLoop(int flagCell, Runnable condition, Runnable body) {
        condition.run();
        cb.loopAt(flagCell, () -> {
            body.run();
            condition.run();
        });
    }

    /** Prints the cell as a single character/byte. */
    public void printChar(int cell) {
        cb.moveTo(cell);
        cb.output();
    }

    /** Reads one input byte into the cell (0 at end of input). */
    public void readChar(int cell) {
        cb.moveTo(cell);
        cb.input();
    }

    /** Prints the cell's value as a decimal number (0–255), preserving it. */
    public void printInt(int cell) {
        int tmp = alloc();
        if (scratchDepth + DECIMAL_PRINT_SCRATCH > scratchSize) {
            throw new BfMemoryException("Not enough scratch cells for printInt");
        }
        copy(cell, tmp);
        cb.moveTo(tmp);
        cb.raw(DECIMAL_PRINT, 0);
        clear(tmp);
        free(tmp);
    }

    /**
     * Reads a decimal number into {@code dst}: consumes ASCII digits up to and including
     * the terminating newline (or end of input). The value wraps mod 256.
     */
    public void readInt(int dst) {
        clear(dst);
        int c = alloc();
        int flag = alloc();
        readChar(c);
        whileLoop(flag, () -> {
            // flag = (c != '\n') && (c != 0 i.e. EOF)
            int notNewline = alloc();
            copy(c, notNewline);
            subConst(notNewline, '\n');
            toBool(notNewline);
            and(notNewline, c, flag);
            clear(notNewline);
            free(notNewline);
        }, () -> {
            subConst(c, '0');
            // dst = dst * 10 + digit
            int tmp = alloc();
            copy(dst, tmp);
            for (int i = 0; i < 9; i++) {
                addVar(dst, tmp);
            }
            clear(tmp);
            free(tmp);
            moveAdd(c, dst);
            readChar(c);
        });
        clear(c);
        free(flag);
        free(c);
    }

    /**
     * Prints a string literal via a scratch cell, adjusting it by the difference between
     * consecutive characters.
     */
    public void printString(String literal) {
        int tmp = alloc();
        cb.moveTo(tmp);
        int last = 0;
        for (byte b : literal.getBytes(StandardCharsets.US_ASCII)) {
            int value = b & 0xFF;
            int diff = value - last;
            if (diff > 0) {
                cb.inc(diff);
            } else if (diff < 0) {
                cb.dec(-diff);
            }
            cb.output();
            last = value;
        }
        cb.dec(last);
        free(tmp);
    }
}
