package de.zorro909.brainfuck.transpiler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnsupportedConstructTest {

    private UnsupportedJavaConstructException transpileExpectingFailure(String body) {
        return assertThrows(UnsupportedJavaConstructException.class,
                        () -> TranspilerTestSupport.transpileBody(body));
    }

    @Test
    void arraysAreRejected() {
        transpileExpectingFailure("int[] numbers = new int[3];");
    }

    @Test
    void stringVariablesAreRejected() {
        var exception = transpileExpectingFailure("String s = \"hi\";");
        assertTrue(exception.getMessage().contains("String"));
    }

    @Test
    void negativeLiteralsAreRejected() {
        var exception = transpileExpectingFailure("int x = -1;");
        assertTrue(exception.getMessage().contains("Negative"));
    }

    @Test
    void unknownMethodCallsAreRejected() {
        var exception = transpileExpectingFailure("doSomething(1);");
        assertTrue(exception.getMessage().contains("doSomething"));
    }

    @Test
    void unknownVariablesAreRejected() {
        var exception = transpileExpectingFailure("x = 1;");
        assertTrue(exception.getMessage().contains("'x'"));
    }

    @Test
    void redeclarationIsRejected() {
        var exception = transpileExpectingFailure("""
                        int x = 1;
                        while (x < 3) {
                            int x = 2;
                        }
                        """);
        assertTrue(exception.getMessage().contains("already declared"));
    }

    @Test
    void additionalMethodsAreRejected() {
        var source = """
                        public class Program {
                            public static void main(String[] args) {
                            }
                            static void helper() {
                            }
                        }
                        """;
        assertThrows(UnsupportedJavaConstructException.class,
                        () -> new JavaToBrainfuckTranspiler().transpile(source));
    }

    @Test
    void fieldsAreRejected() {
        var source = """
                        public class Program {
                            static int counter = 0;
                            public static void main(String[] args) {
                            }
                        }
                        """;
        assertThrows(UnsupportedJavaConstructException.class,
                        () -> new JavaToBrainfuckTranspiler().transpile(source));
    }

    @Test
    void errorMessagesIncludeTheSourceLine() {
        var exception = transpileExpectingFailure("""
                        int x = 1;
                        int y = -5;
                        """);
        assertTrue(exception.getMessage().contains("line 4"),
                        "expected line info in: " + exception.getMessage());
    }

    @Test
    void forWithoutConditionIsRejected() {
        transpileExpectingFailure("for (;;) { }");
    }
}
