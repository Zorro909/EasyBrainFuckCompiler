package de.zorro909.brainfuck.compiler.frontend;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import de.zorro909.brainfuck.compiler.CompileException;
import de.zorro909.brainfuck.compiler.frontend.ClassInfo.FieldInfo;
import de.zorro909.brainfuck.compiler.frontend.ClassInfo.MethodInfo;
import de.zorro909.brainfuck.compiler.frontend.Type.ArrayT;
import de.zorro909.brainfuck.compiler.frontend.Type.ClassT;
import de.zorro909.brainfuck.compiler.frontend.Type.Prim;
import de.zorro909.brainfuck.compiler.frontend.Type.TypeVar;
import de.zorro909.brainfuck.compiler.ir.BinOp;
import de.zorro909.brainfuck.compiler.ir.Block;
import de.zorro909.brainfuck.compiler.ir.Function;
import de.zorro909.brainfuck.compiler.ir.Insn;
import de.zorro909.brainfuck.compiler.ir.Operand;
import de.zorro909.brainfuck.compiler.ir.Operand.Imm;
import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import de.zorro909.brainfuck.compiler.ir.Terminator;
import de.zorro909.brainfuck.compiler.ir.UnOp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers one method body to IR. Expressions produce {@link Value}s (an operand plus its static
 * type); conditions are lowered directly into branches so that {@code &&}, {@code ||} and
 * comparisons never materialize booleans unless they are stored.
 */
final class Lowerer {

    record Value(Operand op, Type type) {
    }

    record Local(Reg reg, Type type) {
    }

    /** Assignable locations. */
    sealed interface LValue {
        Type type();
    }

    record LocalLV(Local local) implements LValue {
        public Type type() {
            return local.type();
        }
    }

    record FieldLV(Operand ref, int slot, Type type) implements LValue {
    }

    record StaticLV(int distance, Type type) implements LValue {
    }

    record ArrayLV(Operand ref, Operand index, Type type) implements LValue {
    }

    /** Break/continue/yield target of an enclosing statement. */
    private final class Target {
        final String label;
        final Block breakTo;
        final Block continueTo;
        final Reg yieldReg;
        final Type yieldType;
        /** Labeled plain blocks are only reachable with an explicit label. */
        final boolean labeledBlock;
        /** Number of enclosing try statements; jumping here leaves the ones above. */
        final int tryDepth = tries.size();

        Target(String label, Block breakTo, Block continueTo, Reg yieldReg, Type yieldType) {
            this(label, breakTo, continueTo, yieldReg, yieldType, false);
        }

        Target(String label, Block breakTo, Block continueTo, Reg yieldReg, Type yieldType,
                        boolean labeledBlock) {
            this.label = label;
            this.breakTo = breakTo;
            this.continueTo = continueTo;
            this.yieldReg = yieldReg;
            this.yieldType = yieldType;
            this.labeledBlock = labeledBlock;
        }
    }

    private final Frontend fe;
    private final MethodInfo method;
    private final ClassInfo cls;
    private final Function fn;
    private final Map<String, com.github.javaparser.ast.type.TypeParameter> methodTps;
    private Block cur;
    private final Deque<Map<String, Local>> scopes = new ArrayDeque<>();
    private final Deque<Target> targets = new ArrayDeque<>();

    /**
     * An enclosing try statement: {@code handler} receives exceptions raised inside it (with the
     * exception object in {@code Runtime.pending}), {@code fin} lowers its finally code.
     */
    private record TryRegion(Block handler, Runnable fin) {
    }

    private final Deque<TryRegion> tries = new ArrayDeque<>();
    /** Lazily created block that throws {@code Runtime.pending} out of the method. */
    private Block unwind;
    private Reg thisReg;
    private String pendingLabel;

    private final ClassT objectT;
    private final ClassT stringT;

    Lowerer(Frontend fe, MethodInfo method) {
        this.fe = fe;
        this.method = method;
        this.cls = method.owner;
        this.fn = method.ir;
        this.methodTps = method.typeParamDecls;
        this.objectT = new ClassT(fe.objectClass, List.of());
        this.stringT = new ClassT(fe.stringClass, List.of());
    }

    // ------------------------------------------------------------------ entry points

    void lower() {
        cur = fn.newBlock("entry");
        fn.entry = cur;
        scopes.push(new HashMap<>());
        int p = 0;
        if (!method.isStatic) {
            thisReg = fn.param(p++);
        }
        for (int i = 0; i < method.params.size(); i++) {
            declareLocal(method.paramNames.get(i), method.params.get(i), fn.param(p++));
        }
        if (method.isConstructor) {
            constructorPrologue();
        }
        if (method.exprBody != null) {
            Value v = expr(method.exprBody, method.returnType);
            if (method.returnType == Prim.VOID) {
                terminate(new Terminator.Return(null));
            } else {
                terminate(new Terminator.Return(coerce(v, method.returnType, method.exprBody).op()));
            }
        } else {
            List<Statement> stmts = method.body.getStatements();
            int start = 0;
            if (method.isConstructor && !stmts.isEmpty()
                            && stmts.get(0) instanceof ExplicitConstructorInvocationStmt) {
                start = 1;
            }
            for (int i = start; i < stmts.size(); i++) {
                stmt(stmts.get(i));
            }
        }
        if (cur.terminator == null) {
            terminate(new Terminator.Return(method.returnType == Prim.VOID ? null
                            : Operand.imm(0)));
        }
    }

    /** Body of {@code $clinit}: static initializers of all user classes in order. */
    void lowerStaticInit(List<ClassInfo> classes) {
        cur = fn.newBlock("entry");
        fn.entry = cur;
        scopes.push(new HashMap<>());
        for (ClassInfo c : classes) {
            Lowerer sub = new Lowerer(fe, syntheticStatic(c));
            sub.cur = cur;
            sub.scopes.push(new HashMap<>());
            if (c.isEnum) {
                sub.initEnumConstants(c);
            }
            for (Node n : c.staticInits) {
                if (n instanceof VariableDeclarator v) {
                    FieldInfo f = c.fields.get(v.getNameAsString());
                    if (fe.hasStaticImageInit(f)) {
                        fe.staticSlot(f);
                        continue;
                    }
                    Value val = sub.initializer(v.getInitializer().get(), f.type);
                    sub.store(new StaticLV(fe.staticSlot(f), f.type), val, v);
                } else {
                    sub.stmt(((InitializerDeclaration) n).getBody());
                }
            }
            cur = sub.cur;
        }
        terminate(new Terminator.Return(null));
    }

    private MethodInfo syntheticStatic(ClassInfo c) {
        MethodInfo m = new MethodInfo("<clinit>", c, false);
        m.isStatic = true;
        m.returnType = Prim.VOID;
        m.ir = fn;
        return m;
    }

    private void initEnumConstants(ClassInfo c) {
        ClassT self = c.selfType();
        List<Operand> refs = new ArrayList<>();
        int ordinal = 0;
        FieldInfo nameF = c.findField("name");
        FieldInfo ordF = c.findField("ordinal");
        for (EnumConstantDeclaration ec : Frontend.enumEntries(c)) {
            if (!ec.getClassBody().isEmpty()) {
                throw new CompileException(ec, "Enum constants with class bodies are not supported");
            }
            Reg obj = allocObject(c);
            emit(new Insn.Store(obj, Operand.imm(0), nameF.slot,
                            Operand.imm(fe.stringLiteral(ec.getNameAsString()))));
            emit(new Insn.Store(obj, Operand.imm(0), ordF.slot, Operand.imm(ordinal)));
            List<Arg> args = args(ec.getArguments());
            MethodInfo ctor = selectOverload(c.constructors, args, ec, Map.of());
            callConstructor(ctor, obj, args, Map.of(), ec);
            store(new StaticLV(fe.staticSlot(c.fields.get(ec.getNameAsString())), self),
                            new Value(obj, self), ec);
            refs.add(obj);
            ordinal++;
        }
        Reg arr = newArray(Operand.imm(refs.size()));
        for (int i = 0; i < refs.size(); i++) {
            emit(new Insn.Store(arr, Operand.imm(i), 1, refs.get(i)));
        }
        store(new StaticLV(fe.staticSlot(c.fields.get("$VALUES")), new ArrayT(self)),
                        new Value(arr, new ArrayT(self)), c.decl);
    }

    private void constructorPrologue() {
        List<Statement> stmts = method.body.getStatements();
        if (method.initOnly) {
            // anonymous class: the super constructor already ran at the creation site
        } else if (!stmts.isEmpty() && stmts.get(0) instanceof ExplicitConstructorInvocationStmt eci) {
            List<Arg> args = args(eci.getArguments());
            if (eci.isThis()) {
                MethodInfo ctor = selectOverload(cls.constructors, args, eci, Map.of());
                callConstructor(ctor, thisReg, args, Map.of(), eci);
                return;
            }
            ClassInfo sup = cls.superclass();
            Map<String, Type> env = fe.envFor(cls.selfType(), sup);
            MethodInfo ctor = selectOverload(sup.constructors, args, eci, env);
            callConstructor(ctor, thisReg, args, env, eci);
        } else {
            ClassInfo sup = cls.superclass();
            if (sup != null && sup != fe.objectClass && !sup.isEnum && !sup.name.equals("Enum")
                            && !sup.name.equals("Record")) {
                MethodInfo ctor = selectOverload(sup.constructors, List.of(), method.decl,
                                Map.of());
                callConstructor(ctor, thisReg, List.of(), Map.of(), method.decl);
            }
        }
        for (Node n : cls.instanceInits) {
            if (n instanceof VariableDeclarator v) {
                FieldInfo f = cls.fields.get(v.getNameAsString());
                Value val = initializer(v.getInitializer().get(), f.type);
                store(new FieldLV(thisReg, f.slot, f.type), val, v);
            } else {
                stmt(((InitializerDeclaration) n).getBody());
            }
        }
    }

    // ------------------------------------------------------------------ IR helpers

    private void emit(Insn insn) {
        cur.insns.add(insn);
    }

    private void terminate(Terminator t) {
        cur.terminator = t;
        cur = fn.newBlock("dead");
    }

    private void jump(Block target) {
        if (cur.terminator == null) {
            cur.terminator = new Terminator.Jump(target);
        }
    }

    private void startBlock(Block b) {
        jump(b);
        cur = b;
    }

    private Reg temp() {
        return fn.newReg();
    }

    private Reg bin(BinOp op, Operand a, Operand b) {
        Reg r = temp();
        emit(new Insn.Binary(op, r, a, b));
        return r;
    }

    private Reg un(UnOp op, Operand a) {
        Reg r = temp();
        emit(new Insn.Unary(op, r, a));
        return r;
    }

    private Reg load(Operand ref, Operand index, int offset) {
        Reg r = temp();
        emit(new Insn.Load(r, ref, index, offset));
        return r;
    }

    /** Emits a call; returns the result register or null. */
    private Reg call(Function f, List<Operand> args) {
        Reg result = f.returnsValue ? temp() : null;
        Block next = fn.newBlock("ret");
        cur.terminator = new Terminator.Call(f, List.copyOf(args), result, next, callHandler());
        cur = next;
        return result;
    }

    private Reg callVirtual(MethodInfo m, List<Operand> args) {
        Reg result = m.returnType != Prim.VOID ? temp() : null;
        Block site = cur;
        Function f = fe.virtualCall(m, site);
        Block next = fn.newBlock("ret");
        site.terminator = new Terminator.Call(f, List.copyOf(args), result, next, callHandler());
        cur = next;
        return result;
    }

    /** Handler for calls at this point; null lets exceptions propagate to the caller. */
    private Block callHandler() {
        return tries.isEmpty() ? null : tries.peek().handler();
    }

    /** Where an exception raised at this point goes: the innermost try or out of the method. */
    private Block handler() {
        if (!tries.isEmpty()) {
            return tries.peek().handler();
        }
        if (unwind == null) {
            unwind = fn.newBlock("unwind");
            unwind.terminator = new Terminator.Throw();
        }
        return unwind;
    }

    private Reg loadPending() {
        return load(Operand.imm(fe.pendingSlot()), Operand.imm(0), 0);
    }

    /** {@code throw ex}: hands the exception to the innermost handler. */
    private void throwValue(Operand ex) {
        emit(new Insn.Store(Operand.imm(fe.pendingSlot()), Operand.imm(0), 0, ex));
        jump(handler());
        cur = fn.newBlock("dead");
    }

    /** Lowers the finally code of every try statement a jump to {@code depth} leaves. */
    private void leaveTries(int depth) {
        Deque<TryRegion> left = new ArrayDeque<>();
        while (tries.size() > depth) {
            TryRegion r = tries.pop();
            left.push(r);
            if (r.fin() != null) {
                r.fin().run();
            }
        }
        while (!left.isEmpty()) {
            tries.push(left.pop());
        }
    }

    private Reg callRuntime(String cls, String name, Operand... args) {
        MethodInfo m = fe.runtimeMethod(cls, name, args.length);
        return call(fe.functionOf(m), List.of(args));
    }

    private Reg allocObject(ClassInfo c) {
        if (c.isAbstract) {
            throw new CompileException(c.decl, "Cannot instantiate abstract " + c.name);
        }
        fe.instantiated.add(c);
        Reg obj = callRuntime("Runtime", "alloc", Operand.imm(c.instanceWords));
        emit(new Insn.Store(obj, Operand.imm(0), 0, Operand.imm(c.classId)));
        return obj;
    }

    private Reg newArray(Operand length) {
        return callRuntime("Runtime", "newArray", length);
    }

    // ------------------------------------------------------------------ scopes

    private void declareLocal(String name, Type type, Reg reg) {
        scopes.peek().put(name, new Local(reg, type));
    }

    private Local declare(String name, Type type, Node at) {
        if (findLocal(name) != null) {
            throw new CompileException(at, "Variable " + name + " is already defined");
        }
        Local l = new Local(temp(), type);
        scopes.peek().put(name, l);
        return l;
    }

    private Local findLocal(String name) {
        for (Map<String, Local> s : scopes) {
            Local l = s.get(name);
            if (l != null) {
                return l;
            }
        }
        return null;
    }

    private void pushScope() {
        scopes.push(new HashMap<>());
    }

    private void popScope() {
        scopes.pop();
    }

    // ------------------------------------------------------------------ types

    private Type resolve(com.github.javaparser.ast.type.Type t) {
        return fe.resolveType(t, cls, methodTps);
    }

    private boolean isString(Type t) {
        return t instanceof ClassT c && c.cls() == fe.stringClass;
    }

