package de.zorro909.brainfuck.compiler.ir;

/** An instruction input: a virtual register or an immediate word. */
public sealed interface Operand permits Operand.Reg, Operand.Imm {

    /** Virtual register of the enclosing function; registers {@code 0..params-1} are the
     * parameters. */
    record Reg(int id) implements Operand {
        @Override
        public String toString() {
            return "r" + id;
        }
    }

    /** Immediate value; truncated to the target word size when emitted. */
    record Imm(long value) implements Operand {
        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    static Imm imm(long value) {
        return new Imm(value);
    }
}
