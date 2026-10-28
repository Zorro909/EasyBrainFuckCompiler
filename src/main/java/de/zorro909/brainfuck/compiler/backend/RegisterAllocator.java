package de.zorro909.brainfuck.compiler.backend;

import de.zorro909.brainfuck.compiler.ir.Block;
import de.zorro909.brainfuck.compiler.ir.Function;
import de.zorro909.brainfuck.compiler.ir.Insn;
import de.zorro909.brainfuck.compiler.ir.Operand;
import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import de.zorro909.brainfuck.compiler.ir.Terminator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Maps virtual registers to frame slots with liveness-based graph coloring. Parameters are
 * pinned to slots {@code 0..params-1} because callers write arguments there.
 */
final class RegisterAllocator {

    private RegisterAllocator() {
    }

    record Allocation(int[] slotOf, int slotCount) {
    }

    static Allocation allocate(Function fn, Map<Block, Reg> callResults) {
        int regs = fn.regCount();
        List<Block> blocks = fn.blocks;
        Map<Block, BitSet> liveIn = new HashMap<>();
        Map<Block, BitSet> liveOut = new HashMap<>();
        for (Block b : blocks) {
            liveIn.put(b, new BitSet(regs));
            liveOut.put(b, new BitSet(regs));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                Block b = blocks.get(i);
                BitSet out = new BitSet(regs);
                for (Block succ : b.terminator.successors()) {
                    out.or(liveIn.get(succ));
                }
                BitSet in = (BitSet) out.clone();
                walkBackwards(b, callResults.get(b), in, (d) -> {
                }, null);
                if (!out.equals(liveOut.get(b)) || !in.equals(liveIn.get(b))) {
                    liveOut.put(b, out);
                    liveIn.put(b, in);
                    changed = true;
                }
            }
        }

        List<BitSet> interference = new ArrayList<>(regs);
        for (int r = 0; r < regs; r++) {
            interference.add(new BitSet(regs));
        }
        for (Block b : blocks) {
            BitSet live = (BitSet) liveOut.get(b).clone();
            walkBackwards(b, callResults.get(b), live, null, (def, liveAfter) -> {
                for (int o = liveAfter.nextSetBit(0); o >= 0; o = liveAfter.nextSetBit(o + 1)) {
                    if (o != def) {
                        interference.get(def).set(o);
                        interference.get(o).set(def);
                    }
                }
            });
        }
        // parameters are all defined at entry and must not share slots with each other
        BitSet entryLive = liveIn.get(fn.entry);
        for (int p = 0; p < fn.params; p++) {
            for (int q = 0; q < fn.params; q++) {
                if (p != q) {
                    interference.get(p).set(q);
                }
            }
            for (int o = entryLive.nextSetBit(0); o >= 0; o = entryLive.nextSetBit(o + 1)) {
                if (o != p) {
                    interference.get(p).set(o);
                    interference.get(o).set(p);
                }
            }
        }

        int[] slot = new int[regs];
        java.util.Arrays.fill(slot, -1);
        int count = 0;
        for (int p = 0; p < fn.params; p++) {
            slot[p] = p;
            count = p + 1;
        }
        for (int r = fn.params; r < regs; r++) {
            BitSet taken = new BitSet();
            BitSet adj = interference.get(r);
            for (int o = adj.nextSetBit(0); o >= 0; o = adj.nextSetBit(o + 1)) {
                if (slot[o] >= 0) {
                    taken.set(slot[o]);
                }
            }
            slot[r] = taken.nextClearBit(0);
            count = Math.max(count, slot[r] + 1);
        }
        return new Allocation(slot, count);
    }

    interface DefVisitor {
        void def(int reg, BitSet liveAfter);
    }

    /**
     * Updates {@code live} from block end to block start. Calls {@code onDef} for every
     * definition with the set of registers live right after it.
     */
    private static void walkBackwards(Block b, Reg callResult, BitSet live, Consumer<Integer> unused,
                    DefVisitor onDef) {
        Terminator t = b.terminator;
        forEachUse(t, r -> live.set(r));
        for (int i = b.insns.size() - 1; i >= 0; i--) {
            Insn insn = b.insns.get(i);
            Reg d = def(insn);
            if (d != null) {
                if (onDef != null) {
                    onDef.def(d.id(), live);
                }
                live.clear(d.id());
            }
            forEachUse(insn, r -> live.set(r));
        }
        if (callResult != null) {
            if (onDef != null) {
                onDef.def(callResult.id(), live);
            }
            live.clear(callResult.id());
        }
    }

    static Reg def(Insn insn) {
        return switch (insn) {
            case Insn.Move m -> m.dst();
            case Insn.Unary u -> u.dst();
            case Insn.Binary bi -> bi.dst();
            case Insn.Load l -> l.dst();
            case Insn.GetChar g -> g.dst();
            case Insn.Store st -> null;
            case Insn.PutChar p -> null;
        };
    }

    static void forEachUse(Insn insn, Consumer<Integer> f) {
        switch (insn) {
            case Insn.Move m -> use(m.src(), f);
            case Insn.Unary u -> use(u.src(), f);
            case Insn.Binary bi -> {
                use(bi.a(), f);
                use(bi.b(), f);
            }
            case Insn.Load l -> {
                use(l.ref(), f);
                use(l.index(), f);
            }
            case Insn.Store st -> {
                use(st.ref(), f);
                use(st.index(), f);
                use(st.value(), f);
            }
            case Insn.PutChar p -> use(p.value(), f);
            case Insn.GetChar g -> {
            }
        }
    }

    static void forEachUse(Terminator t, Consumer<Integer> f) {
        switch (t) {
            case Terminator.Branch br -> use(br.cond(), f);
            case Terminator.Call c -> c.args().forEach(a -> use(a, f));
            case Terminator.Return r -> {
                if (r.value() != null) {
                    use(r.value(), f);
                }
            }
            case Terminator.Jump j -> {
            }
            case Terminator.Throw th -> {
            }
            case Terminator.Halt h -> {
            }
        }
    }

    private static void use(Operand o, Consumer<Integer> f) {
        if (o instanceof Reg r) {
            f.accept(r.id());
        }
    }
}