    private static final Map<String, Prim> UNBOXED = Map.of("Integer", Prim.INT, "Character",
                    Prim.CHAR, "Boolean", Prim.BOOLEAN, "Long", Prim.LONG, "Short", Prim.SHORT,
                    "Byte", Prim.BYTE, "Float", Prim.FLOAT, "Double", Prim.DOUBLE);

    /** Primitive behind a wrapper class type, or null. */
    private Prim unboxed(Type t) {
        if (t instanceof ClassT c && c.cls().isRuntime) {
            return UNBOXED.get(c.cls().name);
        }
        return null;
    }

    private ClassT boxType(Prim p) {
        String name = switch (p) {
            case INT -> "Integer";
            case CHAR -> "Character";
            case BOOLEAN -> "Boolean";
            case LONG -> "Long";
            case SHORT -> "Short";
            case BYTE -> "Byte";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case VOID -> throw new IllegalStateException();
        };
        return fe.classType(name);
    }

    /** Primitive type for arithmetic, unboxing wrapper types; null if not numeric/boolean. */
    private Prim primOf(Type t) {
        if (t instanceof Prim p) {
            return p;
        }
        return unboxed(t);
    }

    private ClassInfo classOfType(Type t, Node at) {
        return switch (t) {
            case ClassT c -> c.cls();
            case TypeVar v -> v.bound() != null ? classOfType(v.bound(), at) : fe.objectClass;
            case Type.Null n -> fe.objectClass;
            default -> throw new CompileException(at, "Not an object type: " + t);
        };
    }

    /** Assignability used by overload resolution. */
    private boolean assignable(Type from, Type to, boolean boxing) {
        if (from == null || to == null) {
            return true;
        }
        if (from.equals(to)) {
            return true;
        }
        if (from instanceof Prim fp && to instanceof Prim tp) {
            return fp.widensTo(tp);
        }
        if (from instanceof Prim fp) {
            if (!boxing || fp == Prim.VOID) {
                return false;
            }
            return isSubtype(boxType(fp), to);
        }
        if (to instanceof Prim tp) {
            if (!boxing) {
                return false;
            }
            Prim u = unboxed(from);
            return u != null && u.widensTo(tp);
        }
        return isSubtype(from, to);
    }

    private boolean isSubtype(Type from, Type to) {
        if (to instanceof TypeVar) {
            return true;
        }
        if (from instanceof TypeVar v) {
            // a type variable is only known to be a subtype of its bound
            return isSubtype(v.bound() != null ? v.bound() : objectT, to);
        }
        if (from == Type.Null.NULL) {
            return to.isReference();
        }
        if (to instanceof ClassT tc) {
            if (tc.cls() == fe.objectClass) {
                return from.isReference();
            }
            if (from instanceof ClassT fc) {
                return fc.cls().isSubclassOf(tc.cls());
            }
            return false;
        }
        if (to instanceof ArrayT ta && from instanceof ArrayT fa) {
            if (ta.elem() instanceof Prim || fa.elem() instanceof Prim) {
                return ta.elem().equals(fa.elem());
            }
            return isSubtype(fa.elem(), ta.elem());
        }
        return false;
    }

    /** Implicit conversion of {@code v} to {@code to} (boxing, unboxing, widening). */
    private Value coerce(Value v, Type to, Node at) {
        Type from = v.type();
        if (to == null || from == null || from.equals(to)) {
            return v;
        }
        if (to instanceof Prim tp) {
            if (tp == Prim.VOID) {
                return v;
            }
            if (from instanceof Prim fp) {
                if ((fp == Prim.BOOLEAN) != (tp == Prim.BOOLEAN)) {
                    throw new CompileException(at, "Incompatible types: " + from + " -> " + to);
                }
                if (fp.isFloating() || tp.isFloating()) {
                    return convertFloating(v, fp, tp, at);
                }
                if (v.op() instanceof Imm(long k) && !fp.widensTo(tp)) {
                    return new Value(Operand.imm(narrowConst(k, tp)), to);
                }
                return new Value(v.op(), to);
            }
            // unboxing (also from Object / type variables: the value must be a wrapper)
            Prim boxed = unboxed(from);
            Value raw = new Value(load(v.op(), Operand.imm(0), 1), boxed != null ? boxed : tp);
            return boxed != null && boxed != tp ? coerce(raw, tp, at) : raw;
        }
        if (from instanceof Prim fp) {
            if (fp == Prim.VOID) {
                throw new CompileException(at, "void value used");
            }
            Prim target = unboxed(to);
            Prim boxAs = target != null ? target : fp;
            Value pv = boxAs == fp ? v : coerce(v, boxAs, at);
            return box(new Value(pv.op(), boxAs), at);
        }
        return new Value(v.op(), to.equals(objectT) ? from : to);
    }

    private long narrowConst(long k, Prim to) {
        return switch (to) {
            case CHAR -> (char) k;
            case BYTE -> (byte) k;
            case SHORT -> (short) k;
            default -> k;
        };
    }

    private Value box(Value v, Node at) {
        Prim p = (Prim) v.type();
        ClassT boxT = boxType(p);
        MethodInfo valueOf = null;
        for (MethodInfo m : boxT.cls().methods) {
            if (m.name.equals("valueOf") && m.params.size() == 1 && m.params.getFirst() == p) {
                valueOf = m;
            }
        }
        if (valueOf == null) {
            throw new CompileException(at, "Cannot box " + p);
        }
        return new Value(call(fe.functionOf(valueOf), List.of(v.op())), boxT);
    }

    /** Explicit narrowing used by casts and compound assignments. */
    private Value narrow(Value v, Prim to) {
        Prim from = primOf(v.type());
        if (from != null && (from.isFloating() || to.isFloating())) {
            return convertFloating(coerce(v, from, null), from, to, null);
        }
        if (from == null || from.widensTo(to) || to == Prim.INT && from == Prim.LONG
                        || to == Prim.LONG || to == Prim.BOOLEAN) {
            return new Value(v.op(), to);
        }
        if (v.op() instanceof Imm(long k)) {
            return new Value(Operand.imm(narrowConst(k, to)), to);
        }
        UnOp op = switch (to) {
            case CHAR -> UnOp.I2C;
            case BYTE -> UnOp.I2B;
            case SHORT -> UnOp.I2S;
            default -> null;
        };
        if (op == null) {
            return new Value(v.op(), to);
        }
        return new Value(un(op, v.op()), to);
    }

    // ------------------------------------------------------------------ statements

