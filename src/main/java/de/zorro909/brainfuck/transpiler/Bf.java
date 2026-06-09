package de.zorro909.brainfuck.transpiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

/**
 * Intrinsics for transpiled programs. The transpiler recognizes calls to this class by
 * name and emits dedicated Brainfuck code; the implementations here run on the JVM with
 * matching semantics, so example programs can also be compiled with javac and used as a
 * differential-testing oracle.
 */
public final class Bf {

    private static final Random RANDOM = new Random();

    private Bf() {
    }

    /**
     * Reads a decimal number from stdin: consumes ASCII digits up to and including the
     * terminating newline (or end of input). Like all transpiled values it wraps mod 256.
     */
    public static int readInt() {
        try {
            int value = 0;
            int c = System.in.read();
            while (c > 0 && c != '\n') {
                value = (value * 10 + (c - '0')) % 256;
                c = System.in.read();
            }
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Invokes interpreter syscall {@code id} and returns the result cell: 0 halts the
     * program, 1 returns a random byte, 2 flushes the output.
     */
    public static int syscall(int id) {
        switch (id) {
            case 0 -> System.exit(0);
            case 1 -> {
                return RANDOM.nextInt(256);
            }
            case 2 -> System.out.flush();
            default -> throw new IllegalArgumentException("Unknown syscall id " + id);
        }
        return 0;
    }
}
