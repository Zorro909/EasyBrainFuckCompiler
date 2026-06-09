package de.zorro909.brainfuck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.zorro909.brainfuck.BfTestSupport;

class AddSubTest {

    private static final int CELL = 5;

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 7, 13, 25, 26, 48, 100, 251, 255 })
    void addLoadsConstantAndLeavesScratchClean(int n) {
        var script = new BrainFuckScript();
        script.builder().moveTo(CELL);
        script.add(n);

        assertEquals(CELL, script.builder().pointer(), "compile-time pointer");
        var result = BfTestSupport.run(script.getScript());
        assertEquals(n, result.cell(CELL));
        assertEquals(0, result.cell(CELL + 1), "scratch cell must end zero");
        assertEquals(CELL, result.pointer(), "runtime pointer");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 7, 13, 25, 26, 48, 100, 251, 255 })
    void subSubtractsConstant(int n) {
        var script = new BrainFuckScript();
        script.builder().moveTo(CELL);
        script.add(255);
        script.sub(n);

        var result = BfTestSupport.run(script.getScript());
        assertEquals(255 - n, result.cell(CELL));
        assertEquals(0, result.cell(CELL + 1));
        assertEquals(CELL, result.pointer());
    }

    @Test
    void addWrapsAt256() {
        var script = new BrainFuckScript();
        script.builder().moveTo(CELL);
        script.add(200);
        script.add(200);

        assertEquals(144, BfTestSupport.run(script.getScript()).cell(CELL));
    }

    @Test
    void subWrapsBelowZero() {
        var script = new BrainFuckScript();
        script.builder().moveTo(CELL);
        script.add(5);
        script.sub(7);

        assertEquals(254, BfTestSupport.run(script.getScript()).cell(CELL));
    }

    @Test
    void negativeConstantIsRejected() {
        var script = new BrainFuckScript();
        assertThrows(IllegalArgumentException.class, () -> script.add(-1));
        assertThrows(IllegalArgumentException.class, () -> script.sub(-1));
    }

    @Test
    void zeroEmitsNothing() {
        var script = new BrainFuckScript();
        script.add(0);
        script.sub(0);
        assertEquals("", script.getScript());
    }
}
