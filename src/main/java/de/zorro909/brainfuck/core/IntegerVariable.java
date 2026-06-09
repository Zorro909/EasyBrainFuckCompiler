package de.zorro909.brainfuck.core;

/**
 * A single-byte integer value (0–255). The value lives in {@code cell() + 1}; the
 * remaining data cells are scratch space required by the decimal print routine and must
 * be zero when {@link #print()} runs.
 */
public class IntegerVariable extends Variable {

    /** Value cell plus scratch cells for the decimal print routine. */
    static final int CELLS = 10;

    /**
     * Prints the current cell as a decimal number. Classic Brainfuck idiom (divide by 10
     * via repeated subtraction, then print the digits); requires the six cells to the
     * right of the value cell to be zero and leaves them zero again. Verified by
     * interpretation for all values 0–255: ends back on the value cell (net pointer
     * movement 0) with the value preserved.
     */
    private static final String DECIMAL_PRINT = "[>>+>+<<<-]>>>[<<<+>>>-]<<+>[<->[>++++++++++<"
                    + "[->-[>+>>]>[+[-<+>]>+>>]<<<<<]>[-]++++++++[<++++++>-]>[<<+>>-]>[<<+>>-]"
                    + "<<]>]<[->>++++++++[<++++++>-]]<[.[-]<]<";
    private static final int DECIMAL_PRINT_NET_DELTA = 0;

    private final BrainFuckScript script;

    IntegerVariable(int cell, BrainFuckScript script) {
        super(cell, CELLS);
        this.script = script;
    }

    /** Absolute cell holding the integer value. */
    public int valueCell() {
        return dataCell(0);
    }

    /**
     * Converts a variable holding ASCII binary digits ({@code '0'}/{@code '1'},
     * most-significant bit first) into this integer's value cell. The source digits are
     * consumed (left zeroed).
     */
    public void importFromBit(Variable from) {
        if (from.maxLength() % 4 != 0) {
            throw new IllegalArgumentException(
                            "Variable importation only works with whole bit groups of 4, got length "
                                            + from.maxLength());
        }
        CodeBuilder builder = script.builder();
        for (int i = 0; i < from.maxLength(); i++) {
            builder.moveTo(from.dataCell(i));
            builder.dec('0');
        }
        for (int bit = 0; bit < from.maxLength(); bit++) {
            // rightmost digit carries weight 2^0
            int sourceCell = from.dataCell(from.maxLength() - 1 - bit);
            int weight = 1 << bit;
            builder.loopAt(sourceCell, () -> {
                builder.dec(1);
                builder.moveTo(valueCell());
                builder.inc(weight);
            });
        }
        builder.moveTo(0);
    }

    /** Emits code that prints the integer value as a decimal number. */
    public void print() {
        CodeBuilder builder = script.builder();
        builder.moveTo(valueCell());
        builder.raw(DECIMAL_PRINT, DECIMAL_PRINT_NET_DELTA);
        builder.moveTo(0);
    }
}
