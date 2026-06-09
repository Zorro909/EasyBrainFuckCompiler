package de.zorro909.brainfuck.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import de.zorro909.brainfuck.BfTestSupport;
import de.zorro909.brainfuck.transpiler.JavaToBrainfuckTranspiler;

class SyscallTest {

    private static final InterpreterConfig EXTENSIONS =
                    InterpreterConfig.defaults().withExtensions(true);

    private Interpreter newInterpreter(ByteArrayOutputStream out) {
        return new Interpreter(EXTENSIONS, new ByteArrayInputStream(new byte[0]), out);
    }

    @Test
    void haltSyscallStopsTheProgram() {
        var out = new ByteArrayOutputStream();
        var interpreter = newInterpreter(out);
        // current cell is 0 = halt; the trailing output must never run
        interpreter.interpret("@++++++++[>++++++++<-]>.");
        assertEquals("", out.toString());
    }

    @Test
    void customSyscallReceivesPointerAndMutatesArgumentCell() {
        var out = new ByteArrayOutputStream();
        var interpreter = newInterpreter(out);
        interpreter.registerSyscall(7, (memory, pointer) -> memory[pointer + 1] = 42);
        // cell0 = 7 (syscall id), invoke, print result cell
        interpreter.interpret("+++++++@>.");
        assertEquals(42, out.toByteArray()[0]);
    }

    @Test
    void unknownSyscallIdFails() {
        var interpreter = newInterpreter(new ByteArrayOutputStream());
        assertThrows(BrainfuckExecutionException.class, () -> interpreter.interpret("+++@"));
    }

    @Test
    void haltIdCannotBeOverridden() {
        var interpreter = newInterpreter(new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class,
                        () -> interpreter.registerSyscall(0, (memory, pointer) -> {
                        }));
    }

    @Test
    void randomSyscallFillsTheArgumentCell() {
        // run a few times: cell must not always stay zero
        boolean sawNonZero = false;
        for (int i = 0; i < 32 && !sawNonZero; i++) {
            var interpreter = newInterpreter(new ByteArrayOutputStream());
            interpreter.interpret("+@");
            sawNonZero = interpreter.memorySnapshot()[1] != 0;
        }
        assertNotEquals(false, sawNonZero, "syscall 1 never produced a random byte");
    }

    @Test
    void strictModeRejectsTheSyscallOpcode() {
        var interpreter = new Interpreter(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream());
        assertThrows(BrainfuckSyntaxException.class, () -> interpreter.interpret("+@"));
    }

    @Test
    void transpiledSyscallHaltsTheProgram() {
        var code = new JavaToBrainfuckTranspiler().transpile("""
                        public class Program {
                            public static void main(String[] args) {
                                System.out.print("before");
                                Bf.syscall(0);
                                System.out.print("after");
                            }
                        }
                        """);
        var out = new ByteArrayOutputStream();
        newInterpreter(out).interpret(code);
        assertEquals("before", out.toString());
    }

    @Test
    void transpiledSyscallResultIsUsableAsExpression() {
        var code = new JavaToBrainfuckTranspiler().transpile("""
                        public class Program {
                            public static void main(String[] args) {
                                int r = Bf.syscall(9);
                                System.out.print(r);
                            }
                        }
                        """);
        var out = new ByteArrayOutputStream();
        var interpreter = newInterpreter(out);
        interpreter.registerSyscall(9, (memory, pointer) -> memory[pointer + 1] = 123);
        interpreter.interpret(code);
        assertEquals("123", out.toString());
    }

    @Test
    void pureBrainfuckStillRunsWithExtensionsEnabled() {
        assertEquals("ok", BfTestSupport.run(
                        "+".repeat('o') + "." + "-".repeat('o' - 'k') + ".", "", EXTENSIONS)
                        .output());
    }
}
