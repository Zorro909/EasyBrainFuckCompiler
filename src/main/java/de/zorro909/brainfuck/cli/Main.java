package de.zorro909.brainfuck.cli;

import de.zorro909.brainfuck.core.BrainFuckScript;
import de.zorro909.brainfuck.core.IntegerVariable;
import de.zorro909.brainfuck.core.Variable;
import de.zorro909.brainfuck.core.VariableManager;
import de.zorro909.brainfuck.interpreter.Interpreter;

public class Main {

    public static void main(String[] args) {
        BrainFuckScript script = demoScript();
        System.out.println("Compiling Finished (Length: " + script.getScript().length() + ")");
        System.out.println(script.getScript());
        new Interpreter().interpret(script.getScript());
    }

    /** The original demo: store the bit string "1111" and print it as the decimal 15. */
    static BrainFuckScript demoScript() {
        var script = new BrainFuckScript();
        VariableManager manager = script.setupVariableManager(3, 40, 50, 60);
        Variable bits = manager.createVariable(4);
        manager.setVariable(bits, "1111");
        IntegerVariable integer = manager.createIntegerVariable();
        integer.importFromBit(bits);
        integer.print();
        return script;
    }
}
