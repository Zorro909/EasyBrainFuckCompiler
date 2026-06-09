package de.zorro909.brainfuck;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import de.zorro909.brainfuck.interpreter.Interpreter;
import de.zorro909.brainfuck.interpreter.InterpreterConfig;

/** Runs Brainfuck code through the interpreter with captured I/O. */
public final class BfTestSupport {

    public record RunResult(String output, byte[] memory, int pointer) {

        public int cell(int index) {
            return memory[index] & 0xFF;
        }
    }

    private BfTestSupport() {
    }

    public static RunResult run(String code) {
        return run(code, "");
    }

    public static RunResult run(String code, String input) {
        return run(code, input, InterpreterConfig.defaults());
    }

    public static RunResult run(String code, String input, InterpreterConfig config) {
        var in = new ByteArrayInputStream(input.getBytes(StandardCharsets.ISO_8859_1));
        var out = new ByteArrayOutputStream();
        var interpreter = new Interpreter(config, in, out);
        interpreter.interpret(code);
        return new RunResult(out.toString(StandardCharsets.ISO_8859_1),
                        interpreter.memorySnapshot(), interpreter.pointer());
    }
}
