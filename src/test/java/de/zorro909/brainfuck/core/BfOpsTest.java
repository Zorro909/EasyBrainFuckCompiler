package de.zorro909.brainfuck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.zorro909.brainfuck.BfTestSupport;
import de.zorro909.brainfuck.interpreter.BrainfuckExecutionException;
import de.zorro909.brainfuck.interpreter.InterpreterConfig;

class BfOpsTest {

    private static final int SCRATCH_START = 0;
    private static final int SCRATCH_SIZE = 16;
    /** First cell outside the scratch region, free for test operands. */
    private static final int A = 16;
    private static final int B = 17;
    private static final int OUT = 18;

    private BfOps newOps() {
        return new BfOps(new CodeBuilder(), SCRATCH_START, SCRATCH_SIZE);
    }

    private BfTestSupport.RunResult run(BfOps ops) {
        return run(ops, "");
    }

    private BfTestSupport.RunResult run(BfOps ops, String input) {
        var result = BfTestSupport.run(ops.builder().code(), input);
        for (int i = 0; i < SCRATCH_SIZE; i++) {
            assertEquals(0, result.cell(SCRATCH_START + i),
                            "scratch cell " + i + " must be zero after all operations");
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 7, 255, 256, 300, -1 })
    void setStoresValueMod256(int value) {
        var ops = newOps();
        ops.addConst(A, 99);
        ops.set(A, value);
        assertEquals(Math.floorMod(value, 256), run(ops).cell(A));
    }

    @Test
    void addAndSubConstWrap() {
        var ops = newOps();
        ops.set(A, 250);
        ops.addConst(A, 10);
        ops.set(B, 3);
        ops.subConst(B, 5);
        var result = run(ops);
        assertEquals(4, result.cell(A));
        assertEquals(254, result.cell(B));
    }

    @Test
    void moveAddTransfersAndClearsSource() {
        var ops = newOps();
        ops.set(A, 11);
        ops.set(B, 5);
        ops.moveAdd(A, B);
        var result = run(ops);
        assertEquals(0, result.cell(A));
        assertEquals(16, result.cell(B));
    }

    @Test
    void moveSubSubtractsAndClearsSource() {
        var ops = newOps();
        ops.set(A, 3);
        ops.set(B, 10);
        ops.moveSub(A, B);
        var result = run(ops);
        assertEquals(0, result.cell(A));
        assertEquals(7, result.cell(B));
    }

    @Test
    void copyPreservesSource() {
        var ops = newOps();
        ops.set(A, 42);
        ops.set(B, 9);
        ops.copy(A, B);
        var result = run(ops);
        assertEquals(42, result.cell(A));
        assertEquals(42, result.cell(B));
    }

    @ParameterizedTest
    @CsvSource({ "5, 3, 8", "0, 7, 7", "200, 100, 44" })
    void addVarAddsAndPreservesSource(int a, int b, int expected) {
        var ops = newOps();
        ops.set(A, a);
        ops.set(B, b);
        ops.addVar(A, B);
        var result = run(ops);
        assertEquals(expected, result.cell(A));
        assertEquals(b, result.cell(B));
    }

    @ParameterizedTest
    @CsvSource({ "10, 3, 7", "7, 7, 0", "3, 5, 254" })
    void subVarSubtractsAndPreservesSource(int a, int b, int expected) {
        var ops = newOps();
        ops.set(A, a);
        ops.set(B, b);
        ops.subVar(A, B);
        var result = run(ops);
        assertEquals(expected, result.cell(A));
        assertEquals(b, result.cell(B));
    }

    @ParameterizedTest
    @CsvSource({ "0, 0", "0, 5", "1, 9", "3, 5", "15, 16", "16, 16" })
    void mulMultipliesMod256(int a, int b) {
        var ops = newOps();
        ops.set(A, a);
        ops.set(B, b);
        ops.mul(A, B, OUT);
        var result = run(ops);
        assertEquals((a * b) % 256, result.cell(OUT));
        assertEquals(a, result.cell(A));
        assertEquals(b, result.cell(B));
    }

