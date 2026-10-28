package de.zorro909.brainfuck.compiler.backend;

import static de.zorro909.brainfuck.compiler.ir.Operand.imm;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.zorro909.brainfuck.bf.BfInterpreter;
import de.zorro909.brainfuck.compiler.ir.BinOp;
import de.zorro909.brainfuck.compiler.ir.Block;
import de.zorro909.brainfuck.compiler.ir.Builtin;
import de.zorro909.brainfuck.compiler.ir.Function;
import de.zorro909.brainfuck.compiler.ir.Insn;
import de.zorro909.brainfuck.compiler.ir.Operand;
import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import de.zorro909.brainfuck.compiler.ir.Program;
import de.zorro909.brainfuck.compiler.ir.Terminator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs hand-built IR through the backend and the interpreter. */
class BackendTest {

    /** Tiny IR builder: straight-line code with helpers for printing. */
    static final class Builder {
        final Program program = new Program();
        final Function printInt = Function.builtin(Builtin.PRINT_INT);
        final Function fn;
        Block block;

        Builder() {
            fn = new Function("main", 0, false);
            program.main = fn;
            program.functions.add(fn);
            block = fn.newBlock("entry");
            fn.entry = block;
        }

        Reg bin(BinOp op, Operand a, Operand b) {
            Reg r = fn.newReg();
            block.insns.add(new Insn.Binary(op, r, a, b));
            return r;
        }

        void call(Function callee, Reg result, Operand... args) {
            Block next = fn.newBlock("cont");
            block.terminator = new Terminator.Call(callee, List.of(args), result, next);
            block = next;
        }

        void print(Operand v) {
            call(printInt, null, v);
            block.insns.add(new Insn.PutChar(imm('\n')));
        }

        String run(int wordBytes) {
            block.terminator = new Terminator.Return(null);
            return BackendTest.run(program, wordBytes);
        }
    }

