package de.zorro909.brainfuck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;

class StringRoundTripTest {

    private BrainFuckScript newScript() {
        var script = new BrainFuckScript();
        script.setupVariableManager(3, 60, 70, 100);
        return script;
    }

    @Test
    void savedStringIsPrintedBackWithTrailingNewline() {
        var script = newScript();
        Variable variable = script.variableManager().createVariable(10);
        script.saveString(variable, "Hello");
        script.printString(variable);

        var result = BfTestSupport.run(script.getScript());
        assertEquals("Hello\n", result.output());
        assertEquals(0, result.pointer());
        assertEquals('H', result.cell(variable.dataCell(0)));
        assertEquals(0, result.cell(variable.cell()), "guard cell must stay zero");
    }

    @Test
    void stringFillingTheVariableCompletelyUsesTheTerminatorCell() {
        var script = newScript();
        Variable variable = script.variableManager().createVariable(5);
        script.saveString(variable, "Hello");
        script.printString(variable);

        var result = BfTestSupport.run(script.getScript());
        assertEquals("Hello\n", result.output());
        assertEquals('\n', result.cell(variable.dataCell(5)));
    }

    @Test
    void tooLongStringIsRejected() {
        var script = newScript();
        Variable variable = script.variableManager().createVariable(3);
        assertThrows(IllegalArgumentException.class,
                        () -> script.saveString(variable, "Hello"));
    }

    @Test
    void twoVariablesDoNotInterfere() {
        var script = newScript();
        Variable first = script.variableManager().createVariable(6);
        Variable second = script.variableManager().createVariable(6);
        script.saveString(first, "abc");
        script.saveString(second, "xyz");
        script.printString(second);
        script.printString(first);

        assertEquals("xyz\nabc\n", BfTestSupport.run(script.getScript()).output());
    }

    @Test
    void printsStringLiteralAndCleansUp() {
        var script = newScript();
        script.printString("Hi there!");

        var result = BfTestSupport.run(script.getScript());
        assertEquals("Hi there!", result.output());
        assertEquals(0, result.cell(1), "working cell must end zero");
        assertEquals(0, result.pointer());
    }

    @Test
    void printsRepeatedCharactersViaDeltaEncoding() {
        var script = newScript();
        script.printString("aaab");

        assertEquals("aaab", BfTestSupport.run(script.getScript()).output());
    }

    @Test
    void printsEmptyLiteral() {
        var script = newScript();
        script.printString("");

        assertEquals("", BfTestSupport.run(script.getScript()).output());
    }

    @Test
    void printCharacterRestoresTheWorkingCell() {
        var script = newScript();
        script.printCharacter((byte) 'A');
        script.printCharacter((byte) 'B');

        var result = BfTestSupport.run(script.getScript());
        assertEquals("AB", result.output());
        assertEquals(0, result.cell(1));
    }

    @Test
    void printCharacterWithKnownCellValueEmitsOnlyTheDifference() {
        var script = newScript();
        script.printCharacter((byte) 'b');
        // working cell is zero again; print 'a' then 'b' then 'b' via known values
        script.printCharacter((byte) 'a', 0);
        script.printCharacter((byte) 'b', 'a');
        script.printCharacter((byte) 'b', 'b');

        assertEquals("babb", BfTestSupport.run(script.getScript()).output());
    }
}
