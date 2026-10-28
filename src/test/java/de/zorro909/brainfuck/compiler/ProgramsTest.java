package de.zorro909.brainfuck.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.zorro909.brainfuck.bf.BfInterpreter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential tests: every program under {@code src/test/resources/programs} is run on the
 * JVM and as compiled Brainfuck; the outputs must be identical. An optional {@code Name.in}
 * file provides stdin, and a {@code // word-bytes: N} line selects the word size (8 makes
 * {@code double} and {@code long} exact).
 */
class ProgramsTest {

    @TestFactory
    Stream<DynamicTest> programs() throws IOException, URISyntaxException {
        Path dir = Path.of(ProgramsTest.class.getResource("/programs").toURI());
        return Files.list(dir).filter(p -> p.toString().endsWith(".java")).sorted()
                        .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(),
                                        () -> check(p)));
    }

    static void check(Path file) throws IOException {
        String source = Files.readString(file);
        String name = file.getFileName().toString().replace(".java", "");
        Path inFile = file.resolveSibling(name + ".in");
        String input = Files.exists(inFile) ? Files.readString(inFile) : "";
        String expected = JvmOracle.run(name, source, input);
        var options = JavaToBrainfuck.Options.defaults();
        var wordBytes = java.util.regex.Pattern.compile("// word-bytes: (\\d+)").matcher(source);
        if (wordBytes.find()) {
            options = options.withWordBytes(Integer.parseInt(wordBytes.group(1)));
        }
        assertEquals(expected, runBrainfuck(source, input, options), name);
    }

    static String runBrainfuck(String source, String input, JavaToBrainfuck.Options options) {
        var result = new JavaToBrainfuck(options).compile(source);
        var out = new ByteArrayOutputStream();
        new BfInterpreter(BfInterpreter.Config.defaults().withMaxSteps(20_000_000_000L)).run(
                        result.code(), new ByteArrayInputStream(input.getBytes(
                                        StandardCharsets.UTF_8)), out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
