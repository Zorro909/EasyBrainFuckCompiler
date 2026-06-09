package de.zorro909.brainfuck.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlFlowTest {

    @Test
    void ifWithoutElse() {
        var output = TranspilerTestSupport.runBody("""
                        int x = 5;
                        if (x > 3) {
                            System.out.print("big");
                        }
                        if (x > 9) {
                            System.out.print("huge");
                        }
                        """);
        assertEquals("big", output);
    }

    @Test
    void ifElseTakesTheCorrectBranch() {
        var output = TranspilerTestSupport.runBody("""
                        int x = 2;
                        if (x % 2 == 0) {
                            System.out.print("even");
                        } else {
                            System.out.print("odd");
                        }
                        """);
        assertEquals("even", output);
    }

    @Test
    void nestedIfElse() {
        var output = TranspilerTestSupport.runBody("""
                        int x = 15;
                        if (x % 3 == 0) {
                            if (x % 5 == 0) {
                                System.out.print("FizzBuzz");
                            } else {
                                System.out.print("Fizz");
                            }
                        } else {
                            System.out.print("none");
                        }
                        """);
        assertEquals("FizzBuzz", output);
    }

    @Test
    void whileLoopCountsDown() {
        var output = TranspilerTestSupport.runBody("""
                        int i = 5;
                        while (i > 0) {
                            System.out.print(i);
                            i = i - 1;
                        }
                        """);
        assertEquals("54321", output);
    }

    @Test
    void whileLoopWithFalseConditionNeverRuns() {
        var output = TranspilerTestSupport.runBody("""
                        int i = 0;
                        while (i > 0) {
                            System.out.print("never");
                        }
                        System.out.print("done");
                        """);
        assertEquals("done", output);
    }

    @Test
    void nestedWhileLoopsBuildAMultiplicationTable() {
        var output = TranspilerTestSupport.runBody("""
                        int i = 1;
                        while (i <= 3) {
                            int j = 1;
                            while (j <= 3) {
                                System.out.print(i * j);
                                System.out.print(" ");
                                j++;
                            }
                            System.out.println();
                            i++;
                        }
                        """);
        assertEquals("1 2 3 \n2 4 6 \n3 6 9 \n", output);
    }

    @Test
    void forLoopIsDesugaredToWhile() {
        var output = TranspilerTestSupport.runBody("""
                        for (int i = 0; i < 4; i++) {
                            System.out.print(i);
                        }
                        """);
        assertEquals("0123", output);
    }

    @Test
    void forLoopWithCompoundUpdate() {
        var output = TranspilerTestSupport.runBody("""
                        int sum = 0;
                        for (int i = 1; i <= 10; i += 1) {
                            sum += i;
                        }
                        System.out.print(sum);
                        """);
        assertEquals("55", output);
    }

    @Test
    void loopVariableDeclaredInsideBodyIsReinitializedEachIteration() {
        var output = TranspilerTestSupport.runBody("""
                        int i = 0;
                        while (i < 3) {
                            int doubled = i * 2;
                            System.out.print(doubled);
                            i++;
                        }
                        """);
        assertEquals("024", output);
    }

    @Test
    void conditionWithLogicalOperators() {
        var output = TranspilerTestSupport.runBody("""
                        int i = 0;
                        while (i < 10 && i * i < 30) {
                            i++;
                        }
                        System.out.print(i);
                        """);
        assertEquals("6", output);
    }
}
