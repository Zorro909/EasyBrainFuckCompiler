package de.zorro909.brainfuck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;

class ReadStringTest {

    private BrainFuckScript newScript() {
        var script = new BrainFuckScript();
        script.setupVariableManager(3, 60, 70, 110);
        return script;
    }

    @Test
    void echoesOneInputLine() {
        var script = newScript();
        InputVariable input = script.readString(10);
        script.printString(input);

        assertEquals("hi\n", BfTestSupport.run(script.getScript(), "hi\n").output());
    }

    @Test
    void readsLineContainingSpaces() {
        var script = newScript();
        InputVariable input = script.readString(12);
        script.printString(input);

        assertEquals("a b c\n", BfTestSupport.run(script.getScript(), "a b c\n").output());
    }

    @Test
    void readsTwoLinesIntoSeparateVariables() {
        var script = newScript();
        InputVariable first = script.readString(8);
        InputVariable second = script.readString(8);
        script.printString(second);
        script.printString(first);

        assertEquals("two\none\n",
                        BfTestSupport.run(script.getScript(), "one\ntwo\n").output());
    }

    @Test
    void copyToNormalMovesContentIntoTheStringRegion() {
        var script = newScript();
        InputVariable input = script.readString(8);
        Variable copy = input.copyToNormal();
        script.printString(copy);

        var result = BfTestSupport.run(script.getScript(), "moved\n");
        assertEquals("moved\n", result.output());
        for (int i = 0; i <= input.maxLength(); i++) {
            assertEquals(0, result.cell(input.dataCell(i)), "input variable must be cleared");
        }
    }

    @Test
    void inputCellsHoldTheCharactersAfterReading() {
        var script = newScript();
        InputVariable input = script.readString(6);

        var result = BfTestSupport.run(script.getScript(), "ok\n");
        assertEquals('o', result.cell(input.dataCell(0)));
        assertEquals('k', result.cell(input.dataCell(1)));
        assertEquals('\n', result.cell(input.dataCell(2)));
        assertEquals(0, result.cell(input.cell()), "guard cell must stay zero");
    }

    // Known limitation, kept for documentation: the restore walk of the hand-written
    // read routine adds 11 to every cell until it hits a zero, so a raw input byte of
    // value 11 (vertical tab) terminates the restore early and corrupts the line.
}
