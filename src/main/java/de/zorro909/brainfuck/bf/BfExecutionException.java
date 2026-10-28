package de.zorro909.brainfuck.bf;

/** Raised by {@link BfInterpreter} and {@link BfProgram}. */
public class BfExecutionException extends RuntimeException {

    public BfExecutionException(String message) {
        super(message);
    }
}
