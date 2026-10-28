package de.zorro909.brainfuck.compiler.ir;

import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import java.util.ArrayList;
import java.util.List;

/** An IR function: a control flow graph over word-sized virtual registers. */
public final class Function {

    public final String name;
    public final int params;
    public final boolean returnsValue;
    /** Non-null for backend-generated functions, which have no blocks. */
    public final Builtin builtin;
    public final List<Block> blocks = new ArrayList<>();
    public Block entry;
    private int regCount;

    public Function(String name, int params, boolean returnsValue) {
        this(name, params, returnsValue, null);
    }

    private Function(String name, int params, boolean returnsValue, Builtin builtin) {
        this.name = name;
        this.params = params;
        this.returnsValue = returnsValue;
        this.builtin = builtin;
        this.regCount = params;
    }

    public static Function builtin(Builtin builtin) {
        return new Function("$" + builtin.name().toLowerCase(), builtin.arity,
                        builtin.returnsValue(), builtin);
    }

    public Reg param(int i) {
        return new Reg(i);
    }

    public Reg newReg() {
        return new Reg(regCount++);
    }

    public int regCount() {
        return regCount;
    }

    public Block newBlock(String label) {
        Block b = new Block(label + "#" + blocks.size());
        blocks.add(b);
        return b;
    }

    @Override
    public String toString() {
        return name;
    }
}
