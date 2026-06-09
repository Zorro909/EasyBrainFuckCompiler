package de.zorro909.brainfuck.core;

/** Thrown when a {@link VariableManager} memory region cannot fit another variable. */
public class BfMemoryException extends RuntimeException {

    public BfMemoryException(String message) {
        super(message);
    }
}
