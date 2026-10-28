package de.zorro909.brainfuck.compiler.backend;

import de.zorro909.brainfuck.compiler.backend.WordOps.Val;
import de.zorro909.brainfuck.compiler.ir.BinOp;
import de.zorro909.brainfuck.compiler.ir.Block;
import de.zorro909.brainfuck.compiler.ir.Builtin;
import de.zorro909.brainfuck.compiler.ir.Function;
import de.zorro909.brainfuck.compiler.ir.Insn;
import de.zorro909.brainfuck.compiler.ir.Operand;
import de.zorro909.brainfuck.compiler.ir.Operand.Imm;
import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import de.zorro909.brainfuck.compiler.ir.Program;
import de.zorro909.brainfuck.compiler.ir.Terminator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates an IR {@link Program} into Brainfuck.
 *
 * <h2>Tape layout</h2>
 * <pre>
 * [ heap: H words of W cells ][ sentinel frame ][ frame 0 = main ][ frame 1 ] ...
 * </pre>
 * The data pointer always sits inside the frame of the running function, and all code
 * addresses cells relative to that frame's base. Calls move the pointer one frame (F cells) to
 * the right, returns move it back, so locals are plain static offsets and recursion is free.
 *
 * <h2>Frame layout</h2>
 * <pre>
 * 0..K-1    carrier: transports values between the frame and the heap (zero when idle)
 * R         1 while the frame is live; the main dispatch loop tests this cell
 * FN        id of the function owning the frame
 * PC, CONT  current block id, and the block to resume after a call/return
 * E1, E2    dispatch flags
 * scratch   temporaries, zero between instructions
 * RV        return value slot (written by the callee)
 * slots     the function's registers, one word each
 * </pre>
 *
 * <h2>Control flow</h2>
 * Every function is a set of basic blocks executed by one big loop:
 * {@code while (R) { PC = CONT; for each function f: if (FN == f) for each block b of f:
 * if (PC == b) { PC = 0; run b } }}. Blocks run in reverse post-order, so forward jumps fall
 * through within the same pass and only back edges, calls and returns cost a new pass.
 *
 * <h2>Heap</h2>
 * Heap references are word distances from the heap top. A load or store copies the distance
 * into the carrier, hops it frame by frame down to the sentinel (cheap, every frame has a zero
 * carrier region at the same offset), and then walks it word by word through the heap as a
 * "bubble": each step swaps the word in front of the carrier behind it, so arbitrary heap
 * contents survive the walk. The way back undoes every swap.
 */
public final class BfBackend {

    public record Options(int wordBytes, int heapWords) {
        public Options {
            if (wordBytes != 2 && wordBytes != 4 && wordBytes != 8) {
                throw new IllegalArgumentException("word size must be 2, 4 or 8 bytes");
            }
            if (heapWords < 16 || heapWords > 65535) {
                throw new IllegalArgumentException("heap size must be 16..65535 words");
            }
        }

        public static Options defaults() {
            return new Options(4, 16384);
        }
    }

    /** Result of a compilation, with layout facts that are useful for diagnostics. */
    public record Output(String code, int frameSize, int functions, int blocks, int tapeBase,
                    List<Marker> markers) {
    }

    /** Start offset of a code region in the output (for profiling). */
    public record Marker(int offset, String label) {
    }

    private final List<Marker> markers = new ArrayList<>();

    private void mark(String label) {
        markers.add(new Marker(e.output().length(), label));
    }

    private final Options opt;
    private final int w;

    // frame layout
    private int fnBytes;
    private int pcBytes;
    private final int carrier = 0;
    /** Compact carrier cells in a frame: ref counter, index counter, value. */
    private static final int C_LO1 = 0;
    private static final int C_HI1 = 1;
    private static final int C_LO2 = 2;
    private static final int C_HI2 = 3;
    private static final int C_VAL = 4;
    private int cSize;
    /** Heap word stride: one lane cell plus the data bytes. */
    private int stride;
    /** Carrier length in lanes while walking the heap (compact cells plus one temp lane). */
    private int laneSpan;
    private int rCell;
    private int fnCell;
    private int pcCell;
    private int contCell;
    /** Block to resume at when the call this frame is suspended in throws; 0 = propagate. */
    private int xpcCell;
    /** Switch flags: function (hi/lo byte) and block (hi/lo byte). */
    private int e1;
    private int e2;
    private int e3;
    private int e4;
    /** Function switch counter: FN is moved here and counted down. */
    private int fnCount;
    private int scratchBase;
    private static final int SCRATCH = 28;
    private int rv;
    private int slotBase;
    private int frameSize;

    private Emitter e;
    private Scratch scratch;
    private WordOps ops;

    private final Map<Function, Integer> fnIds = new IdentityHashMap<>();
    private final Map<Block, Integer> blockIds = new IdentityHashMap<>();
    private final Map<Function, RegisterAllocator.Allocation> allocations = new IdentityHashMap<>();
    private final Map<Block, Terminator.Call> continuationOf = new IdentityHashMap<>();
    private final Map<Builtin, Function> builtins = new HashMap<>();

    public BfBackend(Options options) {
        this.opt = options;
        this.w = options.wordBytes();
    }

