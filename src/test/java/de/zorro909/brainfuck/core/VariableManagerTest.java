package de.zorro909.brainfuck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;

class VariableManagerTest {

    private BrainFuckScript newScript() {
        var script = new BrainFuckScript();
        script.setupVariableManager(3, 40, 50, 70);
        return script;
    }

    @Test
    void stringVariablesAreAllocatedWithoutOverlap() {
        var manager = newScript().variableManager();
        Variable first = manager.createVariable(4);
        Variable second = manager.createVariable(4);

        assertEquals(3, first.cell());
        // guard + 4 data cells + terminator = 6 cells per variable
        assertEquals(9, second.cell());
        assertTrue(first.dataCell(first.maxLength()) < second.cell(),
                        "terminator of the first variable must lie before the second");
    }

    @Test
    void inputVariablesGrowUpwardInsideTheirRegion() {
        // regression: the input allocator used to grow downward out of its region
        var manager = newScript().variableManager();
        InputVariable first = manager.createInputVariable(3);
        InputVariable second = manager.createInputVariable(3);

        assertEquals(50, first.cell());
        assertTrue(second.cell() > first.cell(), "allocation must grow upward");
        assertTrue(second.cell() + second.maxLength() + 1 <= 70,
                        "allocation must stay inside the input region");
    }

    @Test
    void exhaustedStringRegionThrows() {
        var manager = newScript().variableManager();
        manager.createVariable(20);
        assertThrows(BfMemoryException.class, () -> manager.createVariable(20));
    }

    @Test
    void exhaustedInputRegionThrows() {
        var manager = newScript().variableManager();
        manager.createInputVariable(10);
        assertThrows(BfMemoryException.class, () -> manager.createInputVariable(10));
    }

    @Test
    void copyVariableMovesContentForward() {
        var script = newScript();
        var manager = script.variableManager();
        Variable source = manager.createVariable(6);
        Variable target = manager.createVariable(6);
        script.saveString(source, "ab");
        manager.copyVariable(source, target);
        script.printString(target);

        var result = BfTestSupport.run(script.getScript());
        assertEquals("ab\n", result.output());
        for (int i = 0; i <= source.maxLength(); i++) {
            assertEquals(0, result.cell(source.dataCell(i)), "move must clear the source");
        }
    }

    @Test
    void copyVariableMovesContentBackward() {
        var script = newScript();
        var manager = script.variableManager();
        Variable first = manager.createVariable(6);
        Variable second = manager.createVariable(6);
        script.saveString(second, "xy");
        manager.copyVariable(second, first);
        script.printString(first);

        assertEquals("xy\n", BfTestSupport.run(script.getScript()).output());
    }

    @Test
    void copyIntoSmallerVariableIsRejected() {
        var manager = newScript().variableManager();
        Variable big = manager.createVariable(8);
        Variable small = manager.createVariable(4);
        assertThrows(IllegalArgumentException.class, () -> manager.copyVariable(big, small));
    }

    @Test
    void clearVariableZeroesExactlyItsCells() {
        var script = newScript();
        var manager = script.variableManager();
        Variable cleared = manager.createVariable(4);
        Variable neighbour = manager.createVariable(4);
        script.saveString(cleared, "abcd");
        script.saveString(neighbour, "wxyz");
        manager.clearVariable(cleared);

        var result = BfTestSupport.run(script.getScript());
        for (int i = 0; i <= cleared.maxLength(); i++) {
            assertEquals(0, result.cell(cleared.dataCell(i)));
        }
        assertEquals('w', result.cell(neighbour.dataCell(0)), "neighbour must be untouched");
        assertEquals(0, result.pointer());
    }

    @Test
    void invalidRegionsAreRejected() {
        var script = new BrainFuckScript();
        assertThrows(IllegalArgumentException.class,
                        () -> script.setupVariableManager(10, 5, 50, 60));
        assertThrows(IllegalArgumentException.class,
                        () -> new BrainFuckScript().setupVariableManager(3, 40, 60, 50));
    }
}
