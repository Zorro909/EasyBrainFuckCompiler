package de.zorro909.brainfuck.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;

class MainDemoTest {

    @Test
    void originalDemoPrints15() {
        var script = Main.demoScript();
        assertEquals("15", BfTestSupport.run(script.getScript()).output());
    }
}