    public Output compile(Program program) {
        for (Function f : new ArrayList<>(program.functions)) {
            if (f.builtin == null) {
                lowerBuiltins(f);
            }
        }
        List<Function> functions = reachable(program.main);
        int maxBlocks = 1;
        int maxSlots = 2;
        for (Function f : functions) {
            if (f.builtin != null) {
                continue;
            }
            orderBlocks(f);
            Map<Block, Reg> results = new IdentityHashMap<>();
            for (Block b : f.blocks) {
                if (b.terminator instanceof Terminator.Call c) {
                    if (continuationOf.put(c.next(), c) != null) {
                        throw new IllegalStateException("call continuation shared: " + c.next());
                    }
                    if (c.result() != null) {
                        results.put(c.next(), c.result());
                    }
                }
            }
            var alloc = RegisterAllocator.allocate(f, results);
            allocations.put(f, alloc);
            maxBlocks = Math.max(maxBlocks, f.blocks.size());
            maxSlots = Math.max(maxSlots, alloc.slotCount());
        }
        // ids are reached by counting down, so frequently called functions get small ones
        Map<Function, Integer> callSites = new IdentityHashMap<>();
        for (Function f : functions) {
            if (f.builtin == null) {
                for (Block b : f.blocks) {
                    if (b.terminator instanceof Terminator.Call c) {
                        callSites.merge(c.callee(), 1, Integer::sum);
                    }
                }
            }
        }
        Function start = functions.getFirst();
        List<Function> rest = new ArrayList<>(functions.subList(1, functions.size()));
        rest.sort((x, y) -> Integer.compare(callSites.getOrDefault(y, 0),
                        callSites.getOrDefault(x, 0)));
        functions = new ArrayList<>();
        functions.add(start);
        functions.addAll(rest);
        for (int i = 0; i < functions.size(); i++) {
            fnIds.put(functions.get(i), i + 1);
        }
        fnBytes = functions.size() < 255 ? 1 : 2;
        pcBytes = maxBlocks < 255 ? 1 : 2;
        layout(maxSlots);

        e = new Emitter();
        scratch = new Scratch(scratchBase, SCRATCH);
        ops = new WordOps(e, scratch, w);

        mark("prologue");
        int tapeBase = prologue(program);
        int totalBlocks = 0;
        List<Function> byId = functions;
        e.loop(rCell, () -> {
            mark("dispatch");
            for (int i = 0; i < pcBytes; i++) {
                e.move(contCell + i, pcCell + i);
            }
            for (int i = 0; i < fnBytes; i++) {
                e.move(fnCell + i, fnCount + i);
            }
            countdown(fnCount, fnBytes, byId.size(), e1, e3, id -> {
                setBytes(fnCell, fnBytes, id);
                emitFunction(byId.get(id - 1));
            });
        });
        for (Function f : functions) {
            totalBlocks += f.builtin != null ? 1 : f.blocks.size();
        }
        return new Output(e.output().toString(), frameSize, functions.size(), totalBlocks,
                        tapeBase, List.copyOf(markers));
    }

    // ------------------------------------------------------------------ preparation

    private void layout(int maxSlots) {
        cSize = C_VAL + w;
        stride = w + 1;
        laneSpan = cSize + 1;
        rCell = cSize;
        fnCell = rCell + 1;
        pcCell = fnCell + fnBytes;
        contCell = pcCell + pcBytes;
        xpcCell = contCell + pcBytes;
        e1 = xpcCell + pcBytes;
        e2 = e1 + 1;
        e3 = e2 + 1;
        e4 = e3 + 1;
        fnCount = e4 + 1;
        scratchBase = fnCount + fnBytes;
        rv = scratchBase + SCRATCH;
        slotBase = rv + w;
        frameSize = slotBase + maxSlots * w;
        // the sentinel frame must hold the carrier spread out over lanes (+ trail lane)
        frameSize = Math.max(frameSize, (laneSpan + 1) * stride + 1);
    }

