package de.zorro909.brainfuck.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;

class InterpreterTest {

    @Test
    void incrementsAndOutputs() {
        var result = BfTestSupport.run("+++.");
        assertEquals(3, result.output().charAt(0));
        assertEquals(3, result.cell(0));
        assertEquals(0, result.pointer());
    }

    @Test
    void decrementWrapsBelowZero() {
        var result = BfTestSupport.run("-");
        assertEquals(255, result.cell(0));
    }

    @Test
    void incrementWrapsAbove255() {
        var result = BfTestSupport.run("+".repeat(258));
        assertEquals(2, result.cell(0));
    }

    @Test
    void movesPointerAndWritesCells() {
        var result = BfTestSupport.run("+>++>+++");
        assertEquals(1, result.cell(0));
        assertEquals(2, result.cell(1));
        assertEquals(3, result.cell(2));
        assertEquals(2, result.pointer());
    }

    @Test
    void executesNestedLoops() {
        // 2 outer iterations each add 2 to cell 1; inner loop moves cell 1 to cell 2
        var result = BfTestSupport.run("++[>++[>+<-]<-]");
        assertEquals(0, result.cell(0));
        assertEquals(0, result.cell(1));
        assertEquals(4, result.cell(2));
    }

    @Test
    void skipsNestedLoopWhenCellIsZero() {
        // the old interpreter mis-skipped nested brackets and never ran the trailing +
        var result = BfTestSupport.run("[[-]]++");
        assertEquals(2, result.cell(0));
    }

    @Test
    void skipsTriplyNestedLoopWhenCellIsZero() {
        var result = BfTestSupport.run("[[[-]]>[-]<]+");
        assertEquals(1, result.cell(0));
    }

    @Test
    void clearLoopInsideRunningLoopTerminates() {
        var result = BfTestSupport.run("+++[[-]]");
        assertEquals(0, result.cell(0));
    }

    @Test
    void printsHelloWorld() {
        var helloWorld = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]"
                        + ">>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++.";
        assertEquals("Hello World!\n", BfTestSupport.run(helloWorld).output());
    }

    @Test
    void catProgramEchoesInputIncludingWhitespace() {
        // Scanner-based input in the old interpreter could not read spaces or newlines
        var input = "hi there\nsecond line\n";
        assertEquals(input, BfTestSupport.run(",[.,]", input).output());
    }

    @Test
    void readStoresZeroAtEndOfInput() {
        var result = BfTestSupport.run("+++,.", "");
        assertEquals(0, result.cell(0));
        assertEquals(0, result.output().charAt(0));
    }

    @Test
    void unbalancedOpenBracketThrows() {
        assertThrows(BrainfuckSyntaxException.class, () -> BfTestSupport.run("+[>"));
    }

    @Test
    void unbalancedCloseBracketThrows() {
        assertThrows(BrainfuckSyntaxException.class, () -> BfTestSupport.run("+]"));
        assertThrows(BrainfuckSyntaxException.class, () -> BfTestSupport.run("[]]"));
    }

    @Test
    void endlessLoopHitsStepLimit() {
        var config = InterpreterConfig.defaults().withMaxSteps(10_000);
        assertThrows(BrainfuckExecutionException.class,
                        () -> BfTestSupport.run("+[]", "", config));
    }

    @Test
    void pointerWrapsAroundTheTape() {
        var config = InterpreterConfig.defaults().withMemorySize(16);
        var result = BfTestSupport.run("<+", "", config);
        assertEquals(15, result.pointer());
        assertEquals(1, result.cell(15));

        var forward = BfTestSupport.run(">".repeat(16), "", config);
        assertEquals(0, forward.pointer());
    }

    @Test
    void pointerLeavingTapeThrowsWithoutWrapping() {
        var config = InterpreterConfig.defaults().withWrapPointer(false);
        assertThrows(BrainfuckExecutionException.class, () -> BfTestSupport.run("<", "", config));
    }

    @Test
    void ignoresNonCommandCharacters() {
        var result = BfTestSupport.run("abc f\noo +++ bar! &");
        assertEquals(3, result.cell(0));
    }

    @Test
    void syscallOpcodeIsRejectedWithoutExtensions() {
        assertThrows(BrainfuckSyntaxException.class, () -> BfTestSupport.run("+@"));
    }
}