    @ParameterizedTest
    @CsvSource({ "17, 5, 3, 2", "4, 7, 0, 4", "30, 1, 30, 0", "0, 3, 0, 0", "255, 16, 15, 15" })
    void divmodComputesQuotientAndRemainder(int n, int d, int q, int r) {
        var ops = newOps();
        ops.set(A, n);
        ops.set(B, d);
        ops.divmod(A, B, OUT, OUT + 1);
        var result = run(ops);
        assertEquals(q, result.cell(OUT));
        assertEquals(r, result.cell(OUT + 1));
        assertEquals(n, result.cell(A));
        assertEquals(d, result.cell(B));
    }

    @Test
    void divisionByZeroHitsTheStepLimit() {
        var ops = newOps();
        ops.set(A, 1);
        ops.set(B, 0);
        ops.divmod(A, B, OUT, OUT + 1);
        var config = InterpreterConfig.defaults().withMaxSteps(100_000);
        assertThrows(BrainfuckExecutionException.class,
                        () -> BfTestSupport.run(ops.builder().code(), "", config));
    }

    @ParameterizedTest
    @CsvSource({ "0, 0", "1, 1", "7, 1", "255, 1" })
    void toBoolNormalizes(int value, int expected) {
        var ops = newOps();
        ops.set(A, value);
        ops.toBool(A);
        assertEquals(expected, run(ops).cell(A));
    }

    @ParameterizedTest
    @CsvSource({ "0, 1", "1, 0", "7, 0" })
    void notInverts(int value, int expected) {
        var ops = newOps();
        ops.set(A, value);
        ops.not(A);
        assertEquals(expected, run(ops).cell(A));
    }

    @ParameterizedTest
    @CsvSource({ "0, 0, 0, 0", "0, 5, 0, 1", "3, 0, 0, 1", "2, 9, 1, 1" })
    void andOrTruthTable(int a, int b, int andResult, int orResult) {
        var ops = newOps();
        ops.set(A, a);
        ops.set(B, b);
        ops.and(A, B, OUT);
        ops.or(A, B, OUT + 1);
        var result = run(ops);
        assertEquals(andResult, result.cell(OUT));
        assertEquals(orResult, result.cell(OUT + 1));
        assertEquals(a, result.cell(A));
        assertEquals(b, result.cell(B));
    }

    @Test
    void comparisonTableForAllOperators() {
        int[] values = { 0, 1, 2, 5, 255 };
        for (int a : values) {
            for (int b : values) {
                var ops = newOps();
                ops.set(A, a);
                ops.set(B, b);
                ops.eq(A, B, OUT);
                ops.neq(A, B, OUT + 1);
                ops.lt(A, B, OUT + 2);
                ops.gt(A, B, OUT + 3);
                ops.le(A, B, OUT + 4);
                ops.ge(A, B, OUT + 5);
                var result = run(ops);
                String at = " for a=" + a + " b=" + b;
                assertEquals(a == b ? 1 : 0, result.cell(OUT), "eq" + at);
                assertEquals(a != b ? 1 : 0, result.cell(OUT + 1), "neq" + at);
                assertEquals(a < b ? 1 : 0, result.cell(OUT + 2), "lt" + at);
                assertEquals(a > b ? 1 : 0, result.cell(OUT + 3), "gt" + at);
                assertEquals(a <= b ? 1 : 0, result.cell(OUT + 4), "le" + at);
                assertEquals(a >= b ? 1 : 0, result.cell(OUT + 5), "ge" + at);
                assertEquals(a, result.cell(A), "operand a preserved" + at);
                assertEquals(b, result.cell(B), "operand b preserved" + at);
            }
        }
    }

