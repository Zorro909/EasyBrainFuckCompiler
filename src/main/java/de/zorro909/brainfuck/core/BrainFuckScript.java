package de.zorro909.brainfuck.core;

import java.nio.charset.StandardCharsets;

/**
 * High-level Brainfuck code generator ("the writer").
 *
 * <p>Builds a Brainfuck program through variable and string operations; the generated
 * code is retrieved with {@link #getScript()}. All emission happens through a
 * pointer-tracking {@link CodeBuilder}, and every public operation starts and ends with
 * the data pointer on cell 0.
 */
public class BrainFuckScript {

    /**
     * Reads one input line into the cells right of the current one and ends on the
     * starting cell. Each read character has 10 subtracted to detect the newline, plus
     * one more from the loop counter; the closing {@code <[+++++++++++<]} walk adds the
     * 11 back. Known limitation: a character with byte value 11 becomes 0 and stops the
     * restore walk early.
     */
    private static final String READ_LINE = "+[->,----------]++++++++++<[+++++++++++<]";

    private final CodeBuilder builder = new CodeBuilder();
    private VariableManager variableManager;

    /** The low-level emitter, for code generation that needs direct cell access. */
    public CodeBuilder builder() {
        return builder;
    }

    /** Returns the Brainfuck code generated so far. */
    public String getScript() {
        return builder.code();
    }

    public VariableManager setupVariableManager(int stringRegionStart, int stringRegionEnd,
                    int inputRegionStart, int inputRegionEnd) {
        if (variableManager == null) {
            variableManager = new VariableManager(stringRegionStart, stringRegionEnd,
                            inputRegionStart, inputRegionEnd, this);
        }
        return variableManager;
    }

    public VariableManager variableManager() {
        if (variableManager == null) {
            throw new IllegalStateException("setupVariableManager has not been called");
        }
        return variableManager;
    }

    /**
     * Stores an ASCII string in the variable's data cells (which must be zero). A
     * trailing newline is appended if missing; it occupies the terminator cell when the
     * string fills the variable completely.
     */
    public Variable saveString(Variable variable, String s) {
        if (!s.endsWith("\n")) {
            s += "\n";
        }
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > variable.maxLength() + 1) {
            throw new IllegalArgumentException("String of length " + bytes.length
                            + " (incl. newline) does not fit a variable of maxLength "
                            + variable.maxLength());
        }
        for (int i = 0; i < bytes.length; i++) {
            builder.moveTo(variable.dataCell(i));
            add(bytes[i] & 0xFF);
        }
        builder.moveTo(0);
        return variable;
    }

    /** Allocates an input variable and reads one input line into it. */
    public InputVariable readString(int maxLength) {
        return readString(variableManager().createInputVariable(maxLength));
    }

    /**
     * Reads one input line (terminated by {@code '\n'}, which is stored too) into the
     * variable's data cells.
     */
    public InputVariable readString(InputVariable input) {
        builder.moveTo(input.cell());
        builder.raw(READ_LINE, 0);
        builder.moveTo(0);
        return input;
    }

    /** Prints the variable's content up to the first zero cell. */
    public void printString(Variable variable) {
        builder.moveTo(variable.dataCell(0));
        // print until the terminator zero, then scan back to the guard zero
        builder.raw("[.>]<[<]", -1);
        builder.moveTo(0);
    }

    /**
     * Prints a string literal using the cell right of the current one as working cell,
     * adjusting it by the difference between consecutive characters. The working cell
     * and the one right of it must be zero; both end zero.
     */
    public void printString(String s) {
        int last = 0;
        for (byte b : s.getBytes(StandardCharsets.US_ASCII)) {
            printCharacter(b, last);
            last = b & 0xFF;
        }
        builder.move(1);
        sub(last);
        builder.move(-1);
    }

    /** Prints one character via the cell right of the current one (must be zero). */
    public void printCharacter(byte c) {
        builder.move(1);
        add(c & 0xFF);
        builder.output();
        sub(c & 0xFF);
        builder.move(-1);
    }

    /**
     * Prints one character via the cell right of the current one, assuming that cell
     * currently holds {@code knownCellValue}; the character value is left in the cell.
     */
    public void printCharacter(byte c, int knownCellValue) {
        builder.move(1);
        int diff = (c & 0xFF) - knownCellValue;
        if (diff > 0) {
            add(diff);
        } else if (diff < 0) {
            sub(-diff);
        }
        builder.output();
        builder.move(-1);
    }

    /**
     * Adds a constant to the current cell. Large constants are factorized into a
     * multiplication loop that uses the cell right of the current one as scratch (must
     * be zero; ends zero).
     */
    public void add(int n) {
        emitConstant(n, '+');
    }

    /** Subtracts a constant from the current cell; see {@link #add(int)}. */
    public void sub(int n) {
        emitConstant(n, '-');
    }

    private void emitConstant(int n, char op) {
        if (n < 0) {
            throw new IllegalArgumentException("Constant must not be negative: " + n);
        }
        if (n == 0) {
            return;
        }
        int bestFactor = 1;
        int bestSum = -1;
        for (int factor = 2; factor <= 25 && factor <= n; factor++) {
            if (n % factor == 0) {
                int sum = factor + n / factor;
                if (bestSum == -1 || sum < bestSum) {
                    bestSum = sum;
                    bestFactor = factor;
                }
            }
        }
        // counter setup, loop brackets and pointer moves cost 7 extra characters
        if (bestSum == -1 || bestSum + 7 >= n) {
            builder.repeat(op, n);
            return;
        }
        int multiplier = n / bestFactor;
        builder.move(1);
        builder.inc(bestFactor);
        builder.loop(() -> {
            builder.move(-1);
            builder.repeat(op, multiplier);
            builder.move(1);
            builder.dec(1);
        });
        builder.move(-1);
    }
}
