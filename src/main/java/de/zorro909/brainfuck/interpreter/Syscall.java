package de.zorro909.brainfuck.interpreter;

import java.io.IOException;

/**
 * A system call invocable from Brainfuck code via the extension opcode {@code '@'}.
 *
 * <p>When the interpreter executes {@code '@'}, the value of the current cell selects
 * the syscall and {@code pointer + 1} addresses the argument/result cell. Syscall id 0
 * (halt) is handled by the interpreter itself and cannot be overridden.
 */
@FunctionalInterface
public interface Syscall {

    void invoke(byte[] memory, int pointer) throws IOException;
}
