package de.zorro909.brainfuck.interpreter;

/** Thrown when a Brainfuck program fails at runtime (step limit, tape bounds, syscalls). */
public class BrainfuckExecutionException extends RuntimeException {

    public BrainfuckExecutionException(String message) {
        super(message);
    }
}
