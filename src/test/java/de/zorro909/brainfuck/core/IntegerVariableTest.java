package de.zorro909.brainfuck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.zorro909.brainfuck.BfTestSupport;

class IntegerVariableTest {

    private BrainFuckScript newScript() {
        var script = new BrainFuckScript();
        script.setupVariableManager(3, 60, 70, 100);
        return script;
    }

    // regression: importFromBit used to emit pointer moves that never executed,
    // producing endless loops or values in the wrong cell
    @ParameterizedTest
    @CsvSource({ "1111, 15", "1010, 10", "0001, 1", "0000, 0", "1000, 8",
                    "11111111, 255", "10000000, 128", "01011010, 90" })
    void importsBinaryDigitStringsAndPrintsDecimal(String bits, String expected) {
        var script = newScript();
        Variable source = script.variableManager().createVariable(bits.length());
        script.variableManager().setVariable(source, bits);
        IntegerVariable integer = script.variableManager().createIntegerVariable();
        integer.importFromBit(source);
        integer.print();

        var result = BfTestSupport.run(script.getScript());
        assertEquals(expected, result.output());
        assertEquals(Integer.parseInt(expected), result.cell(integer.valueCell()),
                        "print must preserve the value cell");
        for (int i = 0; i < source.maxLength(); i++) {
            assertEquals(0, result.cell(source.dataCell(i)), "import consumes the digits");
        }
        assertEquals(0, result.pointer());
        assertEquals(0, script.builder().pointer());
    }

    @Test
    void importRequiresWholeBitGroups() {
        var script = newScript();
        Variable source = script.variableManager().createVariable(3);
        IntegerVariable integer = script.variableManager().createIntegerVariable();
        assertThrows(IllegalArgumentException.class, () -> integer.importFromBit(source));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 7, 10, 100, 255 })
    void printsDirectlyStoredValues(int value) {
        var script = newScript();
        IntegerVariable integer = script.variableManager().createIntegerVariable();
        script.builder().moveTo(integer.valueCell());
        script.add(value);
        script.builder().moveTo(0);
        integer.print();

        var result = BfTestSupport.run(script.getScript());
        assertEquals(String.valueOf(value), result.output());
        assertEquals(0, result.pointer());
    }

    @Test
    void printTwiceYieldsTheSameNumber() {
        var script = newScript();
        IntegerVariable integer = script.variableManager().createIntegerVariable();
        script.builder().moveTo(integer.valueCell());
        script.add(42);
        script.builder().moveTo(0);
        integer.print();
        integer.print();

        assertEquals("4242", BfTestSupport.run(script.getScript()).output());
    }
}
