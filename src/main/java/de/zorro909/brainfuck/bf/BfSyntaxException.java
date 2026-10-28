package de.zorro909.brainfuck.bf;

/** Raised by {@link BfInterpreter} and {@link BfProgram}. */
public class BfSyntaxException extends RuntimeException {

    public BfSyntaxException(String message) {
        super(message);
    }
}
