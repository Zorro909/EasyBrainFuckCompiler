package de.zorro909.brainfuck.compiler.ir;

import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import java.util.List;

/** Last instruction of a {@link Block}; the only place where control flow happens. */
public sealed interface Terminator {

    List<Block> successors();

    record Jump(Block target) implements Terminator {
        @Override
        public List<Block> successors() {
            return List.of(target);
        }
    }

    /** Jumps to {@code ifTrue} if {@code cond} is non-zero. */
    record Branch(Operand cond, Block ifTrue, Block ifFalse) implements Terminator {
        @Override
        public List<Block> successors() {
            return List.of(ifTrue, ifFalse);
        }
    }

    /**
     * Calls {@code callee}, stores its result into {@code result} (may be null) and continues
     * at {@code next}. If the callee throws, execution continues at {@code handler} instead, or
     * the exception propagates to the caller when {@code handler} is null.
     */
    record Call(Function callee, List<Operand> args, Reg result, Block next, Block handler)
                    implements Terminator {
        public Call(Function callee, List<Operand> args, Reg result, Block next) {
            this(callee, args, result, next, null);
        }

        @Override
        public List<Block> successors() {
            return handler == null ? List.of(next) : List.of(next, handler);
        }
    }

    /** Returns from the function; {@code value} is null for void functions. */
    record Return(Operand value) implements Terminator {
        @Override
        public List<Block> successors() {
            return List.of();
        }
    }

    /**
     * Throws the exception stored in {@code Runtime.pending} out of the function: frames are
     * popped until a caller whose call has a handler, which then resumes there.
     */
    record Throw() implements Terminator {
        @Override
        public List<Block> successors() {
            return List.of();
        }
    }

    /** Stops the whole program. */
    record Halt() implements Terminator {
        @Override
        public List<Block> successors() {
            return List.of();
        }
    }
}
