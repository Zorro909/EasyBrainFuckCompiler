package de.zorro909.brainfuck.compiler.ir;

import de.zorro909.brainfuck.compiler.ir.Operand.Reg;

/** Straight-line instructions inside a {@link Block}. */
public sealed interface Insn {

    record Move(Reg dst, Operand src) implements Insn {
    }

    record Unary(UnOp op, Reg dst, Operand src) implements Insn {
    }

    record Binary(BinOp op, Reg dst, Operand a, Operand b) implements Insn {
    }

    /** {@code dst = heap[ref + index + offset]}; references are word distances from the heap
     * top, see the backend documentation. */
    record Load(Reg dst, Operand ref, Operand index, int offset) implements Insn {
    }

    /** {@code heap[ref + index + offset] = value}. */
    record Store(Operand ref, Operand index, int offset, Operand value) implements Insn {
    }

    /** Writes the low byte of the value to stdout. */
    record PutChar(Operand value) implements Insn {
    }

    /** Reads one byte from stdin; -1 at end of input. */
    record GetChar(Reg dst) implements Insn {
    }
}
