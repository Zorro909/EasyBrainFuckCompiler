package de.zorro909.brainfuck.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Allocates variables in two fixed memory regions: one for string/integer variables and
 * one for input variables. Each variable occupies {@code maxLength + 2} cells (guard +
 * data + terminator, see {@link Variable}); both regions grow upward.
 */
public class VariableManager {

    private final BrainFuckScript script;
    private final int stringRegionEnd;
    private final int inputRegionEnd;
    private int nextStringCell;
    private int nextInputCell;
    private final List<Variable> stringVariables = new ArrayList<>();
    private final List<InputVariable> inputVariables = new ArrayList<>();

    VariableManager(int stringRegionStart, int stringRegionEnd, int inputRegionStart,
                    int inputRegionEnd, BrainFuckScript script) {
        if (stringRegionStart < 0 || stringRegionStart >= stringRegionEnd) {
            throw new IllegalArgumentException("Invalid string region: " + stringRegionStart
                            + ".." + stringRegionEnd);
        }
        if (inputRegionStart < 0 || inputRegionStart >= inputRegionEnd) {
            throw new IllegalArgumentException("Invalid input region: " + inputRegionStart
                            + ".." + inputRegionEnd);
        }
        this.nextStringCell = stringRegionStart;
        this.stringRegionEnd = stringRegionEnd;
        this.nextInputCell = inputRegionStart;
        this.inputRegionEnd = inputRegionEnd;
        this.script = script;
    }

    public Variable createVariable(int maxLength) {
        var variable = new Variable(allocateString(maxLength), maxLength);
        stringVariables.add(variable);
        return variable;
    }

    public IntegerVariable createIntegerVariable() {
        var variable = new IntegerVariable(allocateString(IntegerVariable.CELLS), script);
        stringVariables.add(variable);
        return variable;
    }

    public InputVariable createInputVariable(int maxLength) {
        int cell = nextInputCell;
        if (cell + maxLength + 1 > inputRegionEnd) {
            throw new BfMemoryException("Not enough memory in the input variable region for "
                            + (maxLength + 2) + " cells at " + cell + ".." + inputRegionEnd);
        }
        nextInputCell = cell + maxLength + 2;
        var variable = new InputVariable(cell, maxLength, script);
        inputVariables.add(variable);
        return variable;
    }

    private int allocateString(int maxLength) {
        int cell = nextStringCell;
        if (cell + maxLength + 1 > stringRegionEnd) {
            throw new BfMemoryException("Not enough memory in the string variable region for "
                            + (maxLength + 2) + " cells at " + cell + ".." + stringRegionEnd);
        }
        nextStringCell = cell + maxLength + 2;
        return cell;
    }

    /** Stores {@code content} in {@code variable} (cells must currently be zero). */
    public void setVariable(Variable variable, String content) {
        script.saveString(variable, content);
    }

    /**
     * Moves the content of {@code from} into {@code to}, leaving {@code from} zeroed.
     * Despite the historical name this is a destructive move, not a copy.
     */
    public void copyVariable(Variable from, Variable to) {
        if (to.maxLength() < from.maxLength()) {
            throw new IllegalArgumentException("Target variable is too small: "
                            + to.maxLength() + " < " + from.maxLength());
        }
        clearVariable(to);
        CodeBuilder builder = script.builder();
        for (int i = 0; i <= from.maxLength(); i++) {
            int source = from.dataCell(i);
            int target = to.dataCell(i);
            builder.loopAt(source, () -> {
                builder.dec(1);
                builder.moveTo(target);
                builder.inc(1);
            });
        }
        builder.moveTo(0);
    }

    /** Zeroes all data cells of {@code variable} including the terminator cell. */
    public void clearVariable(Variable variable) {
        CodeBuilder builder = script.builder();
        for (int i = 0; i <= variable.maxLength(); i++) {
            builder.moveTo(variable.dataCell(i));
            builder.clear();
        }
        builder.moveTo(0);
    }
}
