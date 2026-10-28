package de.zorro909.brainfuck.compiler.ir;

/** Functions whose body is generated directly by the backend (bit-level arithmetic). */
public enum Builtin {
    MUL(2), DIV(2), REM(2), AND(2), OR(2), XOR(2), SHL(2), SHR(2), USHR(2), PRINT_INT(1);

    public final int arity;

    Builtin(int arity) {
        this.arity = arity;
    }

    public boolean returnsValue() {
        return this != PRINT_INT;
    }

    public static Builtin of(BinOp op) {
        return switch (op) {
            case MUL -> MUL;
            case DIV -> DIV;
            case REM -> REM;
            case AND -> AND;
            case OR -> OR;
            case XOR -> XOR;
            case SHL -> SHL;
            case SHR -> SHR;
            case USHR -> USHR;
            default -> null;
        };
    }
}
