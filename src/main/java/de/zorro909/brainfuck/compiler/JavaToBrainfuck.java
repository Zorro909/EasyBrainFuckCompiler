package de.zorro909.brainfuck.compiler;

import de.zorro909.brainfuck.compiler.backend.BfBackend;
import de.zorro909.brainfuck.compiler.frontend.Frontend;
import de.zorro909.brainfuck.compiler.ir.Program;
import java.util.List;

/**
 * Compiles Java source code to Brainfuck: {@link Frontend} (Java to IR) followed by
 * {@link BfBackend} (IR to Brainfuck).
 *
 * <p>The generated program runs on any Brainfuck implementation with 8-bit wrapping cells and a
 * large enough tape (see {@link Result#tapeCells()}); values of type {@code int} are
 * {@link Options#wordBytes()} bytes wide.
 */
public final class JavaToBrainfuck {

    /**
     * @param wordBytes bytes per Java value: 4 gives exact 32-bit {@code int} semantics, 2 is
     *                  faster but wraps at 16 bits, 8 makes {@code long} exact
     * @param heapWords heap size in words (objects, arrays, strings, static fields)
     */
    public record Options(int wordBytes, int heapWords) {
        public static Options defaults() {
            return new Options(4, 16384);
        }

        public Options withWordBytes(int w) {
            return new Options(w, heapWords);
        }

        public Options withHeapWords(int h) {
            return new Options(wordBytes, h);
        }
    }

    /**
     * @param code      the Brainfuck program
     * @param frameSize cells per stack frame
     * @param tapeCells cells needed for the heap plus the first frame; every call level adds
     *                  {@code frameSize} more
     */
    public record Result(String code, int frameSize, int tapeCells, int functions, int blocks,
                    List<BfBackend.Marker> markers) {
    }

    private final Options options;

    public JavaToBrainfuck(Options options) {
        this.options = options;
    }

    public JavaToBrainfuck() {
        this(Options.defaults());
    }

    public Result compile(String source) {
        return compile(List.of(new Frontend.Source("Main.java", source)));
    }

    public Result compile(List<Frontend.Source> sources) {
        Program program = new Frontend(new Frontend.Options(options.wordBytes(),
                        options.heapWords())).compile(sources);
        BfBackend.Output out = new BfBackend(new BfBackend.Options(options.wordBytes(),
                        options.heapWords())).compile(program);
        return new Result(out.code(), out.frameSize(), out.tapeBase() + out.frameSize(),
                        out.functions(), out.blocks(), out.markers());
    }
}