    static String run(Program program, int wordBytes) {
        var out = new BfBackend(new BfBackend.Options(wordBytes, 64)).compile(program);
        var bytes = new ByteArrayOutputStream();
        new BfInterpreter(BfInterpreter.Config.defaults().withMaxSteps(2_000_000_000L))
                        .run(out.code(), new ByteArrayInputStream(new byte[0]), bytes);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8})
    void arithmeticAndPrinting(int w) {
        Builder b = new Builder();
        long[][] cases = {
            {1234, 4321}, {-7, 3}, {300, -300}, {0, 0}, {255, 1}, {-1, -1}, {32767, 2},
            {12345, 678}, {-32768, 1}
        };
        StringBuilder expected = new StringBuilder();
        int bits = 8 * w;
        for (long[] c : cases) {
            Reg x = b.fn.newReg();
            Reg y = b.fn.newReg();
            b.block.insns.add(new Insn.Move(x, imm(c[0])));
            b.block.insns.add(new Insn.Move(y, imm(c[1])));
            for (BinOp op : List.of(BinOp.ADD, BinOp.SUB, BinOp.MUL, BinOp.DIV, BinOp.REM,
                            BinOp.LT, BinOp.LE, BinOp.GT, BinOp.GE, BinOp.EQ, BinOp.NE, BinOp.AND,
                            BinOp.OR, BinOp.XOR)) {
                if ((op == BinOp.DIV || op == BinOp.REM) && c[1] == 0) {
                    continue;
                }
                b.print(b.bin(op, x, y));
                expected.append(eval(op, c[0], c[1], bits)).append('\n');
            }
        }
        assertEquals(expected.toString(), b.run(w));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void shifts(int w) {
        Builder b = new Builder();
        StringBuilder expected = new StringBuilder();
        int bits = 8 * w;
        for (long v : new long[] {1, -100, 12345}) {
            for (long s : new long[] {0, 1, 3, 8, 13}) {
                Reg x = b.fn.newReg();
                b.block.insns.add(new Insn.Move(x, imm(v)));
                Reg sr = b.fn.newReg();
                b.block.insns.add(new Insn.Move(sr, imm(s)));
                for (BinOp op : List.of(BinOp.SHL, BinOp.SHR, BinOp.USHR)) {
                    b.print(b.bin(op, x, sr));
                    expected.append(eval(op, v, s, bits)).append('\n');
                }
            }
        }
        assertEquals(expected.toString(), b.run(w));
    }

    static long eval(BinOp op, long a, long b, int bits) {
        long r = switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> sx(a, bits) / sx(b, bits);
            case REM -> sx(a, bits) % sx(b, bits);
            case AND -> a & b;
            case OR -> a | b;
            case XOR -> a ^ b;
            case SHL -> a << b;
            case SHR -> sx(a, bits) >> b;
            case USHR -> (a & ((bits == 64) ? -1 : (1L << bits) - 1)) >>> b;
            case LT -> sx(a, bits) < sx(b, bits) ? 1 : 0;
            case LE -> sx(a, bits) <= sx(b, bits) ? 1 : 0;
            case GT -> sx(a, bits) > sx(b, bits) ? 1 : 0;
            case GE -> sx(a, bits) >= sx(b, bits) ? 1 : 0;
            case EQ -> sx(a, bits) == sx(b, bits) ? 1 : 0;
            case NE -> sx(a, bits) != sx(b, bits) ? 1 : 0;
        };
        return sx(r, bits);
    }

    static long sx(long v, int bits) {
        return bits == 64 ? v : (v << (64 - bits)) >> (64 - bits);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void recursionAndHeap(int w) {
        Program p = new Program();
        Function printInt = Function.builtin(Builtin.PRINT_INT);
        // fib(n) = n < 2 ? n : fib(n-1) + fib(n-2)
        Function fib = new Function("fib", 1, true);
        Block entry = fib.newBlock("entry");
        fib.entry = entry;
        Block base = fib.newBlock("base");
        Block rec = fib.newBlock("rec");
        Reg n = fib.param(0);
        Reg c = fib.newReg();
        entry.insns.add(new Insn.Binary(BinOp.LT, c, n, imm(2)));
        entry.terminator = new Terminator.Branch(c, base, rec);
        base.terminator = new Terminator.Return(n);
        Reg n1 = fib.newReg();
        Reg r1 = fib.newReg();
        rec.insns.add(new Insn.Binary(BinOp.SUB, n1, n, imm(1)));
        Block mid = fib.newBlock("mid");
        rec.terminator = new Terminator.Call(fib, List.of(n1), r1, mid);
        Reg n2 = fib.newReg();
        Reg r2 = fib.newReg();
        mid.insns.add(new Insn.Binary(BinOp.SUB, n2, n, imm(2)));
        Block end = fib.newBlock("end");
        mid.terminator = new Terminator.Call(fib, List.of(n2), r2, end);
        Reg sum = fib.newReg();
        end.insns.add(new Insn.Binary(BinOp.ADD, sum, r1, r2));
        end.terminator = new Terminator.Return(sum);

        Function main = new Function("main", 0, false);
        p.main = main;
        p.functions.add(main);
        p.functions.add(fib);
        p.staticImage = new long[] {0, 77, 0};
        Block m = main.newBlock("m");
        main.entry = m;
        Reg f = main.newReg();
        Block m2 = main.newBlock("m2");
        m.terminator = new Terminator.Call(fib, List.of(imm(12)), f, m2);
        // heap: store fib at distance 2 + index 3, load it back, plus static at distance 1
        Reg idx = main.newReg();
        m2.insns.add(new Insn.Move(idx, imm(3)));
        m2.insns.add(new Insn.Store(imm(2), idx, 0, f));
        m2.insns.add(new Insn.Store(imm(9), imm(0), 0, imm(-5)));
        Reg loaded = main.newReg();
        m2.insns.add(new Insn.Load(loaded, imm(5), imm(0), 0));
        Reg stat = main.newReg();
        m2.insns.add(new Insn.Load(stat, imm(1), imm(0), 0));
        Reg neg = main.newReg();
        m2.insns.add(new Insn.Load(neg, idx, imm(6), 0));
        Block m3 = main.newBlock("m3");
        m2.terminator = new Terminator.Call(printInt, List.of(loaded), null, m3);
        m3.insns.add(new Insn.PutChar(imm(' ')));
        Block m4 = main.newBlock("m4");
        m3.terminator = new Terminator.Call(printInt, List.of(stat), null, m4);
        m4.insns.add(new Insn.PutChar(imm(' ')));
        Block m5 = main.newBlock("m5");
        m4.terminator = new Terminator.Call(printInt, List.of(neg), null, m5);
        m5.terminator = new Terminator.Return(null);

        assertEquals("144 77 -5", run(p, w));
    }
}
