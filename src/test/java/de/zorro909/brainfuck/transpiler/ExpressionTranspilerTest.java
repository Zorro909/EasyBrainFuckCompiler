package de.zorro909.brainfuck.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpressionTranspilerTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
                    "7 + 5; 12",
                    "9 - 4; 5",
                    "6 * 7; 42",
                    "17 / 5; 3",
                    "17 % 5; 2",
                    "2 + 3 * 4; 14",
                    "(2 + 3) * 4; 20",
                    "100 - 10 * 9; 10",
                    "255 + 2; 1",
                    "3 - 5; 254",
                    "(1 + 2) * (3 + 4); 21",
                    "84 / 2 / 7; 6" })
    void evaluatesArithmetic(String expression, String expected) {
        assertEquals(expected, TranspilerTestSupport
                        .runBody("System.out.print(" + expression + ");"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
                    "3 == 3; 1", "3 == 4; 0",
                    "3 != 4; 1", "3 != 3; 0",
                    "2 < 3; 1", "3 < 3; 0", "4 < 3; 0",
                    "4 > 3; 1", "3 > 3; 0",
                    "3 <= 3; 1", "4 <= 3; 0",
                    "3 >= 3; 1", "2 >= 3; 0" })
    void evaluatesComparisons(String expression, String expected) {
        assertEquals(expected, TranspilerTestSupport
                        .runBody("System.out.print(" + expression + ");"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
                    "1 == 1 && 2 == 2; 1",
                    "1 == 1 && 2 == 3; 0",
                    "1 == 2 || 3 == 3; 1",
                    "1 == 2 || 3 == 4; 0",
                    "!(1 == 2); 1",
                    "!(1 == 1); 0" })
    void evaluatesBooleanLogic(String expression, String expected) {
        assertEquals(expected, TranspilerTestSupport
                        .runBody("System.out.print(" + expression + ");"));
    }

    @Test
    void variablesParticipateInExpressions() {
        var output = TranspilerTestSupport.runBody("""
                        int a = 6;
                        int b = a * 7;
                        System.out.print(b - a);
                        """);
        assertEquals("36", output);
    }

    @Test
    void charLiteralsAndCastsArePrintedAsCharacters() {
        var output = TranspilerTestSupport.runBody("""
                        char c = 'A';
                        System.out.print(c);
                        System.out.print('!');
                        System.out.print((char) (c + 1));
                        System.out.print(c + 1);
                        """);
        assertEquals("A!B66", output);
    }

    @Test
    void compoundAssignmentsAndIncrements() {
        var output = TranspilerTestSupport.runBody("""
                        int x = 10;
                        x += 5;
                        x -= 3;
                        x *= 2;
                        x /= 3;
                        x %= 5;
                        x++;
                        ++x;
                        x--;
                        System.out.print(x);
                        """);
        // ((10+5-3)*2)/3 = 8, 8%5 = 3, +1 +1 -1 = 4
        assertEquals("4", output);
    }

    @Test
    void assignmentOverwritesPreviousValue() {
        var output = TranspilerTestSupport.runBody("""
                        int x = 200;
                        x = 7;
                        System.out.print(x);
                        """);
        assertEquals("7", output);
    }
}
