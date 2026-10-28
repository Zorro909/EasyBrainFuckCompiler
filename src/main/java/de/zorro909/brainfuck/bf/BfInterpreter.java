package de.zorro909.brainfuck.bf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Executes a {@link BfProgram} on a tape of 8-bit wrapping cells.
 *
 * <p>The tape is finite (see {@link Config#tapeSize()}); leaving it is an error rather than a
 * silent wrap, which makes compiler bugs visible. Input at end of stream follows
 * {@link Config#eof()}.
 */
public final class BfInterpreter {

    /** What {@code ,} stores when the input is exhausted. */
    public enum Eof {
        ZERO, MINUS_ONE, UNCHANGED
    }

    /**
     * @param tapeSize number of cells
     * @param maxSteps upper bound for executed loop iterations, guards against endless loops
     * @param eof      end-of-input behavior of {@code ,}
     */
    public record Config(int tapeSize, long maxSteps, Eof eof) {

        public static Config defaults() {
            return new Config(1 << 22, Long.MAX_VALUE, Eof.ZERO);
        }

        public Config withMaxSteps(long steps) {
            return new Config(tapeSize, steps, eof);
        }

        public Config withTapeSize(int size) {
            return new Config(size, maxSteps, eof);
        }
    }

    /** Result of a run: final pointer, highest touched cell and executed loop iterations. */
    public record Stats(int pointer, int highWater, long loopIterations) {
    }

    private final Config config;
    private long[] profile;

    /** Enables per-instruction execution counts for the next run. */
    public void enableProfiling() {
        profile = new long[0];
    }

    /** Execution counts per compiled instruction of the last profiled run. */
    public long[] profile() {
        return profile;
    }

    public BfInterpreter(Config config) {
        this.config = config;
    }

    public BfInterpreter() {
        this(Config.defaults());
    }

    public Stats run(String source, InputStream in, OutputStream out) {
        return run(BfProgram.compile(source), in, out);
    }

    public Stats run(BfProgram program, InputStream in, OutputStream out) {
        final int[] op = program.op;
        final int[] a = program.a;
        final int[] b = program.b;
        final int len = program.length;
        final byte[] tape = new byte[config.tapeSize()];
        final int limit = tape.length;
        final long maxSteps = config.maxSteps();
        long steps = 0;
        int p = 0;
        int high = 0;
        byte[] outBuf = new byte[8192];
        int outLen = 0;
        final long[] counts = profile != null ? new long[len] : null;
        if (counts != null) {
            profile = counts;
        }
        try {
            for (int pc = 0; pc < len; pc++) {
                if (counts != null) {
                    counts[pc]++;
                }
                switch (op[pc]) {
                    case BfProgram.ADD -> tape[p + a[pc]] += (byte) b[pc];
                    case BfProgram.MOVE -> {
                        p += a[pc];
                        if (p < 0 || p >= limit) {
                            throw outOfTape(p);
                        }
                        if (p > high) {
                            high = p;
                        }
                    }
                    case BfProgram.CLEAR -> tape[p + a[pc]] = 0;
                    case BfProgram.MULADD -> tape[p + a[pc]] += (byte) (tape[p] * b[pc]);
                    case BfProgram.OPEN -> {
                        if (tape[p] == 0) {
                            pc = a[pc];
                        }
                    }
                    case BfProgram.CLOSE -> {
                        if (tape[p] != 0) {
                            pc = a[pc];
                            if (++steps > maxSteps) {
                                throw new BfExecutionException(
                                                "Exceeded " + maxSteps + " loop iterations");
                            }
                        }
                    }
                    case BfProgram.SCAN -> {
                        int stride = a[pc];
                        while (tape[p] != 0) {
                            p += stride;
                            if (p < 0 || p >= limit) {
                                throw outOfTape(p);
                            }
                        }
                        if (p > high) {
                            high = p;
                        }
                    }
                    case BfProgram.OUT -> {
                        if (outLen == outBuf.length) {
                            out.write(outBuf, 0, outLen);
                            outLen = 0;
                        }
                        outBuf[outLen++] = tape[p + a[pc]];
                    }
                    case BfProgram.IN -> {
                        if (outLen > 0) {
                            out.write(outBuf, 0, outLen);
                            outLen = 0;
                            out.flush();
                        }
                        int c = in.read();
                        if (c >= 0) {
                            tape[p + a[pc]] = (byte) c;
                        } else if (config.eof() == Eof.ZERO) {
                            tape[p + a[pc]] = 0;
                        } else if (config.eof() == Eof.MINUS_ONE) {
                            tape[p + a[pc]] = -1;
                        }
                    }
                    default -> throw new IllegalStateException("bad opcode " + op[pc]);
                }
            }
            out.write(outBuf, 0, outLen);
            out.flush();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new BfExecutionException("Data pointer left the tape near cell " + p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Stats(p, high, steps);
    }

    private static BfExecutionException outOfTape(int p) {
        return new BfExecutionException("Data pointer left the tape at cell " + p);
    }
}
