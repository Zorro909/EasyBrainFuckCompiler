package de.zorro909.brainfuck.compiler.ir;

public enum BinOp {
    ADD, SUB, MUL, DIV, REM, AND, OR, XOR, SHL, SHR, USHR, EQ, NE, LT, LE, GT, GE;

    public boolean isComparison() {
        return ordinal() >= EQ.ordinal();
    }

    public boolean isCommutative() {
        return switch (this) {
            case ADD, MUL, AND, OR, XOR, EQ, NE -> true;
            default -> false;
        };
    }

    /** Comparison with swapped operands: {@code a < b == b > a}. */
    public BinOp swapped() {
        return switch (this) {
            case LT -> GT;
            case GT -> LT;
            case LE -> GE;
            case GE -> LE;
            default -> this;
        };
    }
}
