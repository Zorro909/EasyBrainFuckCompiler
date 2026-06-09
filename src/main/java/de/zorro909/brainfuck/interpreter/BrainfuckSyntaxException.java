package de.zorro909.brainfuck.interpreter;

/** Thrown when a Brainfuck program is structurally invalid (e.g. unbalanced brackets). */
public class BrainfuckSyntaxException extends RuntimeException {

    public BrainfuckSyntaxException(String message) {
        super(message);
    }
}
