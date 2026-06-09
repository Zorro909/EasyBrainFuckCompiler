package de.zorro909.brainfuck.core;

/**
 * A contiguous block of Brainfuck cells managed by a {@link VariableManager}.
 *
 * <p>Layout: {@link #cell()} is a guard cell that must stay zero (string operations scan
 * left until they hit it), the data lives in cells {@code cell() + 1} to
 * {@code cell() + maxLength()}, and {@code cell() + maxLength() + 1} is a terminator
 * cell that holds the trailing newline of a stored string or stays zero.
 */
public class Variable {

    private final int cell;
    private final int maxLength;

    Variable(int cell, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive: " + maxLength);
        }
        this.cell = cell;
        this.maxLength = maxLength;
    }

    /** The guard cell; data starts one cell to the right. */
    public int cell() {
        return cell;
    }

    /** Maximum number of data cells (excluding guard and terminator). */
    public int maxLength() {
        return maxLength;
    }

    /** Absolute cell of the data cell at {@code index} (0-based). */
    public int dataCell(int index) {
        if (index < 0 || index > maxLength) {
            throw new IndexOutOfBoundsException("Index " + index + " out of 0.." + maxLength);
        }
        return cell + 1 + index;
    }
}
