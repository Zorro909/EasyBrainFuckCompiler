package de.zorro909.brainfuck.interpreter;

import java.io.OutputStream;
import java.util.Map;
import java.util.Random;

/**
 * The built-in syscall table.
 *
 * <ul>
 *   <li>{@code 0} — halt the program (handled directly by the interpreter)</li>
 *   <li>{@code 1} — store a random byte in the argument cell</li>
 *   <li>{@code 2} — flush the output stream</li>
 * </ul>
 */
public final class Syscalls {

    public static final int HALT = 0;
    public static final int RANDOM = 1;
    public static final int FLUSH = 2;

    private Syscalls() {
    }

    public static Map<Integer, Syscall> defaults(OutputStream out) {
        var random = new Random();
        return Map.of(
                        RANDOM, (memory, pointer) -> {
                            var value = new byte[1];
                            random.nextBytes(value);
                            memory[(pointer + 1) % memory.length] = value[0];
                        },
                        FLUSH, (memory, pointer) -> out.flush());
    }
}
