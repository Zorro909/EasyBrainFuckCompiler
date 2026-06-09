package de.zorro909.brainfuck.interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes Brainfuck programs on a tape of 8-bit wrapping cells.
 *
 * <p>All characters that are not Brainfuck commands are ignored, so generated code may
 * contain comments. Reading {@code ','} at end of input stores 0. With
 * {@link InterpreterConfig#extensionsEnabled()} the extra opcode {@code '@'} invokes a
 * {@link Syscall} (id = current cell, argument/result cell = pointer + 1); without
 * extensions, programs containing {@code '@'} are rejected.
 */
public class Interpreter {

    private static final String COMMANDS = "><+-.,[]";
    private static final char SYSCALL_OPCODE = '@';

    private final InterpreterConfig config;
    private final InputStream in;
    private final OutputStream out;
    private final Map<Integer, Syscall> syscalls = new HashMap<>();

    private byte[] memory;
    private int pointer;

    public Interpreter() {
        this(InterpreterConfig.defaults(), System.in, System.out);
    }

    public Interpreter(InputStream in, OutputStream out) {
        this(InterpreterConfig.defaults(), in, out);
    }

    public Interpreter(InterpreterConfig config, InputStream in, OutputStream out) {
        this.config = config;
        this.in = in;
        this.out = out;
        this.memory = new byte[config.memorySize()];
        if (config.extensionsEnabled()) {
            syscalls.putAll(Syscalls.defaults(out));
        }
    }

    /** Registers (or replaces) a syscall handler. Id 0 is reserved for halt. */
    public void registerSyscall(int id, Syscall syscall) {
        if (id == Syscalls.HALT) {
            throw new IllegalArgumentException("Syscall id 0 is reserved for halt");
        }
        syscalls.put(id, syscall);
    }

    public void interpret(String source) {
        char[] code = sanitize(source);
        int[] jumpTable = buildJumpTable(code);
        memory = new byte[config.memorySize()];
        pointer = 0;
        long steps = 0;
        try {
            execution:
            for (int pc = 0; pc < code.length; pc++) {
                if (++steps > config.maxSteps()) {
                    throw new BrainfuckExecutionException(
                                    "Program exceeded the maximum of " + config.maxSteps()
                                                    + " steps (possible endless loop)");
                }
                switch (code[pc]) {
                    case '>' -> pointer = movePointer(1);
                    case '<' -> pointer = movePointer(-1);
                    case '+' -> memory[pointer]++;
                    case '-' -> memory[pointer]--;
                    case '.' -> out.write(memory[pointer]);
                    case ',' -> {
                        int read = in.read();
                        memory[pointer] = read < 0 ? 0 : (byte) read;
                    }
                    case '[' -> {
                        if (memory[pointer] == 0) {
                            pc = jumpTable[pc];
                        }
                    }
                    case ']' -> {
                        if (memory[pointer] != 0) {
                            pc = jumpTable[pc];
                        }
                    }
                    case SYSCALL_OPCODE -> {
                        if (invokeSyscall()) {
                            break execution;
                        }
                    }
                    default -> throw new IllegalStateException(
                                    "Unexpected command: " + code[pc]);
                }
            }
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a copy of the tape after the last run. */
    public byte[] memorySnapshot() {
        return memory.clone();
    }

    /** Returns the data pointer position after the last run. */
    public int pointer() {
        return pointer;
    }

    private int movePointer(int delta) {
        int next = pointer + delta;
        if (next >= 0 && next < memory.length) {
            return next;
        }
        if (!config.wrapPointer()) {
            throw new BrainfuckExecutionException("Data pointer left the tape at " + next);
        }
        return Math.floorMod(next, memory.length);
    }

    /** Returns {@code true} if the program should halt. */
    private boolean invokeSyscall() throws IOException {
        int id = memory[pointer] & 0xFF;
        if (id == Syscalls.HALT) {
            return true;
        }
        Syscall syscall = syscalls.get(id);
        if (syscall == null) {
            throw new BrainfuckExecutionException("Unknown syscall id " + id);
        }
        syscall.invoke(memory, pointer);
        return false;
    }

    private char[] sanitize(String source) {
        var commands = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (COMMANDS.indexOf(c) >= 0) {
                commands.append(c);
            } else if (c == SYSCALL_OPCODE) {
                if (!config.extensionsEnabled()) {
                    throw new BrainfuckSyntaxException(
                                    "Syscall opcode '@' requires extensionsEnabled");
                }
                commands.append(c);
            }
        }
        return commands.toString().toCharArray();
    }

    private static int[] buildJumpTable(char[] code) {
        int[] jumpTable = new int[code.length];
        Deque<Integer> openBrackets = new ArrayDeque<>();
        for (int i = 0; i < code.length; i++) {
            if (code[i] == '[') {
                openBrackets.push(i);
            } else if (code[i] == ']') {
                if (openBrackets.isEmpty()) {
                    throw new BrainfuckSyntaxException(
                                    "Unbalanced ']' at command index " + i);
                }
                int open = openBrackets.pop();
                jumpTable[open] = i;
                jumpTable[i] = open;
            }
        }
        if (!openBrackets.isEmpty()) {
            throw new BrainfuckSyntaxException(
                            "Unbalanced '[' at command index " + openBrackets.peek());
        }
        return jumpTable;
    }
}