    @Test
    void ifElseTakesTheRightBranch() {
        var ops = newOps();
        ops.set(A, 1);
        ops.ifElse(A, () -> ops.set(OUT, 'T'), () -> ops.set(OUT, 'F'));
        ops.set(B, 0);
        ops.ifElse(B, () -> ops.set(OUT + 1, 'T'), () -> ops.set(OUT + 1, 'F'));
        var result = run(ops);
        assertEquals('T', result.cell(OUT));
        assertEquals('F', result.cell(OUT + 1));
        assertEquals(1, result.cell(A), "condition preserved");
    }

    @Test
    void nestedIfElseWorks() {
        var ops = newOps();
        ops.set(A, 1);
        ops.set(B, 0);
        ops.ifElse(A,
                        () -> ops.ifElse(B, () -> ops.set(OUT, 1), () -> ops.set(OUT, 2)),
                        () -> ops.set(OUT, 3));
        assertEquals(2, run(ops).cell(OUT));
    }

    @Test
    void whileLoopCountsDown() {
        // sum = 5 + 4 + 3 + 2 + 1
        var ops = newOps();
        ops.set(A, 5);
        ops.whileLoop(B, () -> {
            ops.copy(A, B);
            ops.toBool(B);
        }, () -> {
            ops.addVar(OUT, A);
            ops.subConst(A, 1);
        });
        var result = run(ops);
        assertEquals(15, result.cell(OUT));
        assertEquals(0, result.cell(A));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 7, 10, 42, 100, 255 })
    void printIntPrintsDecimal(int value) {
        var ops = newOps();
        ops.set(A, value);
        ops.printInt(A);
        var result = run(ops);
        assertEquals(String.valueOf(value), result.output());
        assertEquals(value, result.cell(A), "printInt preserves the value");
    }

    @ParameterizedTest
    @CsvSource({ "0, 0", "7, 7", "200, 200", "300, 44" })
    void readIntParsesDigitsUntilNewline(int input, int expected) {
        var ops = newOps();
        ops.readInt(A);
        assertEquals(expected, run(ops, input + "\n").cell(A));
    }

    @Test
    void readIntStopsAtEndOfInputWithoutNewline() {
        var ops = newOps();
        ops.readInt(A);
        assertEquals(42, run(ops, "42").cell(A));
    }

    @Test
    void readsTwoNumbersAndAddsThem() {
        var ops = newOps();
        ops.readInt(A);
        ops.readInt(B);
        ops.addVar(A, B);
        ops.printInt(A);
        assertEquals("30", run(ops, "12\n18\n").output());
    }

    @Test
    void printStringPrintsLiteralAndCleansUp() {
        var ops = newOps();
        ops.printString("Fizz");
        ops.printString("Buzz\n");
        assertEquals("FizzBuzz\n", run(ops).output());
    }

    @Test
    void printAndReadCharRoundTrip() {
        var ops = newOps();
        ops.readChar(A);
        ops.printChar(A);
        ops.printChar(A);
        assertEquals("XX", run(ops, "X").output());
    }

    @Test
    void fizzBuzzStyleCombination() {
        // for i = 1..15: print "Fizz" if i % 3 == 0 else print i; newline
        var ops = newOps();
        int i = A;
        int limit = B;
        int flag = OUT;
        ops.set(i, 1);
        ops.set(limit, 16);
        ops.whileLoop(flag, () -> ops.lt(i, limit, flag), () -> {
            int q = OUT + 1;
            int r = OUT + 2;
            int three = OUT + 3;
            ops.set(three, 3);
            ops.divmod(i, three, q, r);
            ops.not(r);
            ops.ifElse(r, () -> ops.printString("Fizz"), () -> ops.printInt(i));
            ops.printString("\n");
            ops.addConst(i, 1);
        });
        var expected = new StringBuilder();
        for (int n = 1; n <= 15; n++) {
            expected.append(n % 3 == 0 ? "Fizz" : String.valueOf(n)).append('\n');
        }
        assertEquals(expected.toString(), run(ops).output());
    }
}