    private void stmt(Statement s) {
        switch (s) {
            case BlockStmt b -> {
                pushScope();
                for (Statement st : b.getStatements()) {
                    stmt(st);
                }
                popScope();
            }
            case ExpressionStmt es -> exprStmt(es.getExpression());
            case IfStmt is -> {
                Block then = fn.newBlock("then");
                Block join = fn.newBlock("endif");
                Block els = is.getElseStmt().isPresent() ? fn.newBlock("else") : join;
                cond(is.getCondition(), then, els);
                cur = then;
                scoped(is.getThenStmt());
                jump(join);
                if (els != join) {
                    cur = els;
                    scoped(is.getElseStmt().get());
                    jump(join);
                }
                cur = join;
            }
            case WhileStmt ws -> {
                Block head = fn.newBlock("while");
                Block body = fn.newBlock("body");
                Block exit = fn.newBlock("endwhile");
                startBlock(head);
                cond(ws.getCondition(), body, exit);
                cur = body;
                loopBody(ws.getBody(), exit, head);
                jump(head);
                cur = exit;
            }
            case DoStmt ds -> {
                Block body = fn.newBlock("do");
                Block test = fn.newBlock("dotest");
                Block exit = fn.newBlock("enddo");
                startBlock(body);
                loopBody(ds.getBody(), exit, test);
                startBlock(test);
                cond(ds.getCondition(), body, exit);
                cur = exit;
            }
            case ForStmt fs -> {
                pushScope();
                for (Expression init : fs.getInitialization()) {
                    exprStmt(init);
                }
                Block head = fn.newBlock("for");
                Block body = fn.newBlock("forbody");
                Block update = fn.newBlock("forupdate");
                Block exit = fn.newBlock("endfor");
                startBlock(head);
                if (fs.getCompare().isPresent()) {
                    cond(fs.getCompare().get(), body, exit);
                } else {
                    jump(body);
                }
                cur = body;
                loopBody(fs.getBody(), exit, update);
                startBlock(update);
                for (Expression u : fs.getUpdate()) {
                    exprStmt(u);
                }
                jump(head);
                cur = exit;
                popScope();
            }
            case ForEachStmt fe2 -> forEach(fe2);
            case BreakStmt bs -> {
                Target t = findTarget(bs.getLabel().map(l -> l.asString()).orElse(null), false, bs);
                leaveTries(t.tryDepth);
                jump(t.breakTo);
                cur = fn.newBlock("dead");
            }
            case ContinueStmt cs -> {
                Target t = findTarget(cs.getLabel().map(l -> l.asString()).orElse(null), true, cs);
                leaveTries(t.tryDepth);
                jump(t.continueTo);
                cur = fn.newBlock("dead");
            }
            case ReturnStmt rs -> {
                Operand value = null;
                if (rs.getExpression().isPresent()) {
                    Value v = expr(rs.getExpression().get(), method.returnType);
                    value = coerce(v, method.returnType, rs).op();
                    if (!tries.isEmpty() && value instanceof Reg) {
                        // finally code must not change the value being returned
                        Reg copy = temp();
                        emit(new Insn.Move(copy, value));
                        value = copy;
                    }
                }
                leaveTries(0);
                terminate(new Terminator.Return(value));
            }
            case LabeledStmt ls -> {
                Statement inner = ls.getStatement();
                if (inner instanceof WhileStmt || inner instanceof DoStmt || inner instanceof ForStmt
                                || inner instanceof ForEachStmt || inner instanceof SwitchStmt) {
                    pendingLabel = ls.getLabel().asString();
                    stmt(inner);
                } else {
                    Block exit = fn.newBlock("endlabel");
                    targets.push(new Target(ls.getLabel().asString(), exit, null, null, null, true));
                    stmt(inner);
                    targets.pop();
                    startBlock(exit);
                }
            }
            case SwitchStmt ss -> switchLower(ss.getSelector(), ss.getEntries(), null, ss);
            case ThrowStmt ts -> throwValue(expr(ts.getExpression(), null).op());
            case TryStmt ts -> tryStmt(ts);
            case YieldStmt ys -> {
                Target t = null;
                for (Target k : targets) {
                    if (k.yieldReg != null) {
                        t = k;
                        break;
                    }
                }
                if (t == null) {
                    throw new CompileException(ys, "yield outside of switch expression");
                }
                Value v = coerce(expr(ys.getExpression(), t.yieldType), t.yieldType, ys);
                emit(new Insn.Move(t.yieldReg, v.op()));
                leaveTries(t.tryDepth);
                jump(t.breakTo);
                cur = fn.newBlock("dead");
            }
            case EmptyStmt es -> {
            }
            case AssertStmt as -> {
                // assertions are disabled by default on the JVM
            }
            case SynchronizedStmt sy -> stmt(sy.getBody());
            case LocalClassDeclarationStmt lc -> throw new CompileException(lc,
                            "Local classes are not supported; declare the class as a nested class");
            case ExplicitConstructorInvocationStmt eci -> throw new CompileException(eci,
                            "this(...)/super(...) must be the first statement of a constructor");
            default -> throw new CompileException(s, "Unsupported statement: "
                            + s.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ exceptions

    private void tryStmt(TryStmt ts) {
        Runnable fin = ts.getFinallyBlock().<Runnable>map(b -> () -> scoped(b)).orElse(null);
        if (ts.getResources().isEmpty()) {
            lowerTry(() -> scoped(ts.getTryBlock()), ts.getCatchClauses(), fin);
            return;
        }
        // try (R r = ...) B catch ... finally F  ==  try { R r = ...; try B finally close } ...
        lowerTry(() -> {
            pushScope();
            List<Statement> closes = new ArrayList<>();
            for (Expression r : ts.getResources()) {
                List<String> names = new ArrayList<>();
                if (r instanceof VariableDeclarationExpr vd) {
                    exprStmt(r);
                    vd.getVariables().forEach(v -> names.add(v.getNameAsString()));
                } else {
                    names.add(r.toString());
                }
                for (String n : names) {
                    Statement close = com.github.javaparser.StaticJavaParser.parseStatement(
                                    "if (" + n + " != null) " + n + ".close();");
                    close.setParentNode(ts);
                    closes.addFirst(close);
                }
            }
            lowerTry(() -> scoped(ts.getTryBlock()), List.of(), () -> closes.forEach(this::stmt));
            popScope();
        }, ts.getCatchClauses(), fin);
    }

    /**
     * Lowers {@code try body catch... finally fin}. Exceptions raised in the body reach the
     * catch dispatch (or, without catch clauses, the finally handler) through
     * {@code Runtime.pending}; finally code is inlined on the normal path, on the exceptional
     * path and before every jump out of the statement.
     */
    private void lowerTry(Runnable body, List<CatchClause> catches, Runnable fin) {
        if (catches.isEmpty() && fin == null) {
            body.run();
            return;
        }
        Block normal = fn.newBlock("tryend");
        Block finHandler = fin != null ? fn.newBlock("finally") : null;
        Block dispatch = catches.isEmpty() ? null : fn.newBlock("catch");
        tries.push(new TryRegion(dispatch != null ? dispatch : finHandler, fin));
        body.run();
        tries.pop();
        jump(normal);
        if (dispatch != null) {
            cur = dispatch;
            Reg ex = loadPending();
            if (fin != null) {
                tries.push(new TryRegion(finHandler, fin));
            }
            for (CatchClause cc : catches) {
                Block clause = fn.newBlock("catchbody");
                Parameter p = cc.getParameter();
                List<com.github.javaparser.ast.type.Type> alts = p.getType().isUnionType()
                                ? new ArrayList<>(p.getType().asUnionType().getElements())
                                : List.of(p.getType());
                ClassT declared = null;
                for (var alt : alts) {
                    Type t = resolve(alt);
                    if (!(t instanceof ClassT ct)) {
                        throw new CompileException(alt, "Not an exception type: " + alt);
                    }
                    declared = declared == null ? ct : commonSuperclass(declared, ct);
                    Block next = fn.newBlock("catchnext");
                    if (ct.cls() == fe.runtimeClass("Throwable")) {
                        jump(clause);
                    } else {
                        typeTest(ex, ct, clause, next, alt);
                    }
                    cur = next;
                }
                Block noMatch = cur;
                cur = clause;
                pushScope();
                Reg param = temp();
                emit(new Insn.Move(param, ex));
                declareLocal(p.getNameAsString(), declared, param);
                stmt(cc.getBody());
                popScope();
                jump(normal);
                cur = noMatch;
            }
            // no clause matched: Runtime.pending still holds the exception
            jump(handler());
            if (fin != null) {
                tries.pop();
            }
        }
        if (fin != null) {
            cur = finHandler;
            Reg ex = loadPending();
            fin.run();
            throwValue(ex);
        }
        cur = normal;
        if (fin != null) {
            fin.run();
        }
    }

    private ClassT commonSuperclass(ClassT a, ClassT b) {
        ClassInfo c = a.cls();
        while (!b.cls().isSubclassOf(c)) {
            c = ((ClassT) c.superType).cls();
        }
        return c == a.cls() ? a : new ClassT(c, List.of());
    }

    /** A sub-statement with its own scope (declarations in if-branches stay local). */
    private void scoped(Statement s) {
        pushScope();
        stmt(s);
        popScope();
    }

    private void loopBody(Statement body, Block breakTo, Block continueTo) {
        String label = pendingLabel;
        pendingLabel = null;
        targets.push(new Target(label, breakTo, continueTo, null, null));
        scoped(body);
        targets.pop();
    }

    private Target findTarget(String label, boolean isContinue, Node at) {
        for (Target t : targets) {
            if (label != null) {
                if (label.equals(t.label)) {
                    if (isContinue && t.continueTo == null) {
                        throw new CompileException(at, "continue target is not a loop");
                    }
                    return t;
                }
                continue;
            }
            if (t.labeledBlock) {
                continue;
            }
            if (isContinue) {
                if (t.continueTo != null) {
                    return t;
                }
                continue;
            }
            if (t.yieldReg != null) {
                throw new CompileException(at, "break out of a switch expression");
            }
            return t;
        }
        throw new CompileException(at, (isContinue ? "continue" : "break") + " outside of loop");
    }

    private void forEach(ForEachStmt fs) {
        pushScope();
        Value iterable = expr(fs.getIterable(), null);
        var decl = fs.getVariable().getVariables().get(0);
        Type declared = resolve(decl.getType());
        String label = pendingLabel;
        pendingLabel = null;
        Block head = fn.newBlock("foreach");
        Block body = fn.newBlock("eachbody");
        Block update = fn.newBlock("eachnext");
        Block exit = fn.newBlock("endeach");
        if (iterable.type() instanceof ArrayT at) {
            Reg arr = temp();
            emit(new Insn.Move(arr, iterable.op()));
            Reg len = load(arr, Operand.imm(0), 0);
            Reg idx = temp();
            emit(new Insn.Move(idx, Operand.imm(0)));
            startBlock(head);
            Reg c = bin(BinOp.LT, idx, len);
            cur.terminator = new Terminator.Branch(c, body, exit);
            cur = body;
            Type elemT = declared != null ? declared : at.elem();
            Local v = declare(decl.getNameAsString(), elemT, decl);
            Value e = coerce(new Value(load(arr, idx, 1), at.elem()), elemT, fs);
            emit(new Insn.Move(v.reg(), e.op()));
            targets.push(new Target(label, exit, update, null, null));
            scoped(fs.getBody());
            targets.pop();
            startBlock(update);
            emit(new Insn.Binary(BinOp.ADD, idx, idx, Operand.imm(1)));
            jump(head);
        } else {
            ClassInfo ic = classOfType(iterable.type(), fs);
            Value it = invoke(iterable, ic, "iterator", List.of(), fs, false);
            Reg itReg = temp();
            emit(new Insn.Move(itReg, it.op()));
            Value itV = new Value(itReg, it.type());
            startBlock(head);
            ClassInfo itc = classOfType(it.type(), fs);
            Value has = invoke(itV, itc, "hasNext", List.of(), fs, false);
            cur.terminator = new Terminator.Branch(has.op(), body, exit);
            cur = body;
            Value next = invoke(itV, itc, "next", List.of(), fs, false);
            Type elemT = declared != null ? declared : next.type();
            Local v = declare(decl.getNameAsString(), elemT, decl);
            emit(new Insn.Move(v.reg(), coerce(next, elemT, fs).op()));
            targets.push(new Target(label, exit, update, null, null));
            scoped(fs.getBody());
            targets.pop();
            startBlock(update);
            jump(head);
        }
        cur = exit;
        popScope();
    }

    private void exprStmt(Expression e) {
        if (e instanceof VariableDeclarationExpr vd) {
            for (VariableDeclarator v : vd.getVariables()) {
                Type t = resolve(v.getType());
                if (v.getInitializer().isPresent()) {
                    Value init = initializer(v.getInitializer().get(), t);
                    if (t == null) {
                        t = init.type() == Type.Null.NULL ? objectT : init.type();
                    }
                    Local l = declare(v.getNameAsString(), t, v);
                    emit(new Insn.Move(l.reg(), coerce(init, t, v).op()));
                } else {
                    if (t == null) {
                        throw new CompileException(v, "'var' needs an initializer");
                    }
                    Local l = declare(v.getNameAsString(), t, v);
                    emit(new Insn.Move(l.reg(), Operand.imm(0)));
                }
            }
            return;
        }
        exprDiscard(e);
    }

    /** Evaluates an expression for its side effects. */
    private void exprDiscard(Expression e) {
        if (e instanceof UnaryExpr u && (u.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                        || u.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT)) {
            // i++ as a statement: no need to keep the old value
            increment(u.getExpression(), u.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                            ? 1 : -1, false, u);
            return;
        }
        expr(e, null);
    }

    private Value initializer(Expression init, Type t) {
        if (init instanceof ArrayInitializerExpr ai) {
            if (!(t instanceof ArrayT at)) {
                throw new CompileException(init, "Array initializer needs an array type");
            }
            return arrayInit(ai, at);
        }
        return expr(init, t);
    }

    // ------------------------------------------------------------------ switch

    /** Lowers a switch statement ({@code resultType == null}) or expression. */
    private Value switchLower(Expression selector, NodeList<SwitchEntry> entries, Type resultType,
                    Node at) {
        Value sel = expr(selector, null);
        Reg selReg = temp();
        Prim unb = unboxed(sel.type());
        Value selV = unb != null ? coerce(sel, unb, at) : sel;
        emit(new Insn.Move(selReg, selV.op()));
        selV = new Value(selReg, selV.type());
        boolean isString = isString(sel.type());
        ClassInfo enumClass = sel.type() instanceof ClassT ct && ct.cls().isEnum ? ct.cls() : null;
        String label = pendingLabel;
        pendingLabel = null;

        Block exit = fn.newBlock("endswitch");
        Reg result = resultType != null ? temp() : null;
        List<Block> bodies = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            bodies.add(fn.newBlock("case"));
        }
        Block defaultBody = exit;
        // pattern variables of each entry, visible in its guard and body
        List<Map<String, Local>> bindings = new ArrayList<>();
        // dispatch chain
        for (int i = 0; i < entries.size(); i++) {
            SwitchEntry en = entries.get(i);
            bindings.add(null);
            if (en.getLabels().isEmpty() || en.isDefault()) {
                defaultBody = bodies.get(i);
            }
            for (Expression lab : en.getLabels()) {
                if (lab instanceof PatternExpr pe) {
                    pushScope();
                    Block matched = en.getGuard().isPresent() ? fn.newBlock("guard")
                                    : bodies.get(i);
                    Block next = fn.newBlock("next");
                    Block notNull = fn.newBlock("notnull");
                    cur.terminator = new Terminator.Branch(bin(BinOp.EQ, selReg, Operand.imm(0)),
                                    next, notNull);
                    cur = notNull;
                    matchPattern(pe, selReg, sel.type(), matched, next);
                    if (en.getGuard().isPresent()) {
                        cur = matched;
                        cond(en.getGuard().get(), bodies.get(i), next);
                    }
                    bindings.set(i, scopes.pop());
                    cur = next;
                    continue;
                }
                if (lab instanceof NullLiteralExpr) {
                    Reg c = bin(BinOp.EQ, selReg, Operand.imm(0));
                    Block next = fn.newBlock("next");
                    cur.terminator = new Terminator.Branch(c, bodies.get(i), next);
                    cur = next;
                    continue;
                }
                Operand c;
                if (isString) {
                    Value l = expr(lab, stringT);
                    c = invoke(selV, fe.stringClass, "equals", List.of(new Arg(l, null)), lab,
                                    false).op();
                } else if (enumClass != null) {
                    FieldInfo f = enumClass.fields.get(lab.toString());
                    if (f == null) {
                        throw new CompileException(lab, "Unknown enum constant " + lab);
                    }
                    Reg constRef = load(Operand.imm(fe.staticSlot(f)), Operand.imm(0), 0);
                    c = bin(BinOp.EQ, selReg, constRef);
                } else {
                    Object k = ConstEval.eval(lab, fe, cls);
                    if (k == null) {
                        throw new CompileException(lab, "case label is not a constant");
                    }
                    c = bin(BinOp.EQ, selReg, Operand.imm(fe.constWord(k)));
                }
                Block next = fn.newBlock("next");
                cur.terminator = new Terminator.Branch(c, bodies.get(i), next);
                cur = next;
            }
        }
        jump(defaultBody);
        targets.push(new Target(label, exit, null, result, resultType));
        pushScope();
        for (int i = 0; i < entries.size(); i++) {
            SwitchEntry en = entries.get(i);
            cur = bodies.get(i);
            if (bindings.get(i) != null) {
                scopes.push(bindings.get(i));
            }
            switch (en.getType()) {
                case STATEMENT_GROUP -> {
                    for (Statement st : en.getStatements()) {
                        stmt(st);
                    }
                    // fall through to the next group
                    jump(i + 1 < entries.size() ? bodies.get(i + 1) : exit);
                }
                case EXPRESSION -> {
                    Expression e = ((ExpressionStmt) en.getStatements().get(0)).getExpression();
                    if (result != null) {
                        Value v = coerce(expr(e, resultType), resultType, e);
                        emit(new Insn.Move(result, v.op()));
                    } else {
                        exprDiscard(e);
                    }
                    jump(exit);
                }
                case BLOCK, THROWS_STATEMENT -> {
                    scoped(en.getStatements().get(0));
                    jump(exit);
                }
            }
            if (bindings.get(i) != null) {
                scopes.pop();
            }
        }
        popScope();
        targets.pop();
        cur = exit;
        return result == null ? null : new Value(result, resultType);
    }

    // ------------------------------------------------------------------ conditions

    /** Lowers a boolean expression into a branch. */
    private void cond(Expression e, Block t, Block f) {
        switch (e) {
            case EnclosedExpr en -> cond(en.getInner(), t, f);
            case UnaryExpr u when u.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT ->
                            cond(u.getExpression(), f, t);
            case BinaryExpr b when b.getOperator() == BinaryExpr.Operator.AND -> {
                Block mid = fn.newBlock("and");
                cond(b.getLeft(), mid, f);
                cur = mid;
                cond(b.getRight(), t, f);
            }
            case BinaryExpr b when b.getOperator() == BinaryExpr.Operator.OR -> {
                Block mid = fn.newBlock("or");
                cond(b.getLeft(), t, mid);
                cur = mid;
                cond(b.getRight(), t, f);
            }
            case BooleanLiteralExpr bl -> jump(bl.getValue() ? t : f);
            case InstanceOfExpr io -> instanceOfCond(io, t, f);
            default -> {
                Value v = coerce(expr(e, Prim.BOOLEAN), Prim.BOOLEAN, e);
                if (v.op() instanceof Imm(long k)) {
                    jump(k != 0 ? t : f);
                } else {
                    cur.terminator = new Terminator.Branch(v.op(), t, f);
                }
            }
        }
        cur = fn.newBlock("dead");
    }

    /** Materializes a condition as 0/1. */
    private Value condValue(Expression e) {
        Reg r = temp();
        Block t = fn.newBlock("true");
        Block f = fn.newBlock("false");
        Block join = fn.newBlock("bool");
        cond(e, t, f);
        cur = t;
        emit(new Insn.Move(r, Operand.imm(1)));
        jump(join);
        cur = f;
        emit(new Insn.Move(r, Operand.imm(0)));
        jump(join);
        cur = join;
        return new Value(r, Prim.BOOLEAN);
    }

    // ------------------------------------------------------------------ expressions

    /**
     * Lowers an expression. {@code expected} is the target type when known (used for lambdas,
     * diamonds and array initializers); the result is not yet converted to it.
     */
    private Value expr(Expression e, Type expected) {
        Object k = constant(e);
        if (k != null) {
            Value v = constValue(k);
            // integral constants are untyped Longs; long matters for >>> and boxing
            return k instanceof Long && isLongConstant(e) ? new Value(v.op(), Prim.LONG) : v;
        }
        return switch (e) {
            case EnclosedExpr en -> expr(en.getInner(), expected);
            case NullLiteralExpr n -> new Value(Operand.imm(0), Type.Null.NULL);
            case NameExpr n -> read(nameLValue(n));
            case FieldAccessExpr fa -> fieldAccess(fa);
            case ArrayAccessExpr aa -> read(arrayLValue(aa));
            case ThisExpr t -> thisValue(t);
            case AssignExpr a -> assign(a);
            case UnaryExpr u -> unary(u);
            case BinaryExpr b -> binary(b);
            case ConditionalExpr c -> conditional(c, expected);
            case CastExpr c -> cast(c);
            case InstanceOfExpr io -> condValue(io);
            case MethodCallExpr mc -> methodCall(mc);
            case ObjectCreationExpr oc -> newObject(oc, expected);
            case ArrayCreationExpr ac -> newArray(ac);
            case ArrayInitializerExpr ai -> {
                if (!(expected instanceof ArrayT at)) {
                    throw new CompileException(e, "Array initializer needs an array type");
                }
                yield arrayInit(ai, at);
            }
            case LambdaExpr l -> lambda(l, expected);
            case MethodReferenceExpr mr -> methodRef(mr, expected);
            case SwitchExpr se -> {
                if (expected == null) {
                    throw new CompileException(e, "switch expression needs a target type here; "
                                    + "assign it to a typed variable");
                }
                yield switchLower(se.getSelector(), se.getEntries(), expected, se);
            }
            case ClassExpr c -> throw new CompileException(e, "Class literals are not supported");
            default -> throw new CompileException(e, "Unsupported expression: "
                            + e.getClass().getSimpleName());
        };
    }

    private Object constant(Expression e) {
        if (e instanceof IntegerLiteralExpr || e instanceof LongLiteralExpr
                        || e instanceof DoubleLiteralExpr
                        || e instanceof CharLiteralExpr || e instanceof BooleanLiteralExpr
                        || e instanceof StringLiteralExpr || e instanceof TextBlockLiteralExpr
                        || e instanceof BinaryExpr || e instanceof UnaryExpr
                        || e instanceof CastExpr || e instanceof ConditionalExpr
                        || e instanceof EnclosedExpr) {
            if (e instanceof NameExpr) {
                return null;
            }
            if (!(e instanceof IntegerLiteralExpr || e instanceof LongLiteralExpr
                            || e instanceof DoubleLiteralExpr || e instanceof CharLiteralExpr || e instanceof BooleanLiteralExpr
                            || e instanceof StringLiteralExpr || e instanceof TextBlockLiteralExpr)
                            && usesLocals(e)) {
                return null;
            }
            return ConstEval.eval(e, fe, cls);
        }
        return null;
    }

    /** Whether a constant expression has type long (binary numeric promotion, JLS 5.6). */
    private boolean isLongConstant(Expression e) {
        return switch (e) {
            case LongLiteralExpr l -> true;
            case EnclosedExpr en -> isLongConstant(en.getInner());
            case CastExpr c -> c.getType().asString().equals("long");
            case UnaryExpr u -> isLongConstant(u.getExpression());
            case BinaryExpr b -> switch (b.getOperator()) {
                case LEFT_SHIFT, SIGNED_RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT -> isLongConstant(b.getLeft());
                default -> isLongConstant(b.getLeft()) || isLongConstant(b.getRight());
            };
            case ConditionalExpr c -> isLongConstant(c.getThenExpr())
                            || isLongConstant(c.getElseExpr());
            case NameExpr n -> {
                for (ClassInfo c = cls; c != null; c = c.outer) {
                    FieldInfo f = c.findField(n.getNameAsString());
                    if (f != null) {
                        yield f.type == Prim.LONG;
                    }
                }
                yield false;
            }
            case FieldAccessExpr fa -> {
                ClassInfo c = fe.lookupClass(fa.getScope().toString(), cls);
                FieldInfo f = c == null ? null : c.findField(fa.getNameAsString());
                yield f != null && f.type == Prim.LONG;
            }
            default -> false;
        };
    }

    /** Whether an expression mentions a local variable (which would shadow constant fields). */
    private boolean usesLocals(Expression e) {
        for (NameExpr n : e.findAll(NameExpr.class)) {
            if (findLocal(n.getNameAsString()) != null) {
                return true;
            }
        }
        return false;
    }

    private Value constValue(Object k) {
        return switch (k) {
            case Long l -> new Value(Operand.imm(l), Prim.INT);
            case Character c -> new Value(Operand.imm(c), Prim.CHAR);
            case Boolean b -> new Value(Operand.imm(b ? 1 : 0), Prim.BOOLEAN);
            case Float f -> new Value(Operand.imm(floatBits(Prim.FLOAT, f)), Prim.FLOAT);
            case Double d -> new Value(Operand.imm(floatBits(Prim.DOUBLE, d)), Prim.DOUBLE);
            case String s -> {
                long ref = fe.stringLiteral(s);
                literalTexts.put(ref, s);
                yield new Value(Operand.imm(ref), stringT);
            }
            default -> throw new IllegalStateException(k.toString());
        };
    }

    private Value thisValue(ThisExpr t) {
        if (thisReg == null) {
            throw new CompileException(t, "'this' in a static context");
        }
        if (t.getTypeName().isPresent()) {
            String outer = t.getTypeName().get().asString();
            Value v = new Value(thisReg, cls.selfType());
            ClassInfo c = cls;
            while (!c.name.equals(outer) && !c.name.endsWith("." + outer)) {
                FieldInfo link = c.fields.get("this$0");
                if (link == null) {
                    throw new CompileException(t, "No enclosing instance of " + outer);
                }
                v = new Value(load(v.op(), Operand.imm(0), link.slot), link.type);
                c = ((ClassT) link.type).cls();
            }
            return v;
        }
        return new Value(thisReg, cls.selfType());
    }

    // ------------------------------------------------------------------ names & fields

    private LValue nameLValue(NameExpr n) {
        String name = n.getNameAsString();
        Local l = findLocal(name);
        if (l != null) {
            return new LocalLV(l);
        }
        LValue f = implicitField(name, n);
        if (f != null) {
            return f;
        }
        throw new CompileException(n, "Cannot find symbol " + name);
    }

    /** Unqualified field access: this class, enclosing instances, outer classes' statics. */
    private LValue implicitField(String name, Node at) {
        Operand receiver = thisReg;
        Type receiverT = thisReg != null ? cls.selfType() : null;
        for (ClassInfo c = cls; c != null; ) {
            FieldInfo f = c.findField(name);
            if (f != null) {
                if (f.isStatic) {
                    return staticLValue(f);
                }
                if (receiver == null) {
                    throw new CompileException(at, "Non-static field " + name
                                    + " referenced from a static context");
                }
                return fieldLValue(new Value(receiver, receiverT), f);
            }
            FieldInfo link = c.fields.get("this$0");
            if (link != null && receiver != null) {
                receiver = load(receiver, Operand.imm(0), link.slot);
                receiverT = link.type;
                c = ((ClassT) link.type).cls();
            } else {
                receiver = null;
                c = c.outer;
            }
        }
        for (ClassInfo c : fe.staticImports(cls, name)) {
            FieldInfo f = c.findField(name);
            if (f != null && f.isStatic) {
                return staticLValue(f);
            }
        }
        return null;
    }

    private LValue staticLValue(FieldInfo f) {
        Object k = ConstEval.fieldConstant(f, fe);
        if (k != null) {
            Value v = constValue(k);
            return new ConstLV(k instanceof Long && f.type instanceof Prim p ? new Value(v.op(), p)
                            : v);
        }
        if (f.owner.name.equals("System") && f.name.equals("in")) {
            return new ConstLV(new Value(Operand.imm(0), f.type));
        }
        return new StaticLV(fe.staticSlot(f), f.type);
    }

    /** A constant pseudo-location (static finals): readable, never written. */
    record ConstLV(Value value) implements LValue {
        public Type type() {
            return value.type();
        }
    }

    private LValue fieldLValue(Value receiver, FieldInfo f) {
        Type t = Type.subst(f.type, fe.envFor(receiver.type(), f.owner));
        return new FieldLV(receiver.op(), f.slot, t);
    }

    private Value fieldAccess(FieldAccessExpr fa) {
        return read(fieldAccessLValue(fa));
    }

    private LValue fieldAccessLValue(FieldAccessExpr fa) {
        String name = fa.getNameAsString();
        Expression scope = fa.getScope();
        ClassInfo staticScope = asClassName(scope);
        if (staticScope != null) {
            FieldInfo f = staticScope.findField(name);
            if (f == null || !f.isStatic) {
                throw new CompileException(fa, "Cannot find static field " + fa);
            }
            return staticLValue(f);
        }
        if (scope instanceof SuperExpr) {
            FieldInfo f = cls.superclass().findField(name);
            return fieldLValue(new Value(thisReg, cls.selfType()), f);
        }
        Value recv = expr(scope, null);
        if (recv.type() instanceof ArrayT) {
            if (name.equals("length")) {
                return new ConstLV(new Value(load(recv.op(), Operand.imm(0), 0), Prim.INT));
            }
            throw new CompileException(fa, "Arrays have no field " + name);
        }
        ClassInfo c = classOfType(recv.type(), fa);
        FieldInfo f = c.findField(name);
        if (f == null) {
            throw new CompileException(fa, "Cannot find field " + name + " in " + c.name);
        }
        if (f.isStatic) {
            return staticLValue(f);
        }
        return fieldLValue(recv, f);
    }

    /** If {@code e} names a class (and not a variable), that class. */
    private ClassInfo asClassName(Expression e) {
        if (e instanceof NameExpr n) {
            String name = n.getNameAsString();
            if (findLocal(name) != null || implicitFieldExists(name)) {
                return null;
            }
            return fe.lookupClass(name, cls);
        }
        if (e instanceof FieldAccessExpr fa) {
            ClassInfo outer = asClassName(fa.getScope());
            if (outer != null) {
                return fe.lookupClass(outer.name + "." + fa.getNameAsString(), cls);
            }
            String q = fa.toString();
            if (q.startsWith("java.") || q.startsWith("javax.")) {
                return fe.lookupClass(q, cls);
            }
        }
        return null;
    }

    private boolean implicitFieldExists(String name) {
        for (ClassInfo c = cls; c != null; ) {
            if (c.findField(name) != null) {
                return true;
            }
            FieldInfo link = c.fields.get("this$0");
            c = link != null ? ((ClassT) link.type).cls() : c.outer;
        }
        return false;
    }

    private LValue arrayLValue(ArrayAccessExpr aa) {
        Value arr = expr(aa.getName(), null);
        if (!(arr.type() instanceof ArrayT at)) {
            throw new CompileException(aa, "Not an array: " + arr.type());
        }
        Value idx = coerce(expr(aa.getIndex(), Prim.INT), Prim.INT, aa);
        return new ArrayLV(arr.op(), idx.op(), at.elem());
    }

    private LValue lvalue(Expression e) {
        return switch (e) {
            case EnclosedExpr en -> lvalue(en.getInner());
            case NameExpr n -> nameLValue(n);
            case FieldAccessExpr fa -> fieldAccessLValue(fa);
            case ArrayAccessExpr aa -> arrayLValue(aa);
            default -> throw new CompileException(e, "Not assignable: " + e);
        };
    }

    private Value read(LValue lv) {
        return switch (lv) {
            case LocalLV l -> new Value(l.local().reg(), l.local().type());
            case FieldLV f -> new Value(load(f.ref(), Operand.imm(0), f.slot()), f.type());
            case StaticLV s -> new Value(load(Operand.imm(s.distance()), Operand.imm(0), 0),
                            s.type());
            case ArrayLV a -> new Value(load(a.ref(), a.index(), 1), a.type());
            case ConstLV c -> c.value();
        };
    }

    private void store(LValue lv, Value v, Node at) {
        Operand op = coerce(v, lv.type(), at).op();
        switch (lv) {
            case LocalLV l -> emit(new Insn.Move(l.local().reg(), op));
            case FieldLV f -> emit(new Insn.Store(f.ref(), Operand.imm(0), f.slot(), op));
            case StaticLV s -> emit(new Insn.Store(Operand.imm(s.distance()), Operand.imm(0), 0,
                            op));
            case ArrayLV a -> emit(new Insn.Store(a.ref(), a.index(), 1, op));
            case ConstLV c -> throw new CompileException(at, "Cannot assign a constant");
        }
    }

    // ------------------------------------------------------------------ assignment & operators

    private Value assign(AssignExpr a) {
        LValue lv = lvalue(a.getTarget());
        if (a.getOperator() == AssignExpr.Operator.ASSIGN) {
            Value v = initializer(a.getValue(), lv.type());
            v = coerce(v, lv.type(), a);
            Value stable = stabilize(v);
            store(lv, stable, a);
            return stable;
        }
        Value old = read(lv);
        BinaryExpr.Operator op = switch (a.getOperator()) {
            case PLUS -> BinaryExpr.Operator.PLUS;
            case MINUS -> BinaryExpr.Operator.MINUS;
            case MULTIPLY -> BinaryExpr.Operator.MULTIPLY;
            case DIVIDE -> BinaryExpr.Operator.DIVIDE;
            case REMAINDER -> BinaryExpr.Operator.REMAINDER;
            case BINARY_AND -> BinaryExpr.Operator.BINARY_AND;
            case BINARY_OR -> BinaryExpr.Operator.BINARY_OR;
            case XOR -> BinaryExpr.Operator.XOR;
            case LEFT_SHIFT -> BinaryExpr.Operator.LEFT_SHIFT;
            case SIGNED_RIGHT_SHIFT -> BinaryExpr.Operator.SIGNED_RIGHT_SHIFT;
            case UNSIGNED_RIGHT_SHIFT -> BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT;
            default -> throw new CompileException(a, "Unsupported operator " + a.getOperator());
        };
        Value result;
        if (op == BinaryExpr.Operator.PLUS && isString(lv.type())) {
            Value r = expr(a.getValue(), null);
            result = concat(List.of(old, r), a);
        } else {
            Value r = expr(a.getValue(), null);
            result = arith(op, old, r, a);
            Prim target = primOf(lv.type());
            if (target != null && lv.type() instanceof Prim) {
                result = narrow(result, target);
            }
        }
        result = stabilize(result);
        store(lv, result, a);
        return result;
    }

    /** Copies immediates/registers that a later store could change into a fresh register. */
    private Value stabilize(Value v) {
        if (v.op() instanceof Reg r) {
            Reg t = temp();
            emit(new Insn.Move(t, r));
            return new Value(t, v.type());
        }
        return v;
    }

    private Value increment(Expression target, int delta, boolean wantOld, Node at) {
        LValue lv = lvalue(target);
        Value old = read(lv);
        Prim p = primOf(lv.type());
        if (p == null || p == Prim.BOOLEAN) {
            throw new CompileException(at, "Bad operand for ++/--: " + lv.type());
        }
        Value oldPrim = coerce(old, p, at);
        Reg oldCopy = null;
        if (wantOld) {
            oldCopy = temp();
            emit(new Insn.Move(oldCopy, oldPrim.op()));
        }
        Value nv;
        if (p.isFloating()) {
            nv = floatCall(p, "add", p, oldPrim.op(), Operand.imm(floatBits(p, delta)));
        } else {
            nv = narrow(new Value(bin(BinOp.ADD, oldPrim.op(), Operand.imm(delta)), p), p);
        }
        store(lv, nv, at);
        return wantOld ? new Value(oldCopy, p) : nv;
    }

    private Value unary(UnaryExpr u) {
        return switch (u.getOperator()) {
            case PLUS -> {
                Value v = expr(u.getExpression(), null);
                yield promoteUnary(v, u);
            }
            case MINUS -> {
                Value v = promoteUnary(expr(u.getExpression(), null), u);
                if (v.type() instanceof Prim p && p.isFloating()) {
                    if (v.op() instanceof Imm(long k)) {
                        yield new Value(Operand.imm(floatBits(p, -floatValue(p, k))), p);
                    }
                    yield floatCall(p, "neg", p, v.op());
                }
                yield new Value(un(UnOp.NEG, v.op()), v.type());
            }
            case BITWISE_COMPLEMENT -> {
                Value v = promoteUnary(expr(u.getExpression(), null), u);
                if (((Prim) v.type()).isFloating()) {
                    throw new CompileException(u, "Bad operand type " + v.type() + " for ~");
                }
                yield new Value(un(UnOp.NOT, v.op()), v.type());
            }
            case LOGICAL_COMPLEMENT -> {
                Value v = coerce(expr(u.getExpression(), Prim.BOOLEAN), Prim.BOOLEAN, u);
                yield new Value(un(UnOp.LNOT, v.op()), Prim.BOOLEAN);
            }
            case PREFIX_INCREMENT -> increment(u.getExpression(), 1, false, u);
            case PREFIX_DECREMENT -> increment(u.getExpression(), -1, false, u);
            case POSTFIX_INCREMENT -> increment(u.getExpression(), 1, true, u);
            case POSTFIX_DECREMENT -> increment(u.getExpression(), -1, true, u);
        };
    }

    private Value promoteUnary(Value v, Node at) {
        Prim p = primOf(v.type());
        if (p == null || !p.isNumeric()) {
            throw new CompileException(at, "Bad operand type " + v.type());
        }
        Value c = coerce(v, p, at);
        return new Value(c.op(), p == Prim.LONG || p.isFloating() ? p : Prim.INT);
    }

    private Value binary(BinaryExpr b) {
        BinaryExpr.Operator op = b.getOperator();
        switch (op) {
            case AND, OR -> {
                return condValue(b);
            }
            case PLUS -> {
                Object chain = plusChain(b);
                if (chain instanceof Value v) {
                    return v;
                }
                @SuppressWarnings("unchecked")
                List<Value> parts = (List<Value>) chain;
                return concat(parts, b);
            }
            default -> {
                Value l = expr(b.getLeft(), null);
                Value r = expr(b.getRight(), null);
                return arith(op, l, r, b);
            }
        }
    }

    /**
     * Lowers a left-nested {@code +} chain. Returns a {@link Value} for numeric addition or the
     * list of string concatenation operands (evaluated left to right).
     */
    private Object plusChain(Expression e) {
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS
                        && constant(e) == null) {
            Object left = plusChain(b.getLeft());
            if (left instanceof List<?> l) {
                @SuppressWarnings("unchecked")
                List<Value> parts = (List<Value>) l;
                parts.add(expr(b.getRight(), null));
                return parts;
            }
            Value lv = (Value) left;
            Value rv = expr(b.getRight(), null);
            if (isString(lv.type()) || isString(rv.type())) {
                List<Value> parts = new ArrayList<>();
                parts.add(lv);
                parts.add(rv);
                return parts;
            }
            return arith(BinaryExpr.Operator.PLUS, lv, rv, b);
        }
        return expr(e, null);
    }

    private Value arith(BinaryExpr.Operator op, Value l, Value r, Node at) {
        Prim lp = primOf(l.type());
        Prim rp = primOf(r.type());
        if (op == BinaryExpr.Operator.EQUALS || op == BinaryExpr.Operator.NOT_EQUALS) {
            BinOp bop = op == BinaryExpr.Operator.EQUALS ? BinOp.EQ : BinOp.NE;
            // reference comparison unless one side is primitive
            if (l.type() instanceof Prim || r.type() instanceof Prim) {
                Prim p = lp == Prim.BOOLEAN || rp == Prim.BOOLEAN ? Prim.BOOLEAN : Prim.INT;
                if (lp == null || rp == null) {
                    throw new CompileException(at, "Incomparable types " + l.type() + " and "
                                    + r.type());
                }
                if (lp.isFloating() || rp.isFloating()) {
                    Prim fp = Prim.promote(lp, rp);
                    Value eq = floatCall(fp, "eq", Prim.BOOLEAN, coerce(l, fp, at).op(),
                                    coerce(r, fp, at).op());
                    return bop == BinOp.EQ ? eq : new Value(un(UnOp.LNOT, eq.op()), Prim.BOOLEAN);
                }
                Value a = coerce(l, p == Prim.BOOLEAN ? Prim.BOOLEAN : lp, at);
                Value c = coerce(r, p == Prim.BOOLEAN ? Prim.BOOLEAN : rp, at);
                return new Value(bin(bop, a.op(), c.op()), Prim.BOOLEAN);
            }
            return new Value(bin(bop, l.op(), r.op()), Prim.BOOLEAN);
        }
        if (lp == null || rp == null) {
            throw new CompileException(at, "Bad operand types for " + op.asString() + ": "
                            + l.type() + ", " + r.type());
        }
        Value a = coerce(l, lp, at);
        Value c = coerce(r, rp, at);
        if (lp == Prim.BOOLEAN || rp == Prim.BOOLEAN) {
            BinOp bop = switch (op) {
                case BINARY_AND -> BinOp.AND;
                case BINARY_OR -> BinOp.OR;
                case XOR -> BinOp.NE;
                default -> throw new CompileException(at, "Bad operand types for "
                                + op.asString() + ": boolean");
            };
            // both operands are 0/1: avoid the bit-level builtins
            Reg sum = bin(BinOp.ADD, a.op(), c.op());
            return switch (bop) {
                case AND -> new Value(bin(BinOp.EQ, sum, Operand.imm(2)), Prim.BOOLEAN);
                case OR -> new Value(bin(BinOp.NE, sum, Operand.imm(0)), Prim.BOOLEAN);
                default -> new Value(bin(BinOp.EQ, sum, Operand.imm(1)), Prim.BOOLEAN);
            };
        }
        Prim result = Prim.promote(lp, rp);
        if (result.isFloating()) {
            return floatArith(op, a, c, result, at);
        }
        BinOp bop = switch (op) {
            case PLUS -> BinOp.ADD;
            case MINUS -> BinOp.SUB;
            case MULTIPLY -> BinOp.MUL;
            case DIVIDE -> BinOp.DIV;
            case REMAINDER -> BinOp.REM;
            case BINARY_AND -> BinOp.AND;
            case BINARY_OR -> BinOp.OR;
            case XOR -> BinOp.XOR;
            case LEFT_SHIFT -> BinOp.SHL;
            case SIGNED_RIGHT_SHIFT -> BinOp.SHR;
            case UNSIGNED_RIGHT_SHIFT -> BinOp.USHR;
            case LESS -> BinOp.LT;
            case LESS_EQUALS -> BinOp.LE;
            case GREATER -> BinOp.GT;
            case GREATER_EQUALS -> BinOp.GE;
            default -> throw new CompileException(at, "Unsupported operator " + op.asString());
        };
        if ((bop == BinOp.SHL || bop == BinOp.SHR || bop == BinOp.USHR)) {
            result = lp == Prim.LONG ? Prim.LONG : Prim.INT;
        }
        Type rt = bop.isComparison() ? Prim.BOOLEAN : result;
        if (bop == BinOp.USHR && fe.wordBits > 32 && result == Prim.INT) {
            // int >>> on a wider machine word: shift the zero-extended 32-bit value
            Reg masked = bin(BinOp.AND, a.op(), Operand.imm(0xFFFFFFFFL));
            Reg sh = bin(BinOp.USHR, masked, c.op());
            return new Value(sh, rt);
        }
        if ((bop == BinOp.DIV || bop == BinOp.REM) && !(c.op() instanceof Imm(long k) && k != 0)) {
            Block zero = fn.newBlock("divzero");
            Block ok = fn.newBlock("divide");
            cur.terminator = new Terminator.Branch(bin(BinOp.EQ, c.op(), Operand.imm(0)), zero, ok);
            cur = zero;
            callRuntime("Runtime", "divideByZero");
            jump(ok);
            cur = ok;
        }
        return new Value(bin(bop, a.op(), c.op()), rt);
    }

    private Value conditional(ConditionalExpr c, Type expected) {
        Block t = fn.newBlock("tern");
        Block f = fn.newBlock("ternelse");
        Block join = fn.newBlock("ternend");
        Reg r = temp();
        cond(c.getCondition(), t, f);
        cur = t;
        Value a = expr(c.getThenExpr(), expected);
        Block aEnd = cur;
        cur = f;
        Value b = expr(c.getElseExpr(), expected);
        Block bEnd = cur;
        Type type = conditionalType(a.type(), b.type(), expected);
        cur = aEnd;
        emit(new Insn.Move(r, coerce(a, type, c).op()));
        jump(join);
        cur = bEnd;
        emit(new Insn.Move(r, coerce(b, type, c).op()));
        jump(join);
        cur = join;
        return new Value(r, type);
    }

    private Type conditionalType(Type a, Type b, Type expected) {
        if (a.equals(b)) {
            return a;
        }
        Prim pa = a instanceof Prim p ? p : null;
        Prim pb = b instanceof Prim p ? p : null;
        if (pa != null && pb != null) {
            if (pa == Prim.BOOLEAN || pb == Prim.BOOLEAN) {
                return Prim.BOOLEAN;
            }
            return Prim.promote(pa, pb);
        }
        if (pa != null && unboxed(b) != null || pb != null && unboxed(a) != null) {
            return pa != null ? pa : pb;
        }
        if (a == Type.Null.NULL) {
            return pb != null ? boxType(pb) : b;
        }
        if (b == Type.Null.NULL) {
            return pa != null ? boxType(pa) : a;
        }
        if (expected != null && expected.isReference()) {
            return expected;
        }
        return a.isReference() ? a : objectT;
    }

    private Value cast(CastExpr c) {
        Type t = resolve(c.getType());
        Value v = expr(c.getExpression(), t);
        if (t instanceof Prim p) {
            Prim from = primOf(v.type());
            if (from == null) {
                return coerce(v, t, c);
            }
            v = coerce(v, from, c);
            return narrow(v, p);
        }
        if (v.type() instanceof Prim) {
            return coerce(v, t, c);
        }
        if (t instanceof ClassT ct && ct.cls() != fe.objectClass && v.type() != Type.Null.NULL
                        && !isSubtype(v.type(), t)) {
            // checked downcast: null and instances pass, anything else throws
            Reg ref = temp();
            emit(new Insn.Move(ref, v.op()));
            Block test = fn.newBlock("cast");
            Block ok = fn.newBlock("castok");
            Block bad = fn.newBlock("castfail");
            cur.terminator = new Terminator.Branch(bin(BinOp.EQ, ref, Operand.imm(0)), ok, test);
            cur = test;
            typeTest(ref, t, ok, bad, c);
            cur = bad;
            callRuntime("Runtime", "classCast", ref,
                            Operand.imm(fe.stringLiteral(fe.javaName(ct.cls()))));
            jump(ok);
            cur = ok;
            return new Value(ref, t);
        }
        return new Value(v.op(), t);
    }

    // ------------------------------------------------------------------ string concatenation

    private Value concat(List<Value> parts, Node at) {
        if (parts.size() == 2 && isString(parts.get(0).type()) && isString(parts.get(1).type())) {
            Value a = parts.get(0);
            Value b = parts.get(1);
            Reg sa = toStringValue(a, at);
            Reg sb = toStringValue(b, at);
            MethodInfo concat = fe.runtimeMethod("String", "concat", 1);
            return new Value(call(fe.functionOf(concat), List.of(sa, sb)), stringT);
        }
        ClassInfo sbClass = fe.runtimeClass("StringBuilder");
        Reg sb = allocObject(sbClass);
        MethodInfo ctor = selectOverload(sbClass.constructors, List.of(), at, Map.of());
        call(fe.functionOf(ctor), List.of(sb));
        Value sbV = new Value(sb, new ClassT(sbClass, List.of()));
        for (Value p : parts) {
            Value arg = p;
            Prim prim = arg.type() instanceof Prim pp ? pp : null;
            if (prim == Prim.SHORT || prim == Prim.BYTE) {
                arg = new Value(arg.op(), Prim.INT);
            }
            if (arg.type() == Type.Null.NULL) {
                arg = new Value(arg.op(), objectT);
            }
            if (arg.type() instanceof TypeVar || arg.type() instanceof ArrayT) {
                arg = new Value(arg.op(), objectT);
            }
            invoke(sbV, sbClass, "append", List.of(new Arg(arg, null)), at, false);
        }
        return invoke(sbV, sbClass, "toString", List.of(), at, false);
    }

    /** {@code String.valueOf(v)} for any value; strings may be null (prints "null"). */
    private Reg toStringValue(Value v, Node at) {
        if (isString(v.type())) {
            Reg r = temp();
            emit(new Insn.Move(r, v.op()));
            // null strings concatenate as "null"
            Block isNull = fn.newBlock("strnull");
            Block done = fn.newBlock("strok");
            Reg c = bin(BinOp.EQ, r, Operand.imm(0));
            cur.terminator = new Terminator.Branch(c, isNull, done);
            cur = isNull;
            emit(new Insn.Move(r, Operand.imm(fe.stringLiteral("null"))));
            jump(done);
            cur = done;
            return r;
        }
        Value s = invokeStatic(fe.stringClass, "valueOf", List.of(new Arg(v, null)), at);
        return (Reg) s.op();
    }

    // ------------------------------------------------------------------ floating point

    /** Calls {@code FloatOps.name} or {@code DoubleOps.name} for values of type {@code p}. */
    private Value floatCall(Prim p, String name, Type result, Operand... args) {
        MethodInfo m = fe.runtimeMethod(fe.floatOps(p).name, name, args.length);
        return new Value(call(fe.functionOf(m), List.of(args)), result);
    }

    /** Bit pattern of the constant {@code v} as a value of type {@code p}. */
    private long floatBits(Prim p, double v) {
        if (p == Prim.DOUBLE && !fe.doubleIsFloat()) {
            return Double.doubleToRawLongBits(v);
        }
        return Float.floatToRawIntBits((float) v) & 0xFFFFFFFFL;
    }

    private double floatValue(Prim p, long bits) {
        if (p == Prim.DOUBLE && !fe.doubleIsFloat()) {
            return Double.longBitsToDouble(bits);
        }
        return Float.intBitsToFloat((int) bits);
    }

    /** Primitive conversion where float or double is involved (JLS 5.1.2, 5.1.3). */
    private Value convertFloating(Value v, Prim from, Prim to, Node at) {
        if (from == to) {
            return new Value(v.op(), to);
        }
        if (from == Prim.BOOLEAN || to == Prim.BOOLEAN) {
            throw new CompileException(at, "Incompatible types: " + from + " -> " + to);
        }
        if (v.op() instanceof Imm(long k)) {
            if (to.isFloating()) {
                if (from.isFloating()) {
                    return new Value(Operand.imm(floatBits(to, floatValue(from, k))), to);
                }
                boolean single = to == Prim.FLOAT || fe.doubleIsFloat();
                long bits = single ? Float.floatToRawIntBits((float) k) & 0xFFFFFFFFL
                                : Double.doubleToRawLongBits((double) k);
                return new Value(Operand.imm(bits), to);
            }
            double d = floatValue(from, k);
            long w = fe.wordBits >= 64 ? (long) d : (int) d;
            return narrow(new Value(Operand.imm(w), Prim.INT), to);
        }
        if (to.isFloating()) {
            if (from.isFloating()) {
                return fe.doubleIsFloat() ? new Value(v.op(), to)
                                : floatCall(to, "fromOther", to, v.op());
            }
            return floatCall(to, "fromLong", to, v.op());
        }
        // floating -> integral: truncate to a word, then narrow like int -> char/short/byte
        return narrow(floatCall(from, "toLong", Prim.INT, v.op()), to);
    }

    private Value floatArith(BinaryExpr.Operator op, Value l, Value r, Prim p, Node at) {
        Operand x = coerce(l, p, at).op();
        Operand y = coerce(r, p, at).op();
        return switch (op) {
            case PLUS -> floatCall(p, "add", p, x, y);
            case MINUS -> floatCall(p, "sub", p, x, y);
            case MULTIPLY -> floatCall(p, "mul", p, x, y);
            case DIVIDE -> floatCall(p, "div", p, x, y);
            case REMAINDER -> floatCall(p, "rem", p, x, y);
            case LESS -> floatCall(p, "lt", Prim.BOOLEAN, x, y);
            case LESS_EQUALS -> floatCall(p, "le", Prim.BOOLEAN, x, y);
            case GREATER -> floatCall(p, "lt", Prim.BOOLEAN, y, x);
            case GREATER_EQUALS -> floatCall(p, "le", Prim.BOOLEAN, y, x);
            default -> throw new CompileException(at, "Bad operand types for " + op.asString()
                            + ": " + p);
        };
    }

    // ------------------------------------------------------------------ printing

    /** System.out.print/println with direct output of literals and numbers. */
    private void print(List<Expression> args, boolean newline, Node at) {
        if (args.size() > 1) {
            throw new CompileException(at, "print takes one argument");
        }
        List<Value> parts = new ArrayList<>();
        if (!args.isEmpty()) {
            Expression a = args.getFirst();
            Object k = constant(a);
            if (k != null) {
                parts.add(constValue(k));
            } else {
                Object chain = a instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS
                                ? plusChain(a) : expr(a, null);
                if (chain instanceof List<?> l) {
                    for (Object o : l) {
                        parts.add((Value) o);
                    }
                } else {
                    parts.add((Value) chain);
                }
            }
        }
        for (Value p : parts) {
            printValue(p, at, parts.size() == 1);
        }
        if (newline) {
            emit(new Insn.PutChar(Operand.imm('\n')));
        }
    }

    private void printValue(Value p, Node at, boolean whole) {
        Type t = p.type();
        if (isString(t) && p.op() instanceof Imm(long ref) && literalText(ref) != null) {
            printLiteral(literalText(ref));
            return;
        }
        if (t instanceof Prim prim) {
            switch (prim) {
                case CHAR -> {
                    if (p.op() instanceof Imm(long c) && c < 0x80) {
                        emit(new Insn.PutChar(p.op()));
                    } else {
                        callRuntime("Runtime", "printChar", p.op());
                    }
                }
                case BOOLEAN -> callRuntime("Runtime", "printBoolean", p.op());
                case FLOAT, DOUBLE -> {
                    if (p.op() instanceof Imm(long k)) {
                        double d = floatValue(prim, k);
                        printLiteral(prim == Prim.FLOAT || fe.doubleIsFloat()
                                        ? Float.toString((float) d) : Double.toString(d));
                    } else {
                        floatCall(prim, "print", Prim.VOID, p.op());
                    }
                }
                case VOID -> throw new CompileException(at, "void value printed");
                default -> {
                    if (p.op() instanceof Imm(long v)) {
                        printLiteral(Long.toString(v));
                    } else {
                        call(fe.printIntFunction(), List.of(p.op()));
                    }
                }
            }
            return;
        }
        if (isString(t)) {
            callRuntime("Runtime", "printString", p.op());
        } else if (whole && t instanceof ArrayT a && a.elem() == Prim.CHAR) {
            callRuntime("Runtime", "printChars", p.op());
        } else {
            callRuntime("Runtime", "printObject", p.op());
        }
    }

    private final Map<Long, String> literalTexts = new HashMap<>();

    private String literalText(long ref) {
        return literalTexts.get(ref);
    }

    private void printLiteral(String s) {
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            emit(new Insn.PutChar(Operand.imm(b & 0xFF)));
        }
    }

