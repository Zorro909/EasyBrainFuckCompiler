package de.zorro909.brainfuck.transpiler;

import java.util.LinkedHashMap;
import java.util.Map;

import de.zorro909.brainfuck.core.BfMemoryException;

/**
 * Compile-time memory layout for transpiled programs.
 *
 * <pre>
 * cells 0 .. scratchSize-1            BfOps scratch region (zero between operations)
 * cells scratchSize .. scratchSize+V  named variables, one cell each, declaration order
 * cells above the variables           expression temporaries (LIFO stack)
 * </pre>
 *
 * <p>Expression temporaries are always fully popped between statements, so a variable
 * declared mid-program never collides with an active temporary.
 */
class MemoryLayout {

    enum VarType {
        INT, CHAR
    }

    private record Slot(int cell, VarType type) {
    }

    private final int variablesStart;
    private final Map<String, Slot> variables = new LinkedHashMap<>();
    private int activeTemps = 0;

    MemoryLayout(int variablesStart) {
        this.variablesStart = variablesStart;
    }

    int declareVariable(String name, VarType type) {
        if (variables.containsKey(name)) {
            throw new UnsupportedJavaConstructException(
                            "Variable '" + name + "' is already declared (shadowing/"
                                            + "redeclaration is not supported)");
        }
        if (activeTemps != 0) {
            throw new IllegalStateException(
                            "Variables must not be declared while expression temporaries are active");
        }
        var slot = new Slot(variablesStart + variables.size(), type);
        variables.put(name, slot);
        return slot.cell();
    }

    int variableCell(String name) {
        return slot(name).cell();
    }

    VarType variableType(String name) {
        return slot(name).type();
    }

    boolean isDeclared(String name) {
        return variables.containsKey(name);
    }

    private Slot slot(String name) {
        var slot = variables.get(name);
        if (slot == null) {
            throw new UnsupportedJavaConstructException("Unknown variable '" + name + "'");
        }
        return slot;
    }

    /** Reserves the next temporary cell above the declared variables. */
    int pushTemp() {
        int cell = variablesStart + variables.size() + activeTemps;
        if (cell >= 65536) {
            throw new BfMemoryException("Expression temporary stack overflows the tape");
        }
        activeTemps++;
        return cell;
    }

    /** Releases the topmost temporary; must be freed in LIFO order. */
    void popTemp(int cell) {
        if (activeTemps == 0 || cell != variablesStart + variables.size() + activeTemps - 1) {
            throw new IllegalStateException("Temporaries must be popped LIFO: " + cell);
        }
        activeTemps--;
    }
}
