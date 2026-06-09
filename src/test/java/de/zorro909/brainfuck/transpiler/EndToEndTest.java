package de.zorro909.brainfuck.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;

/** Transpiles the shipped example programs and verifies their exact output. */
class EndToEndTest {

    private String runExample(String name, String input) throws IOException {
        var source = Files.readString(Path.of("examples", name));
        var code = new JavaToBrainfuckTranspiler().transpile(source);
        return BfTestSupport.run(code, input).output();
    }

    @Test
    void fizzBuzz() throws IOException {
        var expected = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            if (i % 15 == 0) {
                expected.append("FizzBuzz\n");
            } else if (i % 3 == 0) {
                expected.append("Fizz\n");
            } else if (i % 5 == 0) {
                expected.append("Buzz\n");
            } else {
                expected.append(i).append('\n');
            }
        }
        assertEquals(expected.toString(), runExample("FizzBuzz.java", ""));
    }

    @Test
    void echo() throws IOException {
        var input = "hello brainfuck\nsecond line";
        assertEquals(input, runExample("Echo.java", input));
    }

    @Test
    void countdown() throws IOException {
        assertEquals("5\n4\n3\n2\n1\nLiftoff!\n", runExample("Countdown.java", "5\n"));
    }

    @Test
    void sumInput() throws IOException {
        assertEquals("33 + 9 = 42\n", runExample("SumInput.java", "33\n9\n"));
    }
}