    /** printf/format with a literal format string. */
    private void printf(List<Expression> args, Node at) {
        formatTo(args, at, this::printLiteral, v -> printValue(v, at, true));
    }

    /** {@code String.format} / {@code "...".formatted(...)} with a literal format string. */
    private Value stringFormat(List<Expression> args, Node at) {
        ClassInfo sbClass = fe.runtimeClass("StringBuilder");
        Reg sb = allocObject(sbClass);
        MethodInfo ctor = selectOverload(sbClass.constructors, List.of(), at, Map.of());
        call(fe.functionOf(ctor), List.of(sb));
        Value sbV = new Value(sb, new ClassT(sbClass, List.of()));
        formatTo(args, at, text -> {
            if (!text.isEmpty()) {
                invoke(sbV, sbClass, "append", List.of(new Arg(constValue(text), null)), at, false);
            }
        }, v -> invoke(sbV, sbClass, "append", List.of(new Arg(v, null)), at, false));
        return invoke(sbV, sbClass, "toString", List.of(), at, false);
    }

    /**
     * Walks a literal format string: literal text goes to {@code literal}, each converted
     * argument (a primitive or a string) to {@code value}. Supports the flags {@code -} and
     * {@code 0}, widths, precisions and the conversions d s c b x X f e n %.
     */
    private void formatTo(List<Expression> args, Node at, java.util.function.Consumer<String> literal,
                    java.util.function.Consumer<Value> value) {
        Object fmt = args.isEmpty() ? null : constant(args.getFirst());
        if (!(fmt instanceof String format)) {
            throw new CompileException(at, "format needs a literal format string");
        }
        int argIndex = 1;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char ch = format.charAt(i);
            if (ch != '%') {
                text.append(ch);
                continue;
            }
            int j = i + 1;
            boolean left = false;
            boolean zero = false;
            while (j < format.length() && (format.charAt(j) == '-' || format.charAt(j) == '0')) {
                left |= format.charAt(j) == '-';
                zero |= format.charAt(j) == '0';
                j++;
            }
            int width = 0;
            while (j < format.length() && Character.isDigit(format.charAt(j))) {
                width = width * 10 + format.charAt(j) - '0';
                j++;
            }
            int precision = -1;
            if (j < format.length() && format.charAt(j) == '.') {
                precision = 0;
                j++;
                while (j < format.length() && Character.isDigit(format.charAt(j))) {
                    precision = precision * 10 + format.charAt(j) - '0';
                    j++;
                }
            }
            if (j >= format.length()) {
                throw new CompileException(at, "Bad format string");
            }
            char conv = format.charAt(j);
            i = j;
            if (conv == 'n') {
                text.append('\n');
                continue;
            }
            if (conv == '%') {
                text.append('%');
                continue;
            }
            literal.accept(text.toString());
            text.setLength(0);
            if (argIndex >= args.size()) {
                throw new CompileException(at, "Missing argument for %" + conv);
            }
            Value v = expr(args.get(argIndex++), null);
            if (precision >= 0 && conv != 'f' && conv != 'e') {
                throw new CompileException(at, "Precision is only supported for %f and %e");
            }
            if (conv == 'x' || conv == 'X') {
                Value iv = coerce(v, Prim.INT, at);
                Reg hex = call(fe.functionOf(fe.runtimeMethod("Integer", "toHexString", 1)),
                                List.of(iv.op()));
                v = new Value(hex, stringT);
                if (conv == 'X') {
                    v = invoke(v, fe.stringClass, "toUpperCase", List.of(), at, false);
                }
            } else if (conv == 'c') {
                v = new Value(coerce(v, primOf(v.type()), at).op(), Prim.CHAR);
            } else if (conv == 'f' || conv == 'e') {
                Value d = coerce(v, Prim.DOUBLE, at);
                v = floatCall(Prim.DOUBLE, conv == 'f' ? "toFixed" : "toScientific", stringT,
                                d.op(), Operand.imm(precision >= 0 ? precision : 6));
            } else if (conv != 'd' && conv != 's' && conv != 'b') {
                throw new CompileException(at, "Unsupported format conversion %" + conv);
            }
            if (width > 0) {
                Reg str = toStringValue(v, at);
                Reg padded = callRuntime("Fmt", "pad", str, Operand.imm(width), Operand.imm(
                                left ? 1 : 0), Operand.imm(zero ? '0' : ' '));
                v = new Value(padded, stringT);
            }
            value.accept(v);
        }
        literal.accept(text.toString());
    }

    // ------------------------------------------------------------------ method calls

    /** A call argument: an evaluated value, or a lambda/method reference awaiting its type. */
    record Arg(Value value, Expression deferred) {
    }

    private List<Arg> args(List<Expression> exprs) {
        List<Arg> out = new ArrayList<>();
        for (Expression e : exprs) {
            if (e instanceof LambdaExpr || e instanceof MethodReferenceExpr) {
                out.add(new Arg(null, e));
            } else if (e instanceof ArrayInitializerExpr) {
                throw new CompileException(e, "Array initializer not allowed here");
            } else {
                out.add(new Arg(expr(e, null), null));
            }
        }
        return out;
    }

    private Value methodCall(MethodCallExpr mc) {
        String name = mc.getNameAsString();
        if (mc.getScope().isPresent()) {
            Expression scope = mc.getScope().get();
            String sc = scope.toString();
            if (sc.equals("String") && name.equals("format") && findLocal("String") == null) {
                return stringFormat(mc.getArguments(), mc);
            }
            if (name.equals("formatted") && scope instanceof StringLiteralExpr
                            || name.equals("formatted") && scope instanceof TextBlockLiteralExpr) {
                List<Expression> all = new ArrayList<>();
                all.add(scope);
                all.addAll(mc.getArguments());
                return stringFormat(all, mc);
            }
            if (sc.equals("System.out") || sc.equals("System.err")) {
                boolean err = sc.equals("System.err");
                switch (name) {
                    case "println", "print" -> {
                        if (err) {
                            args(mc.getArguments());
                        } else {
                            print(mc.getArguments(), name.equals("println"), mc);
                        }
                    }
                    case "printf", "format" -> {
                        if (err) {
                            args(mc.getArguments());
                        } else {
                            printf(mc.getArguments(), mc);
                        }
                    }
                    case "flush", "close" -> {
                    }
                    case "write" -> {
                        Value v = coerce(expr(mc.getArgument(0), Prim.INT), Prim.INT, mc);
                        if (!err) {
                            emit(new Insn.PutChar(v.op()));
                        }
                    }
                    default -> throw new CompileException(mc, "Unsupported method " + sc + "."
                                    + name);
                }
                return new Value(null, Prim.VOID);
            }
            if (sc.equals("System.in") && name.equals("read")) {
                return invokeStatic(fe.runtimeClass("InputStream"), "read", List.of(), mc,
                                Operand.imm(0));
            }
            if (sc.equals("Bf") && fe.lookupClass("Bf", cls) == fe.runtimeClass("Bf")) {
                return intrinsic(mc);
            }
            if (sc.equals("Runtime") && name.equals("printRaw") && cls.isRuntime) {
                Object k = constant(mc.getArgument(0));
                printLiteral((String) k);
                return new Value(null, Prim.VOID);
            }
            if (scope instanceof SuperExpr) {
                ClassInfo sup = cls.superclass();
                List<Arg> args = args(mc.getArguments());
                Map<String, Type> env = fe.envFor(cls.selfType(), sup);
                List<MethodInfo> cands = methodsNamed(sup, name, false);
                // interface default methods: Iface.super.m()
                MethodInfo m = selectOverload(cands, args, mc, env);
                MethodInfo impl = m.hasBody() ? m : fe.implementationIn(sup, m);
                List<Operand> ops = new ArrayList<>();
                ops.add(thisReg);
                ops.addAll(convertArgs(m, args, env, mc));
                Reg r = call(fe.functionOf(impl), ops);
                return new Value(r, returnType(m, env, args));
            }
            ClassInfo staticScope = asClassName(scope);
            if (staticScope != null) {
                return invokeStatic(staticScope, name, args(mc.getArguments()), mc);
            }
            Value recv = expr(scope, null);
            if (recv.type() instanceof ArrayT at) {
                if (name.equals("clone") && mc.getArguments().isEmpty()) {
                    Reg r = callRuntime("Runtime", "cloneArray", recv.op());
                    return new Value(r, at);
                }
                if (name.equals("length")) {
                    throw new CompileException(mc, "Use .length (a field) on arrays");
                }
                recv = new Value(recv.op(), objectT);
            }
            if (recv.type() instanceof Prim p) {
                throw new CompileException(mc, "Cannot call " + name + " on primitive " + p);
            }
            ClassInfo c = classOfType(recv.type(), mc);
            return invoke(recv, c, name, args(mc.getArguments()), mc, false);
        }
        // unqualified: this class (and its outer classes)
        List<Arg> args = args(mc.getArguments());
        Operand receiver = thisReg;
        Type receiverT = thisReg != null ? cls.selfType() : null;
        for (ClassInfo c = cls; c != null; ) {
            List<MethodInfo> cands = methodsNamed(c, name, true);
            if (!cands.isEmpty()) {
                MethodInfo m = selectOverload(cands, args, mc, Map.of());
                if (m.isStatic) {
                    return callStatic(m, args, mc);
                }
                if (receiver == null) {
                    throw new CompileException(mc, "Non-static method " + name
                                    + " called from a static context");
                }
                return invokeResolved(new Value(receiver, receiverT), m, args, mc, false);
            }
            FieldInfo link = c.fields.get("this$0");
            if (link != null && receiver != null) {
                receiver = load(receiver, Operand.imm(0), link.slot);
                receiverT = link.type;
                c = ((ClassT) link.type).cls();
            } else {
                receiver = null;
                receiverT = null;
                c = c.outer;
            }
        }
        for (ClassInfo c : fe.staticImports(cls, name)) {
            List<MethodInfo> cands = new ArrayList<>(methodsNamed(c, name, true));
            cands.removeIf(m -> !m.isStatic);
            if (!cands.isEmpty()) {
                return callStatic(selectOverload(cands, args, mc, Map.of()), args, mc);
            }
        }
        throw new CompileException(mc, "Cannot find method " + name);
    }

    private Value intrinsic(MethodCallExpr mc) {
        List<Expression> a = mc.getArguments();
        List<Operand> ops = new ArrayList<>();
        for (Expression e : a) {
            ops.add(expr(e, null).op());
        }
        switch (mc.getNameAsString()) {
            case "putChar" -> emit(new Insn.PutChar(ops.get(0)));
            case "getChar" -> {
                Reg r = temp();
                emit(new Insn.GetChar(r));
                return new Value(r, Prim.INT);
            }
            case "printInt" -> {
                call(fe.printIntFunction(), ops);
            }
            case "halt" -> terminate(new Terminator.Halt());
            case "load" -> {
                return new Value(load(ops.get(0), ops.get(1), 0), Prim.INT);
            }
            case "store" -> emit(new Insn.Store(ops.get(0), ops.get(1), 0, ops.get(2)));
            case "ref" -> {
                return new Value(ops.get(0), Prim.INT);
            }
            case "obj" -> {
                return new Value(ops.get(0), objectT);
            }
            // floating point values as their bit patterns and back (no conversion)
            case "bits" -> {
                return new Value(ops.get(0), Prim.LONG);
            }
            case "toFloat" -> {
                return new Value(ops.get(0), Prim.FLOAT);
            }
            case "toDouble" -> {
                return new Value(ops.get(0), Prim.DOUBLE);
            }
            case "className" -> {
                Reg r = call(fe.classNameFunction(), ops);
                return new Value(r, stringT);
            }
            default -> {
                Object k = constant(mc);
                if (k == null) {
                    k = ConstEval.eval(mc, fe, cls);
                }
                if (k != null) {
                    return constValue(k);
                }
                throw new CompileException(mc, "Unknown intrinsic " + mc.getNameAsString());
            }
        }
        return new Value(null, Prim.VOID);
    }

    /** Methods named {@code name} visible in {@code c}: own, inherited, interface defaults. */
    private List<MethodInfo> methodsNamed(ClassInfo c, String name, boolean includeOuterless) {
        List<MethodInfo> out = new ArrayList<>();
        Set<ClassInfo> seen = new HashSet<>();
        Deque<ClassInfo> work = new ArrayDeque<>(List.of(c));
        while (!work.isEmpty()) {
            ClassInfo k = work.removeFirst();
            if (!seen.add(k)) {
                continue;
            }
            outer:
            for (MethodInfo m : k.methods) {
                if (!m.name.equals(name)) {
                    continue;
                }
                for (MethodInfo existing : out) {
                    if (fe.overrides(existing, m) || sameSignature(existing, m)) {
                        continue outer;
                    }
                }
                out.add(m);
            }
            if (k.superclass() != null) {
                work.add(k.superclass());
            }
            k.interfaces.forEach(i -> work.add(i.cls()));
            if (k.isInterface && k.interfaces.isEmpty()) {
                work.add(fe.objectClass);
            }
        }
        return out;
    }

    private boolean sameSignature(MethodInfo a, MethodInfo b) {
        if (a.params.size() != b.params.size()) {
            return false;
        }
        for (int i = 0; i < a.params.size(); i++) {
            if (!fe.erase(a.params.get(i)).equals(fe.erase(b.params.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private Value invokeStatic(ClassInfo c, String name, List<Arg> args, Node at,
                    Operand... fakeReceiver) {
        List<MethodInfo> cands = methodsNamed(c, name, false);
        if (cands.isEmpty()) {
            throw new CompileException(at, "Cannot find method " + c.name + "." + name);
        }
        MethodInfo m = selectOverload(cands, args, at, Map.of());
        if (!m.isStatic) {
            if (fakeReceiver.length == 1) {
                return invokeResolved(new Value(fakeReceiver[0], c.selfType()), m, args, at, true);
            }
            throw new CompileException(at, "Non-static method " + m + " called statically");
        }
        return callStatic(m, args, at);
    }

    private Value callStatic(MethodInfo m, List<Arg> args, Node at) {
        if (m.owner.isRuntime && m.name.equals("printRaw") && args.size() == 1
                        && args.getFirst().value() != null
                        && args.getFirst().value().op() instanceof Imm(long ref)
                        && literalText(ref) != null) {
            printLiteral(literalText(ref));
            return new Value(null, Prim.VOID);
        }
        Map<String, Type> env = new HashMap<>();
        List<Operand> ops = convertArgs(m, args, env, at);
        Reg r = call(fe.functionOf(m), ops);
        return new Value(r, returnType(m, env, args));
    }

    /** Instance call {@code recv.name(args)} with dynamic dispatch where needed. */
    private Value invoke(Value recv, ClassInfo c, String name, List<Arg> args, Node at,
                    boolean direct) {
        List<MethodInfo> cands = methodsNamed(c, name, false);
        if (cands.isEmpty()) {
            throw new CompileException(at, "Cannot find method " + name + " in " + c.name);
        }
        MethodInfo m = selectOverload(cands, args, at, fe.envFor(recv.type(), c));
        if (m.isStatic) {
            return callStatic(m, args, at);
        }
        return invokeResolved(recv, m, args, at, direct);
    }

    private Value invokeResolved(Value recv, MethodInfo m, List<Arg> args, Node at,
                    boolean direct) {
        Map<String, Type> env = new HashMap<>(fe.envFor(recv.type(), m.owner));
        List<Operand> ops = new ArrayList<>();
        ops.add(recv.op());
        ops.addAll(convertArgs(m, args, env, at));
        Reg r;
        boolean isFinal = Frontend.hasModifier(m.decl,
                        com.github.javaparser.ast.Modifier.Keyword.PRIVATE)
                        || direct && m.hasBody();
        if (isFinal) {
            r = call(fe.functionOf(m), ops);
        } else {
            r = callVirtual(m, ops);
        }
        return new Value(r, returnType(m, env, args));
    }

    private Type returnType(MethodInfo m, Map<String, Type> env, List<Arg> args) {
        Type t = Type.subst(m.returnType, env);
        return eraseFree(t);
    }

    /** Replaces unresolved type variables of methods by Object. */
    private Type eraseFree(Type t) {
        return switch (t) {
            case TypeVar v -> v.bound() != null ? eraseFree(v.bound()) : objectT;
            case ArrayT a -> new ArrayT(eraseFree(a.elem()));
            default -> t;
        };
    }

    /** Converts arguments to the parameter types (boxing, varargs, lambdas). */
    private List<Operand> convertArgs(MethodInfo m, List<Arg> args, Map<String, Type> env,
                    Node at) {
        // infer method type variables from argument types
        for (int i = 0; i < m.params.size() && i < args.size(); i++) {
            if (args.get(i).value() != null) {
                unify(m.params.get(i), args.get(i).value().type(), env, Set.copyOf(m.typeParams));
            }
        }
        List<Operand> ops = new ArrayList<>();
        int n = m.params.size();
        boolean spread = m.isVarArgs && !(args.size() == n && args.get(n - 1).value() != null
                        && (args.get(n - 1).value().type() instanceof ArrayT
                                        || args.get(n - 1).value().type() == Type.Null.NULL));
        int fixed = spread ? n - 1 : n;
        for (int i = 0; i < fixed; i++) {
            Type pt = Type.subst(m.params.get(i), env);
            ops.add(argValue(args.get(i), pt, at).op());
        }
        if (spread) {
            ArrayT at2 = (ArrayT) Type.subst(m.params.get(n - 1), env);
            int count = args.size() - fixed;
            Reg arr = newArray(Operand.imm(count));
            for (int i = 0; i < count; i++) {
                Value v = argValue(args.get(fixed + i), at2.elem(), at);
                emit(new Insn.Store(arr, Operand.imm(i), 1, v.op()));
            }
            ops.add(arr);
        }
        return ops;
    }

    private Value argValue(Arg a, Type pt, Node at) {
        if (a.deferred() != null) {
            return expr(a.deferred(), pt);
        }
        Type target = pt instanceof TypeVar ? objectT : pt;
        return coerce(a.value(), target, at);
    }

    private void unify(Type param, Type arg, Map<String, Type> env, Set<String> tps) {
        if (arg == null || arg == Type.Null.NULL) {
            return;
        }
        switch (param) {
            case TypeVar v when tps.contains(v.name()) -> {
                Type bound = arg instanceof Prim p ? boxType(p) : arg;
                env.putIfAbsent(v.name(), bound);
            }
            case ArrayT pa when arg instanceof ArrayT aa -> unify(pa.elem(), aa.elem(), env, tps);
            case ClassT pc when arg instanceof ClassT ac && !pc.args().isEmpty() -> {
                Map<String, Type> ae = fe.envFor(ac, pc.cls());
                for (int i = 0; i < pc.args().size() && i < pc.cls().typeParams.size(); i++) {
                    Type sub = ae.get(pc.cls().typeParams.get(i));
                    if (sub != null) {
                        unify(pc.args().get(i), sub, env, tps);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Java-style overload resolution in three phases (strict, boxing, varargs). */
    private MethodInfo selectOverload(List<MethodInfo> cands, List<Arg> args, Node at,
                    Map<String, Type> env) {
        for (int phase = 0; phase < 3; phase++) {
            List<MethodInfo> ok = new ArrayList<>();
            for (MethodInfo m : cands) {
                if (applicable(m, args, phase, env)) {
                    ok.add(m);
                }
            }
            if (ok.isEmpty()) {
                continue;
            }
            MethodInfo best = ok.getFirst();
            for (MethodInfo m : ok) {
                if (m != best && moreSpecific(m, best)) {
                    best = m;
                }
            }
            return best;
        }
        List<String> types = new ArrayList<>();
        for (Arg a : args) {
            types.add(a.value() != null ? String.valueOf(a.value().type()) : "lambda");
        }
        String name = cands.isEmpty() ? "?" : cands.getFirst().name;
        throw new CompileException(at, "No applicable method " + name + types
                        + (cands.isEmpty() ? "" : "; candidates: " + cands));
    }

    private boolean applicable(MethodInfo m, List<Arg> args, int phase, Map<String, Type> env) {
        int n = m.params.size();
        if (phase < 2 || !m.isVarArgs) {
            if (args.size() != n) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                if (!argApplicable(args.get(i), Type.subst(m.params.get(i), env), phase >= 1)) {
                    return false;
                }
            }
            return true;
        }
        if (args.size() < n - 1) {
            return false;
        }
        for (int i = 0; i < n - 1; i++) {
            if (!argApplicable(args.get(i), Type.subst(m.params.get(i), env), true)) {
                return false;
            }
        }
        Type elem = ((ArrayT) m.params.get(n - 1)).elem();
        for (int i = n - 1; i < args.size(); i++) {
            if (!argApplicable(args.get(i), Type.subst(elem, env), true)) {
                return false;
            }
        }
        return true;
    }

    private boolean argApplicable(Arg a, Type p, boolean boxing) {
        if (a.deferred() != null) {
            if (!(p instanceof ClassT pc) || !pc.cls().isInterface) {
                return false;
            }
            MethodInfo sam = samOf(pc.cls());
            if (sam == null) {
                return false;
            }
            if (a.deferred() instanceof LambdaExpr l) {
                return l.getParameters().size() == sam.params.size();
            }
            return true;
        }
        return assignable(a.value().type(), p, boxing);
    }

    private boolean moreSpecific(MethodInfo a, MethodInfo b) {
        if (a.params.size() != b.params.size()) {
            return false;
        }
        for (int i = 0; i < a.params.size(); i++) {
            if (!assignable(a.params.get(i), b.params.get(i), false)) {
                return false;
            }
        }
        // prefer subclass declarations for identical signatures
        return !sameSignature(a, b) || a.owner.isSubclassOf(b.owner);
    }

    // ------------------------------------------------------------------ object creation

    private Value newObject(ObjectCreationExpr oc, Type expected) {
        ClassT t = (ClassT) resolve(oc.getType());
        if (oc.getType().getTypeArguments().map(NodeList::isEmpty).orElse(false)
                        && expected instanceof ClassT et && !et.args().isEmpty()) {
            // diamond: borrow the type arguments of the target type
            Map<String, Type> env = fe.envFor(new ClassT(t.cls(), t.cls().selfType().args()),
                            et.cls());
            List<Type> args = new ArrayList<>();
            for (String tp : t.cls().typeParams) {
                Type inferred = null;
                for (int i = 0; i < et.cls().typeParams.size() && i < et.args().size(); i++) {
                    Type mapped = env.get(et.cls().typeParams.get(i));
                    if (mapped instanceof TypeVar v && v.name().equals(tp)) {
                        inferred = et.args().get(i);
                    }
                }
                args.add(inferred != null ? inferred : objectT);
            }
            t = new ClassT(t.cls(), args);
        }
        if (oc.getAnonymousClassBody().isPresent()) {
            return anonymousClass(oc, t);
        }
        ClassInfo c = t.cls();
        List<Arg> args = args(oc.getArguments());
        Reg obj = allocObject(c);
        FieldInfo link = c.fields.get("this$0");
        if (link != null) {
            Value outer = outerInstanceFor(((ClassT) link.type).cls(), oc);
            emit(new Insn.Store(obj, Operand.imm(0), link.slot, outer.op()));
        }
        Map<String, Type> env = fe.envFor(t, c);
        MethodInfo ctor = selectOverload(c.constructors, args, oc, env);
        callConstructor(ctor, obj, args, env, oc);
        return new Value(obj, t);
    }

    /** The innermost enclosing instance of class {@code outer} (for inner classes). */
    private Value outerInstanceFor(ClassInfo outer, Node at) {
        if (thisReg == null) {
            // javac would reject this; treat the inner class as if it were static
            return new Value(Operand.imm(0), outer.selfType());
        }
        Value v = new Value(thisReg, cls.selfType());
        ClassInfo c = cls;
        while (!c.isSubclassOf(outer)) {
            FieldInfo link = c.fields.get("this$0");
            if (link == null) {
                throw new CompileException(at, "No enclosing instance of " + outer.name);
            }
            v = new Value(load(v.op(), Operand.imm(0), link.slot), link.type);
            c = ((ClassT) link.type).cls();
        }
        return v;
    }

    private void callConstructor(MethodInfo ctor, Operand obj, List<Arg> args,
                    Map<String, Type> env, Node at) {
        if (args.isEmpty() && fe.isTrivialConstructor(ctor)) {
            return;
        }
        List<Operand> ops = new ArrayList<>();
        ops.add(obj);
        ops.addAll(convertArgs(ctor, args, new HashMap<>(env), at));
        call(fe.functionOf(ctor), ops);
    }

    private Value newArray(ArrayCreationExpr ac) {
        Type elem = resolve(ac.getElementType());
        int dims = ac.getLevels().size();
        Type full = elem;
        for (int i = 0; i < dims; i++) {
            full = new ArrayT(full);
        }
        if (ac.getInitializer().isPresent()) {
            return arrayInit(ac.getInitializer().get(), (ArrayT) full);
        }
        List<Operand> sizes = new ArrayList<>();
        for (var level : ac.getLevels()) {
            if (level.getDimension().isPresent()) {
                sizes.add(coerce(expr(level.getDimension().get(), Prim.INT), Prim.INT, ac).op());
            }
        }
        if (sizes.size() == 1) {
            return new Value(newArray(sizes.getFirst()), full);
        }
        if (sizes.size() == 2) {
            return new Value(callRuntime("Runtime", "newArray2", sizes.get(0), sizes.get(1)), full);
        }
        // deeper arrays: allocate level by level with loops
        return new Value(multiArray(sizes, 0), full);
    }

    private Reg multiArray(List<Operand> sizes, int level) {
        Reg arr = newArray(sizes.get(level));
        if (level + 1 < sizes.size()) {
            Reg i = temp();
            emit(new Insn.Move(i, Operand.imm(0)));
            Block head = fn.newBlock("mdim");
            Block body = fn.newBlock("mdimbody");
            Block exit = fn.newBlock("mdimend");
            startBlock(head);
            Reg c = bin(BinOp.LT, i, sizes.get(level));
            cur.terminator = new Terminator.Branch(c, body, exit);
            cur = body;
            Reg sub = multiArray(sizes, level + 1);
            emit(new Insn.Store(arr, i, 1, sub));
            emit(new Insn.Binary(BinOp.ADD, i, i, Operand.imm(1)));
            jump(head);
            cur = exit;
        }
        return arr;
    }

    private Value arrayInit(ArrayInitializerExpr ai, ArrayT t) {
        List<Value> vals = new ArrayList<>();
        for (Expression e : ai.getValues()) {
            Value v = e instanceof ArrayInitializerExpr nested ? arrayInit(nested,
                            (ArrayT) t.elem()) : expr(e, t.elem());
            vals.add(stabilize(coerce(v, t.elem(), e)));
        }
        Reg arr = newArray(Operand.imm(vals.size()));
        for (int i = 0; i < vals.size(); i++) {
            emit(new Insn.Store(arr, Operand.imm(i), 1, vals.get(i).op()));
        }
        return new Value(arr, t);
    }

    // ------------------------------------------------------------------ lambdas & inner classes

    /** The single abstract method of a functional interface, or null. */
    private MethodInfo samOf(ClassInfo iface) {
        MethodInfo found = null;
        Deque<ClassInfo> work = new ArrayDeque<>(List.of(iface));
        Set<ClassInfo> seen = new HashSet<>();
        while (!work.isEmpty()) {
            ClassInfo k = work.removeFirst();
            if (!seen.add(k)) {
                continue;
            }
            for (MethodInfo m : k.methods) {
                if (m.isAbstract && !m.isStatic && !isObjectMethod(m)) {
                    if (found != null && !fe.overrides(found, m) && !fe.overrides(m, found)) {
                        return null;
                    }
                    if (found == null) {
                        found = m;
                    }
                }
            }
            k.interfaces.forEach(i -> work.add(i.cls()));
        }
        return found;
    }

    private boolean isObjectMethod(MethodInfo m) {
        for (MethodInfo o : fe.objectClass.methods) {
            if (o.name.equals(m.name) && o.params.size() == m.params.size()) {
                return true;
            }
        }
        return false;
    }

    private Value lambda(LambdaExpr l, Type target) {
        if (!(target instanceof ClassT tc) || !tc.cls().isInterface) {
            throw new CompileException(l, "Lambda needs a functional interface target type");
        }
        MethodInfo sam = samOf(tc.cls());
        if (sam == null) {
            throw new CompileException(l, tc + " is not a functional interface");
        }
        Map<String, Type> env = fe.envFor(tc, sam.owner);
        List<String> names = new ArrayList<>();
        List<Type> types = new ArrayList<>();
        for (int i = 0; i < l.getParameters().size(); i++) {
            Parameter p = l.getParameters().get(i);
            names.add(p.getNameAsString());
            Type declared = p.getType().isUnknownType() || p.getType().isVarType() ? null
                            : resolve(p.getType());
            types.add(declared != null ? declared : eraseFree(Type.subst(sam.params.get(i), env)));
        }
        Statement body = l.getBody();
        Expression exprBody = body instanceof ExpressionStmt es ? es.getExpression() : null;
        return syntheticInstance(l, tc, sam, env, names, types, exprBody,
                        exprBody == null ? (BlockStmt) body : null, List.of(), null);
    }

    private Value methodRef(MethodReferenceExpr mr, Type target) {
        if (!(target instanceof ClassT tc) || !tc.cls().isInterface || samOf(tc.cls()) == null) {
            throw new CompileException(mr, "Method reference needs a functional interface type");
        }
        MethodInfo sam = samOf(tc.cls());
        // rewrite as a lambda: x -> Type.m(x) / x -> x.m() / x -> expr.m(x)
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < sam.params.size(); i++) {
            params.append(i == 0 ? "" : ", ").append("$p").append(i);
        }
        String scope = mr.getScope().toString();
        String id = mr.getIdentifier();
        String call;
        ClassInfo scopeClass = mr.getScope() instanceof TypeExpr || asClassName(mr.getScope()) != null
                        ? fe.lookupClass(scope, cls) : null;
        if (scope.equals("System.out")) {
            call = "System.out." + id + "(" + params + ")";
        } else if (id.equals("new")) {
            call = "new " + scope + "(" + params + ")";
        } else if (scopeClass != null) {
            boolean isStatic = false;
            for (MethodInfo m : methodsNamed(scopeClass, id, false)) {
                if (m.isStatic && m.params.size() == sam.params.size()) {
                    isStatic = true;
                }
            }
            if (isStatic || sam.params.isEmpty()) {
                call = scope + "." + id + "(" + params + ")";
            } else {
                StringBuilder rest = new StringBuilder();
                for (int i = 1; i < sam.params.size(); i++) {
                    rest.append(i == 1 ? "" : ", ").append("$p").append(i);
                }
                call = "((" + scope + ") $p0)." + id + "(" + rest + ")";
            }
        } else {
            // bound receiver: evaluate it now into a captured local
            Value recv = expr(mr.getScope(), null);
            String tmp = "$recv" + fn.regCount();
            Local local = declare(tmp, recv.type(), mr);
            emit(new Insn.Move(local.reg(), recv.op()));
            call = tmp + "." + id + "(" + params + ")";
        }
        Expression lambda = com.github.javaparser.StaticJavaParser.parseExpression(
                        "(" + params + ") -> " + call);
        lambda.setParentNode(mr.getParentNode().orElse(null));
        return lambda((LambdaExpr) lambda, target);
    }

    private Value anonymousClass(ObjectCreationExpr oc, ClassT base) {
        List<BodyDeclaration<?>> members = oc.getAnonymousClassBody().get();
        if (base.cls().isInterface) {
            MethodInfo sam = null;
            return syntheticInstance(oc, base, sam, Map.of(), List.of(), List.of(), null, null,
                            members, null);
        }
        return syntheticInstance(oc, base, null, Map.of(), List.of(), List.of(), null, null,
                        members, args(oc.getArguments()));
    }

    /**
     * Creates a synthetic class for a lambda or anonymous class and instantiates it. Local
     * variables used in the body are captured as fields of the same name; the enclosing
     * instance (if any) is captured as {@code this$0}.
     */
    private Value syntheticInstance(Node site, ClassT base, MethodInfo sam, Map<String, Type> env,
                    List<String> paramNames, List<Type> paramTypes, Expression exprBody,
                    BlockStmt blockBody, List<BodyDeclaration<?>> members, List<Arg> superArgs) {
        var decl = new ClassOrInterfaceDeclaration();
        decl.setName(fe.freshLambdaName(cls).replace('.', '_'));
        ClassInfo c = new ClassInfo(decl.getNameAsString(), decl, cls.isRuntime);
        c.outer = cls;
        if (base.cls().isInterface) {
            c.superType = new ClassT(fe.objectClass, List.of());
            c.interfaces.add(base);
        } else {
            c.superType = base;
        }
        // captured locals: every visible local whose name occurs in the body
        Set<String> used = new HashSet<>();
        site.findAll(NameExpr.class).forEach(n -> used.add(n.getNameAsString()));
        Map<String, Local> captures = new LinkedHashMap<>();
        for (Map<String, Local> scope : scopes) {
            for (var e : scope.entrySet()) {
                if (used.contains(e.getKey()) && !paramNames.contains(e.getKey())) {
                    captures.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        for (var e : captures.entrySet()) {
            c.fields.put(e.getKey(), new FieldInfo(e.getKey(), c, e.getValue().type(), false, true,
                            null));
        }
        if (thisReg != null) {
            c.fields.put("this$0", new FieldInfo("this$0", c, cls.selfType(), false, true, null));
        }
        fe.resolveMembers(c, members);
        if (sam != null) {
            MethodInfo impl = new MethodInfo(sam.name, c, false);
            impl.params.addAll(paramTypes);
            impl.paramNames.addAll(paramNames);
            impl.returnType = eraseFree(Type.subst(sam.returnType, env));
            impl.exprBody = exprBody;
            impl.body = blockBody;
            impl.decl = site;
            c.methods.add(impl);
        }
        // anonymous classes keep only the default constructor; super args go to the base
        c.constructors.removeIf(k -> true);
        fe.addSyntheticClass(c);
        Reg obj = allocObject(c);
        for (var e : captures.entrySet()) {
            emit(new Insn.Store(obj, Operand.imm(0), c.fields.get(e.getKey()).slot,
                            e.getValue().reg()));
        }
        if (thisReg != null) {
            emit(new Insn.Store(obj, Operand.imm(0), c.fields.get("this$0").slot, thisReg));
        }
        if (superArgs != null) {
            ClassInfo sup = base.cls();
            Map<String, Type> senv = fe.envFor(base, sup);
            MethodInfo ctor = selectOverload(sup.constructors, superArgs, site, senv);
            callConstructor(ctor, obj, superArgs, senv, site);
        }
        // instance initializers of anonymous classes
        if (!c.instanceInits.isEmpty()) {
            MethodInfo init = new MethodInfo("<init>", c, true);
            init.returnType = Prim.VOID;
            init.body = new BlockStmt();
            init.decl = site;
            init.initOnly = true;
            c.constructors.add(init);
            call(fe.functionOf(init), List.of(obj));
        }
        return new Value(obj, base);
    }

    // ------------------------------------------------------------------ instanceof

    private void instanceOfCond(InstanceOfExpr io, Block t, Block f) {
        Value v = expr(io.getExpression(), null);
        Reg ref = temp();
        emit(new Insn.Move(ref, v.op()));
        if (io.getPattern().isPresent()) {
            // a pattern never matches null, even when it is unconditional for the static type
            Block notNull = fn.newBlock("notnull");
            cur.terminator = new Terminator.Branch(bin(BinOp.EQ, ref, Operand.imm(0)), f, notNull);
            cur = notNull;
            matchPattern(io.getPattern().get(), ref, v.type(), t, f);
        } else {
            typeTest(ref, resolve(io.getType()), t, f, io);
        }
    }

    /**
     * Matches {@code ref} (of static type {@code type}) against a type or record pattern,
     * declaring the pattern variables in the current scope. Nested patterns that are
     * unconditional for their component type also match {@code null}, as in Java.
     */
    private void matchPattern(PatternExpr p, Reg ref, Type type, Block t, Block f) {
        switch (p) {
            case TypePatternExpr tp -> {
                Type target = tp.getType().isVarType() ? type : resolve(tp.getType());
                Reg r = temp();
                declareLocal(tp.getNameAsString(), target, r);
                emit(new Insn.Move(r, ref));
                if (!type.isReference() || isSubtype(type, target)) {
                    jump(t);
                } else {
                    typeTest(ref, target, t, f, tp);
                }
            }
            case RecordPatternExpr rp -> {
                Type target = resolve(rp.getType());
                if (!(target instanceof ClassT rt) || !rt.cls().isRecord) {
                    throw new CompileException(rp, rp.getType() + " is not a record");
                }
                var params = ((RecordDeclaration) rt.cls().decl).getParameters();
                if (params.size() != rp.getPatternList().size()) {
                    throw new CompileException(rp, "Record " + rt.cls().name + " has "
                                    + params.size() + " components");
                }
                Block ok = fn.newBlock("record");
                typeTest(ref, target, ok, f, rp);
                cur = ok;
                Map<String, Type> env = fe.envFor(target, rt.cls());
                for (int i = 0; i < params.size(); i++) {
                    FieldInfo field = rt.cls().fields.get(params.get(i).getNameAsString());
                    Reg component = load(ref, Operand.imm(0), field.slot);
                    Block next = fn.newBlock("component");
                    matchPattern(rp.getPatternList().get(i), component,
                                    Type.subst(field.type, env), next, f);
                    cur = next;
                }
                jump(t);
            }
            default -> throw new CompileException(p, "Unsupported pattern");
        }
    }

    /** Jumps to {@code t} if {@code ref} is a non-null instance of {@code target}. */
    private void typeTest(Reg ref, Type target, Block t, Block f, Node at) {
        Block notNull = fn.newBlock("notnull");
        Reg isNull = bin(BinOp.EQ, ref, Operand.imm(0));
        cur.terminator = new Terminator.Branch(isNull, f, notNull);
        cur = notNull;
        if (!(target instanceof ClassT tc)) {
            // arrays carry no class id, so only statically known array types can be tested
            throw new CompileException(at, "Type test against " + target
                            + " is not supported; arrays have no runtime type");
        }
        if (tc.cls() == fe.objectClass) {
            jump(t);
            return;
        }
        Reg cid = load(ref, Operand.imm(0), 0);
        List<ClassInfo> subs = fe.subtypes(tc.cls());
        for (ClassInfo s : subs) {
            if (s.isInterface || s.isAbstract) {
                continue;
            }
            Reg eq = bin(BinOp.EQ, cid, Operand.imm(s.classId));
            Block next = fn.newBlock("isnext");
            cur.terminator = new Terminator.Branch(eq, t, next);
            cur = next;
        }
        jump(f);
    }
}
