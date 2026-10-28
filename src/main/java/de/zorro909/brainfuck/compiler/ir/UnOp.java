package de.zorro909.brainfuck.compiler.ir;

public enum UnOp {
    /** two's complement negation */
    NEG,
    /** bitwise complement */
    NOT,
    /** logical not: 1 if zero, else 0 */
    LNOT,
    /** narrowing casts with Java semantics */
    I2B, I2C, I2S
}