    private List<Function> reachable(Function main) {
        Set<Function> seen = new LinkedHashSet<>();
        List<Function> work = new ArrayList<>(List.of(main));
        while (!work.isEmpty()) {
            Function f = work.removeLast();
            if (!seen.add(f) || f.builtin != null) {
                continue;
            }
            for (Block b : f.blocks) {
                if (b.terminator instanceof Terminator.Call c) {
                    work.add(c.callee());
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** Puts blocks in reverse post-order from the entry and drops unreachable ones. */
    private void orderBlocks(Function f) {
        List<Block> post = new ArrayList<>();
        Set<Block> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        // iterative DFS
        record Frame(Block b, int next) {
        }
        java.util.ArrayDeque<Frame> stack = new java.util.ArrayDeque<>();
        stack.push(new Frame(f.entry, 0));
        seen.add(f.entry);
        while (!stack.isEmpty()) {
            Frame top = stack.pop();
            List<Block> succ = top.b().terminator.successors();
            if (top.next() < succ.size()) {
                stack.push(new Frame(top.b(), top.next() + 1));
                Block s = succ.get(top.next());
                if (seen.add(s)) {
                    stack.push(new Frame(s, 0));
                }
            } else {
                post.add(top.b());
            }
        }
        f.blocks.clear();
        for (int i = post.size() - 1; i >= 0; i--) {
            f.blocks.add(post.get(i));
        }
        for (int i = 0; i < f.blocks.size(); i++) {
            blockIds.put(f.blocks.get(i), i + 1);
        }
    }

    /** Whether a binary operation is emitted inline instead of calling a bit-level builtin. */
    static boolean isInline(Insn.Binary bi, int wordBytes) {
        BinOp op = bi.op();
        if (Builtin.of(op) == null) {
            return true;
        }
        if (bi.a() instanceof Imm && bi.b() instanceof Imm) {
            return true;
        }
        if (!(bi.b() instanceof Imm(long v))) {
            return op.isCommutative() && bi.a() instanceof Imm(long av) && isInlineConst(op, av,
                            wordBytes);
        }
        return isInlineConst(op, v, wordBytes);
    }

    private static boolean isInlineConst(BinOp op, long v, int wordBytes) {
        int bits = 8 * wordBytes;
        return switch (op) {
            case MUL -> v == 0 || v == 1 || v == -1 || v == 2;
            case DIV -> v == 1;
            case REM -> v == 1 || v == -1;
            case AND -> {
                for (int i = 0; i < wordBytes; i++) {
                    int b = WordOps.byteOf(v, i);
                    if (b != 0 && b != 0xFF) {
                        yield false;
                    }
                }
                yield true;
            }
            case OR, XOR -> v == 0;
            case SHL, SHR, USHR -> Math.floorMod(v, bits) % 8 == 0;
            default -> true;
        };
    }

    /** Splits blocks at every non-inline MUL/DIV/... and turns the operation into a call. */
    private void lowerBuiltins(Function f) {
        for (int bi = 0; bi < f.blocks.size(); bi++) {
            Block b = f.blocks.get(bi);
            for (int i = 0; i < b.insns.size(); i++) {
                if (b.insns.get(i) instanceof Insn.Binary bin && !isInline(bin, w)) {
                    Builtin kind = Builtin.of(bin.op());
                    Function callee = builtins.computeIfAbsent(kind, Function::builtin);
                    Block rest = f.newBlock(b.label + "'");
                    rest.insns.addAll(b.insns.subList(i + 1, b.insns.size()));
                    rest.terminator = b.terminator;
                    b.insns.subList(i, b.insns.size()).clear();
                    b.terminator = new Terminator.Call(callee, List.of(bin.a(), bin.b()), bin.dst(),
                                    rest);
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ prologue

    /** Writes the static heap image and enters main's frame; returns the absolute frame base. */
    private int prologue(Program program) {
        int heap = opt.heapWords();
        long[] image = program.staticImage;
        if (image.length >= heap) {
            throw new IllegalStateException("static data (" + image.length
                            + " words) does not fit into the heap (" + heap + " words)");
        }
        int lowest = heap - image.length;
        fastMoveRight(lowest * stride);
        for (int d = image.length - 1; d >= 0; d--) {
            int cell = (heap - 1 - d) * stride + 1;
            if (image[d] != 0) {
                for (int i = 0; i < w; i++) {
                    e.add(cell + i, WordOps.byteOf(image[d], i));
                }
            }
        }
        int frame0 = heap * stride + frameSize;
        e.moveTo(frame0);
        e.rebase(frame0);
        e.add(rCell, 1);
        setBytes(fnCell, fnBytes, fnIds.get(program.main));
        setBytes(contCell, pcBytes, 1);
        return frame0;
    }

    /** Moves the pointer right over zero cells using a compact counting loop. */
    private void fastMoveRight(int cells) {
        int target = e.position() + cells;
        final int stride = 255;
        String hop = ">".repeat(stride);
        String back = "<".repeat(stride);
        while (target - e.position() >= 2 * stride) {
            int k = Math.min(255, (target - e.position()) / stride);
            int at = e.position();
            // [-[->>>..+<<<..]>>>..]: carries the counter k cells*stride to the right
            e.add(at, k);
            e.raw("[-[-" + hop + "+" + back + "]" + hop + "]");
            e.assumePosition(at + k * stride);
        }
        e.moveTo(target);
    }

    private void setBytes(int cell, int bytes, long value) {
        for (int i = 0; i < bytes; i++) {
            e.add(cell + i, WordOps.byteOf(value, i));
        }
    }

    // ------------------------------------------------------------------ dispatch

    /**
     * Runs one block of {@code f}: the block switch counts PC down to zero, which is also the
     * state a running block expects. Terminators write the next block into CONT.
     */
    private void emitFunction(Function f) {
        if (f.builtin != null) {
            mark(f.name);
            e.add(pcCell, -1);
            emitBuiltin(f.builtin);
            mark("dispatch");
            return;
        }
        var alloc = allocations.get(f);
        useCounts = countUses(f);
        countdown(pcCell, pcBytes, f.blocks.size(), e2, e4, bid -> {
            Block b = f.blocks.get(bid - 1);
            mark(f.name + "/" + b.label);
            emitBlock(f, alloc, b);
            mark("dispatch");
            if (!scratch.allFree()) {
                throw new IllegalStateException("scratch leak in " + f + "/" + b);
            }
        });
    }

    /**
     * {@code switch (value) { case 1..max: body }} for a value of {@code bytes} cells, by counting
     * the cells down to zero in nested loops: reaching case k costs k decrements, no copies.
     * Each loop runs at most once, and its closing test sees a zero counter even if the case
     * body moved to another frame (counters and flags are zero in inactive frames).
     */
    private void countdown(int cell, int bytes, int max, int flag, int flagLo,
                    java.util.function.IntConsumer body) {
        if (bytes == 1) {
            countdownByte(cell, max, flag, body, false);
            return;
        }
        int maxHi = max >>> 8;
        countdownByte(cell + 1, maxHi, flag, hi -> {
            int loMax = hi == maxHi ? max & 255 : 255;
            countdownByte(cell, loMax, flagLo, lo -> {
                int id = hi * 256 + lo;
                if (id >= 1) {
                    body.accept(id);
                }
            }, true);
        }, true);
    }

    private void countdownByte(int cell, int max, int flag, java.util.function.IntConsumer body,
                    boolean withZero) {
        e.add(flag, 1);
        if (max >= 1) {
            countdownLevel(cell, 1, max, flag, body);
        }
        if (withZero) {
            e.ifFlag(flag, () -> body.accept(0));
        } else {
            e.clear(flag);
        }
    }

    private void countdownLevel(int cell, int k, int max, int flag,
                    java.util.function.IntConsumer body) {
        e.loop(cell, () -> {
            e.add(cell, -1);
            if (k < max) {
                countdownLevel(cell, k + 1, max, flag, body);
            }
            e.ifFlag(flag, () -> body.accept(k));
        });
    }

    /** {@code flag += (header field == value)}. */
    private void headerEquals(int cell, int bytes, int value, int flag) {
        int ne = scratch.alloc();
        int t = scratch.alloc();
        for (int i = 0; i < bytes; i++) {
            int d = scratch.alloc();
            e.copyAdd(cell + i, d, t);
            e.add(d, -WordOps.byteOf(value, i));
            e.ifNonZeroDestructive(d, () -> e.add(ne, 1));
            scratch.free(d);
        }
        scratch.free(t);
        e.add(flag, 1);
        if (bytes == 1) {
            e.ifFlag(ne, () -> e.add(flag, -1));
        } else {
            e.ifNonZeroDestructive(ne, () -> e.add(flag, -1));
        }
        scratch.free(ne);
    }

    // ------------------------------------------------------------------ blocks

    private int[] slotOf;
    private Map<Integer, Integer> useCounts;

    private int reg(Reg r) {
        return slotBase + slotOf[r.id()] * w;
    }

    private Val val(Operand o) {
        return switch (o) {
            case Imm(long v) -> Val.imm(v);
            case Reg r -> Val.reg(reg(r));
        };
    }

    private void emitBlock(Function f, RegisterAllocator.Allocation alloc, Block b) {
        slotOf = alloc.slotOf();
        Terminator.Call call = continuationOf.get(b);
        if (call != null && call.callee().returnsValue) {
            if (call.result() != null) {
                int dst = reg(call.result());
                ops.clearWord(dst);
                ops.moveWord(rv, dst);
            } else {
                ops.clearWord(rv);
            }
        }
        List<Insn> insns = b.insns;
        int fuseCompare = -1;
        if (b.terminator instanceof Terminator.Branch br && br.cond() instanceof Reg c
                        && !insns.isEmpty() && insns.getLast() instanceof Insn.Binary last
                        && last.op().isComparison() && last.dst().equals(c)
                        && useCounts.getOrDefault(c.id(), 0) == 1) {
            fuseCompare = insns.size() - 1;
        }
        for (int i = 0; i < insns.size(); i++) {
            if (i == fuseCompare) {
                break;
            }
            emitInsn(insns.get(i));
        }
        if (fuseCompare >= 0) {
            Insn.Binary cmp = (Insn.Binary) insns.getLast();
            Terminator.Branch br = (Terminator.Branch) b.terminator;
            int flag = scratch.alloc();
            compareFlag(cmp.op(), val(cmp.a()), val(cmp.b()), flag);
            branchOnFlag(flag, br.ifTrue(), br.ifFalse());
            scratch.free(flag);
        } else {
            emitTerminator(f, alloc, b.terminator);
        }
    }

    private static Map<Integer, Integer> countUses(Function f) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Block b : f.blocks) {
            for (Insn insn : b.insns) {
                RegisterAllocator.forEachUse(insn, r -> counts.merge(r, 1, Integer::sum));
            }
            RegisterAllocator.forEachUse(b.terminator, r -> counts.merge(r, 1, Integer::sum));
        }
        return counts;
    }

    private void emitInsn(Insn insn) {
        switch (insn) {
            case Insn.Move m -> ops.assign(reg(m.dst()), val(m.src()));
            case Insn.Unary u -> emitUnary(u);
            case Insn.Binary bi -> emitBinary(bi);
            case Insn.Load l -> heapAccess(l.ref(), l.index(), l.offset(), null, l.dst());
            case Insn.Store s -> heapAccess(s.ref(), s.index(), s.offset(), s.value(), null);
            case Insn.PutChar p -> {
                if (p.value() instanceof Imm(long v)) {
                    int c = scratch.alloc();
                    e.add(c, (int) v);
                    e.output(c);
                    e.clear(c);
                    scratch.free(c);
                } else {
                    e.output(reg((Reg) p.value()));
                }
            }
            case Insn.GetChar g -> {
                int dst = reg(g.dst());
                ops.clearWord(dst);
                e.input(dst);
                int z = scratch.alloc();
                ops.addIsZero(dst, z);
                e.ifFlag(z, () -> {
                    for (int i = 0; i < w; i++) {
                        e.add(dst + i, 255);
                    }
                });
                scratch.free(z);
            }
        }
    }

    private long fold(long v) {
        int bits = 8 * w;
        return bits == 64 ? v : (v << (64 - bits)) >> (64 - bits);
    }

    private void emitUnary(Insn.Unary u) {
        int dst = reg(u.dst());
        if (u.src() instanceof Imm(long v)) {
            long r = switch (u.op()) {
                case NEG -> -v;
                case NOT -> ~v;
                case LNOT -> fold(v) == 0 ? 1 : 0;
                case I2B -> (byte) v;
                case I2C -> (char) v;
                case I2S -> (short) v;
            };
            ops.assign(dst, Val.imm(r));
            return;
        }
        Val src = val(u.src());
        switch (u.op()) {
            case NEG -> {
                ops.assign(dst, src);
                ops.negate(dst);
            }
            case NOT -> {
                ops.assign(dst, src);
                ops.not(dst);
            }
            case LNOT -> {
                int f = scratch.alloc();
                ops.addEqual(src, Val.imm(0), f);
                ops.clearWord(dst);
                e.move(f, dst);
                scratch.free(f);
            }
            case I2C -> {
                ops.assign(dst, src);
                for (int i = 2; i < w; i++) {
                    e.clear(dst + i);
                }
            }
            case I2B -> {
                ops.assign(dst, src);
                ops.signExtendFrom(dst, 0);
            }
            case I2S -> {
                ops.assign(dst, src);
                if (w > 2) {
                    ops.signExtendFrom(dst, 1);
                }
            }
        }
    }

    private long evalBinary(BinOp op, long a, long b) {
        int bits = 8 * w;
        a = fold(a);
        b = fold(b);
        return switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> b == 0 ? 0 : a / b;
            case REM -> b == 0 ? 0 : a % b;
            case AND -> a & b;
            case OR -> a | b;
            case XOR -> a ^ b;
            case SHL -> a << Math.floorMod(b, bits);
            case SHR -> a >> Math.floorMod(b, bits);
            case USHR -> {
                long mask = bits == 64 ? -1L : (1L << bits) - 1;
                yield (a & mask) >>> Math.floorMod(b, bits);
            }
            case EQ -> a == b ? 1 : 0;
            case NE -> a != b ? 1 : 0;
            case LT -> a < b ? 1 : 0;
            case LE -> a <= b ? 1 : 0;
            case GT -> a > b ? 1 : 0;
            case GE -> a >= b ? 1 : 0;
        };
    }

    private void emitBinary(Insn.Binary bi) {
        int dst = reg(bi.dst());
        BinOp op = bi.op();
        Operand a = bi.a();
        Operand b = bi.b();
        if (a instanceof Imm(long av) && b instanceof Imm(long bv)) {
            ops.assign(dst, Val.imm(evalBinary(op, av, bv)));
            return;
        }
        if (op.isComparison()) {
            int f = scratch.alloc();
            compareFlag(op, val(a), val(b), f);
            ops.clearWord(dst);
            e.move(f, dst);
            scratch.free(f);
            return;
        }
        if (op.isCommutative() && a instanceof Imm) {
            Operand t = a;
            a = b;
            b = t;
        }
        switch (op) {
            case ADD -> {
                if (sameSlot(a, dst)) {
                    addInPlace(dst, b);
                } else if (sameSlot(b, dst)) {
                    addInPlace(dst, a);
                } else if (a.equals(b)) {
                    ops.assign(dst, val(a));
                    doubleInPlace(dst);
                } else {
                    ops.assign(dst, val(a));
                    ops.add(dst, val(b));
                }
            }
            case SUB -> {
                if (sameSlot(a, dst)) {
                    if (sameSlot(b, dst)) {
                        ops.clearWord(dst);
                    } else {
                        ops.sub(dst, val(b));
                    }
                } else if (sameSlot(b, dst)) {
                    int t = scratch.allocRun(w);
                    ops.load(t, val(a));
                    ops.sub(t, val(b));
                    ops.clearWord(dst);
                    ops.moveWord(t, dst);
                    scratch.freeRun(t, w);
                } else {
                    ops.assign(dst, val(a));
                    ops.sub(dst, val(b));
                }
            }
            case MUL -> {
                long c = ((Imm) b).value();
                if (c == 0) {
                    ops.assign(dst, Val.imm(0));
                } else if (c == 1) {
                    ops.assign(dst, val(a));
                } else if (c == -1) {
                    ops.assign(dst, val(a));
                    ops.negate(dst);
                } else {
                    ops.assign(dst, val(a));
                    doubleInPlace(dst);
                }
            }
            case DIV -> ops.assign(dst, val(a));
            case REM -> ops.assign(dst, Val.imm(0));
            case AND -> {
                long mask = ((Imm) b).value();
                ops.assign(dst, val(a));
                for (int i = 0; i < w; i++) {
                    if (WordOps.byteOf(mask, i) == 0) {
                        e.clear(dst + i);
                    }
                }
            }
            case OR, XOR -> ops.assign(dst, val(a));
            case SHL, SHR, USHR -> byteShift(op, dst, val(a),
                            Math.floorMod(((Imm) b).value(), 8 * w) / 8);
            default -> throw new IllegalStateException("not inline: " + bi);
        }
    }

    private boolean sameSlot(Operand o, int cell) {
        return o instanceof Reg r && reg(r) == cell;
    }

    /** {@code dst += src} handling {@code src} aliasing {@code dst}. */
    private void addInPlace(int dst, Operand src) {
        if (sameSlot(src, dst)) {
            doubleInPlace(dst);
        } else {
            ops.add(dst, val(src));
        }
    }

    private void doubleInPlace(int dst) {
        int t = scratch.allocRun(w);
        ops.copyWord(dst, t);
        ops.add(dst, Val.reg(t));
        ops.clearWord(t);
        scratch.freeRun(t, w);
    }

    private void byteShift(BinOp op, int dst, Val a, int bytes) {
        int t = scratch.allocRun(w);
        for (int i = 0; i < w; i++) {
            int from = op == BinOp.SHL ? i - bytes : i + bytes;
            if (from >= 0 && from < w) {
                ops.loadByte(t + i, a, from, 1);
            }
        }
        if (op == BinOp.SHR && bytes > 0) {
            ops.signExtendFrom(t, w - 1 - bytes);
        }
        ops.clearWord(dst);
        ops.moveWord(t, dst);
        scratch.freeRun(t, w);
    }

    /** {@code flag += (a op b)} for a comparison. */
    private void compareFlag(BinOp op, Val a, Val b, int flag) {
        switch (op) {
            case EQ -> ops.addEqual(a, b, flag);
            case NE -> ops.addNotEqual(a, b, flag);
            case LT -> ops.compare(a, b, flag, -1);
            case GT -> ops.compare(a, b, -1, flag);
            case LE, GE -> {
                int g = scratch.alloc();
                if (op == BinOp.LE) {
                    ops.compare(a, b, -1, g);
                } else {
                    ops.compare(a, b, g, -1);
                }
                e.add(flag, 1);
                e.ifFlag(g, () -> e.add(flag, -1));
                scratch.free(g);
            }
            default -> throw new IllegalArgumentException(op.toString());
        }
    }

    // ------------------------------------------------------------------ terminators

    private void emitTerminator(Function f, RegisterAllocator.Allocation alloc, Terminator t) {
        switch (t) {
            case Terminator.Jump j -> setBytes(contCell, pcBytes, blockIds.get(j.target()));
            case Terminator.Branch br -> {
                if (br.cond() instanceof Imm(long v)) {
                    setBytes(contCell, pcBytes, blockIds.get(fold(v) != 0 ? br.ifTrue()
                                    : br.ifFalse()));
                    return;
                }
                int flag = scratch.alloc();
                ops.addIsNonZeroWord(val(br.cond()), flag);
                branchOnFlag(flag, br.ifTrue(), br.ifFalse());
                scratch.free(flag);
            }
            case Terminator.Call c -> {
                int calleeBase = frameSize;
                for (int i = 0; i < c.args().size(); i++) {
                    ops.load(calleeBase + slotBase + i * w, val(c.args().get(i)));
                }
                e.add(calleeBase + rCell, 1);
                setBytes(calleeBase + fnCell, fnBytes, fnIds.get(c.callee()));
                setBytes(calleeBase + contCell, pcBytes, 1);
                setBytes(contCell, pcBytes, blockIds.get(c.next()));
                // builtins never throw, and their epilogue does not clear XPC
                if (c.handler() != null && c.callee().builtin == null) {
                    setBytes(xpcCell, pcBytes, blockIds.get(c.handler()));
                }
                e.rebase(frameSize);
            }
            case Terminator.Return r -> {
                boolean isMain = fnIds.get(f) == 1;
                if (r.value() != null && !isMain) {
                    ops.load(rv - frameSize, val(r.value()));
                }
                if (!isMain) {
                    for (int i = 0; i < pcBytes; i++) {
                        e.clear(xpcCell + i - frameSize);
                    }
                }
                epilogue(alloc.slotCount(), isMain);
            }
            case Terminator.Throw th -> unwind(alloc.slotCount());
            case Terminator.Halt h -> e.clear(rCell);
        }
    }

    private void branchOnFlag(int flag, Block ifTrue, Block ifFalse) {
        int t = blockIds.get(ifTrue);
        int fl = blockIds.get(ifFalse);
        setBytes(contCell, pcBytes, fl);
        e.ifFlag(flag, () -> {
            for (int i = 0; i < pcBytes; i++) {
                e.add(contCell + i, WordOps.byteOf(t, i) - WordOps.byteOf(fl, i));
            }
        });
    }

    /** Clears the frame and pops it; for main it just stops the program. */
    private void epilogue(int slots, boolean isMain) {
        for (int i = 0; i < slots * w; i++) {
            e.clear(slotBase + i);
        }
        e.clear(rCell);
        if (!isMain) {
            for (int i = 0; i < fnBytes; i++) {
                e.clear(fnCell + i);
            }
            e.rebase(-frameSize);
        }
    }

    /**
     * Pops the current frame, then every caller frame whose pending call has no handler
     * ({@code XPC == 0}), and resumes the first frame that has one at its handler block.
     * {@code $start} always installs a handler, so the loop stops at frame 0 at the latest.
     */
    private void unwind(int slots) {
        epilogue(slots, false);
        int z = scratch.alloc();
        headerEquals(xpcCell, pcBytes, 0, z);
        e.movingLoop(z, -frameSize, () -> {
            e.add(z, -1);
            // the frame is suspended in a call: PC and XPC are already zero
            for (int c = slotBase; c < frameSize; c++) {
                e.clear(c);
            }
            e.clear(rCell);
            for (int i = 0; i < fnBytes; i++) {
                e.clear(fnCell + i);
            }
            for (int i = 0; i < pcBytes; i++) {
                e.clear(contCell + i);
            }
            headerEquals(xpcCell - frameSize, pcBytes, 0, z - frameSize);
        });
        scratch.free(z);
        for (int i = 0; i < pcBytes; i++) {
            e.clear(contCell + i);
            e.move(xpcCell + i, contCell + i);
        }
    }

    // ------------------------------------------------------------------ heap access

    /** Offset of carrier lane {@code j} relative to the carrier while it walks the heap. */
    private int lane(int j) {
        return j * stride;
    }

    /**
     * Loads {@code heap[ref + index + offset]} into {@code dst}, or stores {@code value} there
     * when {@code dst} is null.
     *
     * <p>Heap words are {@code [lane][W data bytes]}. The carrier keeps its cells in the lanes of
     * consecutive words, so walking only moves the (few) carrier cells and never the data. The
     * walk counts down two 16-bit counters (reference and index) with nested byte loops whose
     * loop cell is the counter itself, and leaves a breadcrumb in a lane behind every step; the
     * way back simply follows the breadcrumbs.
     */
    private void heapAccess(Operand ref, Operand index, int offset, Operand value, Reg dst) {
        boolean store = dst == null;
        // steps from the sentinel to the word: distance + 1; split into counters and a
        // small constant part that is walked with unrolled steps
        int fixed = offset + 1;
        boolean refCounter = true;
        boolean indexCounter = true;
        if (ref instanceof Imm(long r)) {
            if (r + fixed <= 12) {
                fixed += (int) r;
                refCounter = false;
            } else {
                setBytes(C_LO1, 2, r);
            }
        } else {
            ops.copyBytes(reg((Reg) ref), C_LO1, 2);
        }
        if (index instanceof Imm(long ix)) {
            if (ix + fixed <= 12 || !refCounter && ix <= 12) {
                fixed += (int) ix;
                indexCounter = false;
            } else if (!refCounter) {
                setBytes(C_LO1, 2, ix);
                refCounter = true;
                indexCounter = false;
            } else {
                setBytes(C_LO2, 2, ix);
            }
        } else if (!refCounter) {
            ops.copyBytes(reg((Reg) index), C_LO1, 2);
            refCounter = true;
            indexCounter = false;
        } else {
            ops.copyBytes(reg((Reg) index), C_LO2, 2);
        }
        if (store) {
            ops.load(C_VAL, val(value));
        }
        List<Integer> out = new ArrayList<>();
        if (refCounter) {
            out.add(C_LO1);
            out.add(C_HI1);
        }
        if (indexCounter) {
            out.add(C_LO2);
            out.add(C_HI2);
        }
        if (store) {
            for (int i = 0; i < w; i++) {
                out.add(C_VAL + i);
            }
        }

        // 1. hop down to the sentinel frame (the only frame with R == 0)
        e.loop(rCell, () -> {
            for (int c : out) {
                e.move(c, c - frameSize);
            }
            e.rebase(-frameSize);
        });
        // 2. spread the carrier over the lanes of the sentinel region (descending: no overlap)
        for (int i = out.size() - 1; i >= 0; i--) {
            int c = out.get(i);
            if (c != 0) {
                e.move(c, lane(c));
            }
        }
        // 3. walk: index counter, constant part, reference counter
        List<Integer> movers = new ArrayList<>(out);
        if (indexCounter) {
            walkCounter(C_LO2, C_HI2, movers);
            movers.remove(Integer.valueOf(C_LO2));
            movers.remove(Integer.valueOf(C_HI2));
        }
        for (int k = 0; k < fixed; k++) {
            stepLeft(movers, -1);
        }
        if (refCounter) {
            walkCounter(C_LO1, C_HI1, movers);
        }
        // 4. access the word at the carrier: its data follows lane 0
        int tmp = lane(laneSpan - 1);
        for (int j = 0; j < w; j++) {
            if (store) {
                e.clear(1 + j);
                e.move(lane(C_VAL + j), 1 + j);
            } else {
                e.copyAdd(1 + j, lane(C_VAL + j), tmp);
            }
        }
        // 5. follow the breadcrumbs back to the sentinel
        List<Integer> back = new ArrayList<>();
        if (!store) {
            for (int i = w - 1; i >= 0; i--) {
                back.add(C_VAL + i);
            }
        }
        int trail = lane(laneSpan);
        e.movingLoop(trail, stride, () -> {
            e.add(trail, -1);
            for (int c : back) {
                e.move(lane(c), lane(c + 1));
            }
        });
        for (int i = back.size() - 1; i >= 0; i--) {
            int c = back.get(i);
            e.move(lane(c), c);
        }
        // 6. hop up to the topmost live frame
        e.movingLoop(rCell + frameSize, frameSize, () -> {
            for (int c : back) {
                e.move(c, c + frameSize);
            }
        });
        if (!store) {
            int d = reg(dst);
            ops.clearWord(d);
            ops.moveWord(C_VAL, d);
        }
    }

    /**
     * Walks {@code hi * 256 + lo} words to the left. {@code lo} itself is the loop cell, so a
     * step costs a decrement and the carrier moves; {@code hi} runs an outer loop of 256 steps
     * (255 counted by {@code lo} plus one explicit step).
     */
    private void walkCounter(int lo, int hi, List<Integer> movers) {
        countedSteps(lo, movers);
        e.loop(lane(hi), () -> {
            e.add(lane(hi), -1);
            e.add(lane(lo), 255);
            stepLeft(movers, -1);
            countedSteps(lo, movers);
        });
    }

    private void countedSteps(int lo, List<Integer> movers) {
        e.moveTo(lane(lo));
        e.raw("[");
        stepLeft(movers, lo);
        e.moveTo(lane(lo));
        e.raw("]");
    }

    /**
     * Moves the carrier one heap word to the left and drops a breadcrumb into the lane that
     * becomes free behind it. {@code dec} (if >= 0) is decremented first.
     */
    private void stepLeft(List<Integer> movers, int dec) {
        if (dec >= 0) {
            e.add(lane(dec), -1);
        }
        List<Integer> sorted = new ArrayList<>(movers);
        java.util.Collections.sort(sorted);
        for (int c : sorted) {
            e.move(lane(c), lane(c) - stride);
        }
        e.add(lane(laneSpan - 1), 1);
        e.rebase(-stride);
    }

    // ------------------------------------------------------------------ builtins

    private void emitBuiltin(Builtin b) {
        int a0 = slotBase;
        int a1 = slotBase + w;
        int result = rv - frameSize;
        int work = frameSize;
        switch (b) {
            case MUL -> {
                final int A = 0, B = 1, P = 2, S = 3, T = 4, C = 5;
                BitOps bo = new BitOps(e, w, work, 6);
                bo.unpack(new int[] {a0, a1}, new int[] {A, B});
                bo.addFieldNonZero(B, bo.flagA, T, bo.flagB);
                e.loop(bo.flagA, () -> {
                    e.add(bo.flagA, -1);
                    e.move(bo.bit(B, 0), bo.flagC);
                    e.ifFlag(bo.flagC, () -> bo.addField(P, A, false, false, S, T, C, -1));
                    bo.shiftUp(A);
                    bo.shiftDown(B, false, T);
                    bo.addFieldNonZero(B, bo.flagA, T, bo.flagB);
                });
                bo.clearField(A);
                bo.pack(P, result);
                epilogue(2, false);
            }
            case DIV, REM -> emitDivRem(b == Builtin.DIV, a0, a1, result, work);
            case AND, OR, XOR -> {
                final int A = 0, B = 1, R = 2;
                BitOps bo = new BitOps(e, w, work, 3);
                bo.unpack(new int[] {a0, a1}, new int[] {A, B});
                for (int k = 0; k < bo.n; k++) {
                    int x = bo.bit(A, k);
                    int y = bo.bit(B, k);
                    int r = bo.bit(R, k);
                    switch (b) {
                        case AND -> {
                            e.loop(x, () -> {
                                e.add(x, -1);
                                e.loop(y, () -> {
                                    e.add(y, -1);
                                    e.add(r, 1);
                                });
                            });
                            e.clear(y);
                        }
                        case OR -> {
                            e.move(y, x);
                            e.ifNonZeroDestructive(x, () -> e.add(r, 1));
                        }
                        default -> {
                            e.move(y, x);
                            e.loop(x, () -> {
                                e.add(x, -1);
                                e.add(r, 1);
                                e.loop(x, () -> {
                                    e.add(x, -1);
                                    e.add(r, -1);
                                });
                            });
                        }
                    }
                }
                bo.pack(R, result);
                epilogue(2, false);
            }
            case SHL, SHR, USHR -> {
                final int V = 0, N = 1, T = 2;
                BitOps bo = new BitOps(e, w, work, 3);
                bo.unpack(new int[] {a0, a1}, new int[] {V, N});
                int logBits = Integer.numberOfTrailingZeros(bo.n);
                for (int k = 0; k < logBits; k++) {
                    e.moveAdd(bo.bit(N, k), new int[] {bo.counter}, new int[] {1 << k});
                }
                bo.clearField(N);
                e.loop(bo.counter, () -> {
                    e.add(bo.counter, -1);
                    if (b == Builtin.SHL) {
                        bo.shiftUp(V);
                    } else {
                        bo.shiftDown(V, b == Builtin.SHR, T);
                    }
                });
                bo.pack(V, result);
                epilogue(2, false);
            }
            case PRINT_INT -> emitPrintInt(a0, work);
        }
    }

    private void emitDivRem(boolean div, int a0, int a1, int result, int work) {
        final int Q = 0, D = 1, R = 2, T = 3, S = 4, C = 5, X = 6;
        BitOps bo = new BitOps(e, w, work, 7);
        bo.unpack(new int[] {a0, a1}, new int[] {Q, D});
        int nonZero = scratch.alloc();
        int isZero = scratch.alloc();
        int signA = scratch.alloc();
        int signB = scratch.alloc();
        bo.addFieldNonZero(D, nonZero, X, bo.flagA);
        e.add(isZero, 1);
        int t = scratch.alloc();
        int t2 = scratch.alloc();
        e.copyAdd(nonZero, t, t2);
        e.ifNonZeroDestructive(t, () -> e.add(isZero, -1));
        e.ifFlag(isZero, () -> {
            printConst("Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n", t);
            e.clear(rCell);
        });
        e.ifFlag(nonZero, () -> {
            int top = bo.n - 1;
            e.copyAdd(bo.bit(Q, top), signA, bo.bit(X, top));
            e.copyAdd(bo.bit(D, top), signB, bo.bit(X, top));
            e.copyAdd(signA, t, t2);
            e.ifFlag(t, () -> bo.negateField(Q, S, C));
            e.copyAdd(signB, t, t2);
            e.ifFlag(t, () -> bo.negateField(D, S, C));
            e.add(bo.counter, bo.n);
            e.loop(bo.counter, () -> {
                e.add(bo.counter, -1);
                bo.shiftUp(R);
                e.move(bo.bit(Q, top), bo.bit(R, 0));
                bo.shiftUp(Q);
                for (int k = 0; k < bo.n; k++) {
                    e.copyAdd(bo.bit(R, k), bo.bit(T, k), bo.bit(S, k));
                }
                bo.addField(T, D, true, true, S, X, C, bo.flagB);
                e.ifFlag(bo.flagB, () -> {
                    for (int k = 0; k < bo.n; k++) {
                        e.clear(bo.bit(R, k));
                        e.move(bo.bit(T, k), bo.bit(R, k));
                    }
                    e.add(bo.bit(Q, 0), 1);
                });
                bo.clearField(T);
            });
            // quotient negative iff signs differ, remainder takes the dividend's sign
            if (div) {
                e.copyAdd(signA, t, t2);
                e.move(signB, t);
                e.loop(t, () -> {
                    e.add(t, -1);
                    e.add(t2, 1);
                    e.loop(t, () -> {
                        e.add(t, -1);
                        e.add(t2, -1);
                    });
                });
                e.ifFlag(t2, () -> bo.negateField(Q, S, C));
                bo.pack(Q, result);
                bo.clearField(R);
            } else {
                e.clear(signB);
                e.move(signA, t);
                e.ifFlag(t, () -> bo.negateField(R, S, C));
                bo.pack(R, result);
                bo.clearField(Q);
            }
            e.clear(signA);
            e.clear(signB);
            bo.clearField(D);
            epilogue(2, false);
        });
        scratch.free(t2);
        scratch.free(t);
        scratch.free(signB);
        scratch.free(signA);
        scratch.free(isZero);
        scratch.free(nonZero);
    }

    private void emitPrintInt(int a0, int work) {
        final int V = 0, S = 1, C = 2;
        BitOps bo = new BitOps(e, w, work, 3);
        int digits = switch (w) {
            case 2 -> 5;
            case 4 -> 10;
            default -> 20;
        };
        int dig = bo.extent();
        // digit j: [D, t, carry]
        bo.unpack(new int[] {a0}, new int[] {V});
        int neg = scratch.alloc();
        int t = scratch.alloc();
        int t2 = scratch.alloc();
        int top = bo.n - 1;
        e.copyAdd(bo.bit(V, top), neg, bo.bit(S, top));
        e.copyAdd(neg, t, t2);
        e.ifFlag(t, () -> bo.negateField(V, S, C));
        e.add(bo.counter, bo.n);
        e.loop(bo.counter, () -> {
            e.add(bo.counter, -1);
            e.move(bo.bit(V, top), dig + 2);
            bo.shiftUp(V);
            for (int j = 0; j < digits; j++) {
                int d = dig + 3 * j;
                int tj = d + 1;
                int cin = d + 2;
                int cout = j + 1 < digits ? d + 5 : t2;
                e.moveAdd(d, new int[] {tj}, new int[] {2});
                e.move(cin, tj);
                for (int v = 1; v <= 19; v++) {
                    e.moveTo(tj);
                    e.raw("[");
                    e.add(tj, -1);
                    if (v == 10) {
                        e.add(d, -9);
                        e.add(cout, 1);
                    } else {
                        e.add(d, 1);
                    }
                }
                for (int v = 1; v <= 19; v++) {
                    e.moveTo(tj);
                    e.raw("]");
                }
            }
            e.clear(t2);
        });
        e.ifFlag(neg, () -> printConst("-", t));
        int started = neg;
        for (int j = digits - 1; j >= 0; j--) {
            int d = dig + 3 * j;
            if (j > 0) {
                ops.addIsNonZero(d, t);
                e.copyAdd(started, t, t2);
                e.ifNonZeroDestructive(t, () -> {
                    e.clear(started);
                    e.add(started, 1);
                    e.add(d, '0');
                    e.output(d);
                });
            } else {
                e.add(d, '0');
                e.output(d);
            }
            e.clear(d);
        }
        e.clear(started);
        scratch.free(t2);
        scratch.free(t);
        scratch.free(neg);
        epilogue(1, false);
    }

    /** Prints a constant string through a zero temp cell (left zero). */
    private void printConst(String text, int cell) {
        int value = 0;
        for (byte by : text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = by & 0xFF;
            e.add(cell, c - value);
            value = c;
            e.output(cell);
        }
        e.clear(cell);
    }
}
