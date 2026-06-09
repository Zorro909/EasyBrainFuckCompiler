package de.zorro909.brainfuck.interpreter;

/**
 * Runtime configuration for the {@link Interpreter}.
 *
 * @param memorySize        number of 8-bit cells on the tape
 * @param maxSteps          maximum number of executed commands before the run is
 *                          aborted with a {@link BrainfuckExecutionException}; guards
 *                          against non-terminating programs
 * @param wrapPointer       if {@code true} the data pointer wraps around at both tape
 *                          ends, otherwise leaving the tape is an execution error
 * @param extensionsEnabled if {@code true} the non-standard syscall opcode
 *                          {@code '@'} is available; otherwise programs containing it
 *                          are rejected so plain Brainfuck output stays portable
 */
public record InterpreterConfig(int memorySize, long maxSteps, boolean wrapPointer,
                boolean extensionsEnabled) {

    public InterpreterConfig {
        if (memorySize <= 0) {
            throw new IllegalArgumentException("memorySize must be positive: " + memorySize);
        }
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be positive: " + maxSteps);
        }
    }

    public static InterpreterConfig defaults() {
        return new InterpreterConfig(65536, 100_000_000L, true, false);
    }

    public InterpreterConfig withMemorySize(int memorySize) {
        return new InterpreterConfig(memorySize, maxSteps, wrapPointer, extensionsEnabled);
    }

    public InterpreterConfig withMaxSteps(long maxSteps) {
        return new InterpreterConfig(memorySize, maxSteps, wrapPointer, extensionsEnabled);
    }

    public InterpreterConfig withWrapPointer(boolean wrapPointer) {
        return new InterpreterConfig(memorySize, maxSteps, wrapPointer, extensionsEnabled);
    }

    public InterpreterConfig withExtensions(boolean extensionsEnabled) {
        return new InterpreterConfig(memorySize, maxSteps, wrapPointer, extensionsEnabled);
    }
}
