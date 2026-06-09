package de.zorro909.brainfuck.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.zorro909.brainfuck.core.BrainFuckScript;
import de.zorro909.brainfuck.core.IntegerVariable;
import de.zorro909.brainfuck.core.Variable;
import de.zorro909.brainfuck.core.VariableManager;
import de.zorro909.brainfuck.interpreter.Interpreter;
import de.zorro909.brainfuck.interpreter.InterpreterConfig;
import de.zorro909.brainfuck.transpiler.JavaToBrainfuckTranspiler;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "transpile" -> transpile(args);
            case "run" -> run(args);
            case "demo" -> demo();
            default -> usage();
        }
    }

    private static void usage() {
        System.out.println("""
                        EasyBrainFuckCompiler

                        Usage:
                          transpile <file.java> [-o <out.bf>]   transpile Java to Brainfuck
                          run <file.java|file.bf> [--extensions]  transpile if needed, then interpret
                          demo                                   run the original bit-import demo
                        """);
    }

    private static void transpile(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
            return;
        }
        String code = new JavaToBrainfuckTranspiler()
                        .transpile(Files.readString(Path.of(args[1])));
        if (args.length >= 4 && args[2].equals("-o")) {
            Files.writeString(Path.of(args[3]), code);
            System.out.println("Wrote " + code.length() + " commands to " + args[3]);
        } else {
            System.out.println(code);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
            return;
        }
        String source = Files.readString(Path.of(args[1]));
        String code = args[1].endsWith(".java")
                        ? new JavaToBrainfuckTranspiler().transpile(source)
                        : source;
        boolean extensions = args.length >= 3 && args[2].equals("--extensions");
        var config = InterpreterConfig.defaults().withExtensions(extensions);
        new Interpreter(config, System.in, System.out).interpret(code);
    }

    private static void demo() {
        BrainFuckScript script = demoScript();
        System.out.println("Compiling Finished (Length: " + script.getScript().length() + ")");
        System.out.println(script.getScript());
        new Interpreter().interpret(script.getScript());
        System.out.println();
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
