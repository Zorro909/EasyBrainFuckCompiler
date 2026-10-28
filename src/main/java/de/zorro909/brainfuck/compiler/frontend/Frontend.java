package de.zorro909.brainfuck.compiler.frontend;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
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
import de.zorro909.brainfuck.compiler.ir.Operand.Reg;
import de.zorro909.brainfuck.compiler.ir.Program;
import de.zorro909.brainfuck.compiler.ir.Terminator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java source to IR: builds the class table for the user program plus the runtime library,
 * lowers every reachable method (see {@link Lowerer}) and lays out static data.
 *
 * <p>Code is generated on demand starting from {@code main}, so unused library code costs
 * nothing. Virtual calls go through per-method dispatcher functions whose bodies are generated
 * last, once the set of instantiated classes is known (rapid type analysis); dispatchers with a
 * single target are bypassed entirely.
 */
public final class Frontend {

    public record Options(int wordBytes, int heapWords) {
    }

    /** A user source file. */
    public record Source(String name, String content) {
    }

    private static final List<String> RUNTIME_FILES = List.of("Lang.java", "Util.java");
    private static final Set<String> JAVA_UTIL = Set.of("Comparator", "Collection", "List",
                    "Queue", "Deque", "Set", "ArrayList", "LinkedList", "ArrayDeque", "Stack",
                    "Objects", "Map", "HashMap", "LinkedHashMap", "TreeMap", "HashSet",
                    "LinkedHashSet", "TreeSet", "Collections", "Arrays", "Random",
                    "NoSuchElementException", "InputMismatchException", "EmptyStackException",
                    "ConcurrentModificationException", "Scanner", "StringTokenizer", "Iterator");

    final Options options;
    final int wordBits;

    private final Map<String, ClassInfo> userClasses = new LinkedHashMap<>();
    private final Map<String, ClassInfo> runtimeClasses = new LinkedHashMap<>();
    /**
     * Nested classes by their simple name, as a lenient fallback for unqualified references
     * from outside their outer class. Never shadows a real class; ambiguous names map to null.
     */
    private final Map<String, ClassInfo> userAliases = new HashMap<>();
    private final Map<String, ClassInfo> runtimeAliases = new HashMap<>();
    final List<ClassInfo> allClasses = new ArrayList<>();
    private final Map<Node, ClassInfo> classOfDecl = new IdentityHashMap<>();

    final Program program = new Program();
    private final List<Long> image = new ArrayList<>(List.of(0L));
    private final Map<FieldInfo, Integer> deferredHeapStart = new IdentityHashMap<>();
    private final Map<String, Integer> literals = new HashMap<>();
    private final Deque<MethodInfo> pending = new ArrayDeque<>();
    final Set<ClassInfo> instantiated = new LinkedHashSet<>();
    private final Map<MethodInfo, Dispatcher> dispatchers = new LinkedHashMap<>();
    private Function classNameFn;
    private int lambdaCounter;

    ClassInfo objectClass;
    ClassInfo stringClass;

    public Frontend(Options options) {
        this.options = options;
        this.wordBits = 8 * options.wordBytes();
    }

    // ------------------------------------------------------------------ entry point

    public Program compile(List<Source> sources) {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        List<CompilationUnit> runtimeUnits = new ArrayList<>();
        for (String file : RUNTIME_FILES) {
            runtimeUnits.add(parse(parser, file, readResource(file)));
        }
        if (wordBits >= 32) {
            // software floating point: float is binary32; double is binary64 only if it fits a word
            String template = readResource("SoftFloat.java");
            runtimeUnits.add(parse(parser, "FloatOps.java", softFloat(template, "FloatOps", 23, 8)));
            runtimeUnits.add(parse(parser, "DoubleOps.java", wordBits >= 64
                            ? softFloat(template, "DoubleOps", 52, 11)
                            : softFloat(template, "DoubleOps", 23, 8)));
        }
        List<CompilationUnit> userUnits = new ArrayList<>();
        for (Source s : sources) {
            userUnits.add(parse(parser, s.name(), s.content()));
        }
        for (CompilationUnit cu : runtimeUnits) {
            for (TypeDeclaration<?> td : cu.getTypes()) {
                declare(td, true, null);
            }
        }
        for (CompilationUnit cu : userUnits) {
            for (TypeDeclaration<?> td : cu.getTypes()) {
                declare(td, false, null);
            }
        }
        objectClass = runtimeClasses.get("Object");
        stringClass = runtimeClasses.get("String");
        for (ClassInfo c : allClasses) {
            resolveHeader(c);
        }
        for (ClassInfo c : allClasses) {
            resolveMembers(c);
        }
        int id = 1;
        for (ClassInfo c : allClasses) {
            c.classId = id++;
            layout(c);
        }

        MethodInfo main = findMain();
        Function start = new Function("$start", 0, false);
        program.functions.add(start);
        program.main = start;
        Block entry = start.newBlock("start");
        start.entry = entry;
        Function clinit = buildClinit(userUnits);
        // exceptions that reach $start print like the JVM's default handler and stop
        Block uncaught = start.newBlock("uncaught");
        Reg ex = start.newReg();
        uncaught.insns.add(new Insn.Load(ex, Operand.imm(pendingSlot()), Operand.imm(0), 0));
        Block halt = start.newBlock("halt");
        uncaught.terminator = new Terminator.Call(functionOf(runtimeMethod("Runtime", "uncaught",
                        1)), List.of(ex), null, halt);
        halt.terminator = new Terminator.Halt();
        Block cur = entry;
        if (clinit != null) {
            Block next = start.newBlock("main");
            cur.terminator = new Terminator.Call(clinit, List.of(), null, next, uncaught);
            cur = next;
        }
        int emptyArgs = allocStatic(1);
        Block done = start.newBlock("done");
        cur.terminator = new Terminator.Call(functionOf(main), List.of(Operand.imm(emptyArgs)),
                        null, done, uncaught);
        done.terminator = new Terminator.Return(null);

        drain();
        finishDispatchers();
        for (var e : deferredHeapStart.entrySet()) {
            image.set(e.getValue(), (long) image.size());
        }
        program.staticImage = image.stream().mapToLong(Long::longValue).toArray();
        return program;
    }

    private static CompilationUnit parse(JavaParser parser, String name, String content) {
        var result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            StringBuilder sb = new StringBuilder("Syntax error in " + name + ":");
            result.getProblems().forEach(p -> sb.append("\n  ").append(p.getVerboseMessage()));
            throw new CompileException(sb.toString());
        }
        CompilationUnit cu = result.getResult().get();
        cu.setStorage(java.nio.file.Path.of(name));
        return cu;
    }

    private String softFloat(String template, String name, int mant, int exp) {
        return template.replace("$NAME", name)
                        .replace("$OTHER", name.equals("FloatOps") ? "DoubleOps" : "FloatOps")
                        .replace("$MANT", String.valueOf(mant))
                        .replace("$EXP", String.valueOf(exp))
                        .replace("$WORD", String.valueOf(wordBits));
    }

    /** Runtime class implementing the arithmetic of {@code float} or {@code double}. */
    ClassInfo floatOps(Prim p) {
        if (wordBits < 32) {
            throw new CompileException("float and double need --word-bytes 4 or 8");
        }
        return runtimeClass(p == Prim.FLOAT ? "FloatOps" : "DoubleOps");
    }

    /** Whether double has the same (binary32) format as float. */
    boolean doubleIsFloat() {
        return wordBits < 64;
    }

    private static String readResource(String file) {
        try (InputStream in = Frontend.class.getResourceAsStream(
                        "/de/zorro909/brainfuck/runtime/" + file)) {
            if (in == null) {
                throw new IllegalStateException("runtime library missing: " + file);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private MethodInfo findMain() {
        MethodInfo found = null;
        for (ClassInfo c : allClasses) {
            if (c.isRuntime) {
                continue;
            }
            for (MethodInfo m : c.methods) {
                if (m.name.equals("main") && m.isStatic && m.params.size() == 1
                                && m.params.getFirst() instanceof ArrayT a
                                && a.elem() instanceof ClassT s && s.cls() == stringClass) {
                    if (found != null) {
                        throw new CompileException("More than one main method: "
                                        + found.owner + " and " + c);
                    }
                    found = m;
                }
            }
        }
        if (found == null) {
            throw new CompileException("No 'public static void main(String[] args)' found");
        }
        return found;
    }

    // ------------------------------------------------------------------ class table

    private void declare(TypeDeclaration<?> td, boolean runtime, ClassInfo outer) {
        String simple = td.getNameAsString();
        String name = outer == null ? simple : outer.name + "." + simple;
        if (td instanceof EnumDeclaration en) {
            desugarEnum(en);
        } else if (td instanceof RecordDeclaration rd) {
            desugarRecord(rd);
        }
        ClassInfo c = new ClassInfo(name, td, runtime);
        c.outer = outer;
        Map<String, ClassInfo> ns = runtime ? runtimeClasses : userClasses;
        if (ns.containsKey(name)) {
            throw new CompileException(td, "Duplicate class " + name);
        }
        ns.put(name, c);
        if (outer != null) {
            Map<String, ClassInfo> aliases = runtime ? runtimeAliases : userAliases;
            aliases.put(simple, aliases.containsKey(simple) ? null : c);
        }
        allClasses.add(c);
        classOfDecl.put(td, c);
        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                declare(nested, runtime, c);
            }
        }
    }

    ClassInfo classOf(Node decl) {
        return classOfDecl.get(decl);
    }

    /** Looks up a class by (possibly qualified) name as seen from {@code ctx}. */
    ClassInfo lookupClass(String name, ClassInfo ctx) {
        if (name.startsWith("java.") || name.startsWith("javax.")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        for (ClassInfo c = ctx; c != null; c = c.outer) {
            // nested classes of the context, its outers and their superclasses
            for (ClassInfo s = c; s != null; s = s.superclass()) {
                ClassInfo nested = find(s.name + "." + name, s.isRuntime);
                if (nested != null) {
                    return nested;
                }
            }
        }
        boolean runtime = ctx != null && ctx.isRuntime;
        ClassInfo c = find(name, runtime);
        if (c == null && !runtime) {
            c = runtimeClasses.get(name);
        }
        // last resort: a nested class named without its outer class (e.g. imported Map.Entry)
        if (c == null && !name.contains(".")) {
            c = (runtime ? runtimeAliases : userAliases).get(name);
            if (c == null && !runtime) {
                c = runtimeAliases.get(name);
            }
        }
        return c;
    }

    private ClassInfo find(String name, boolean runtime) {
        return (runtime ? runtimeClasses : userClasses).get(name);
    }

    /** Classes whose static member {@code member} is visible in {@code ctx} by static import. */
    List<ClassInfo> staticImports(ClassInfo ctx, String member) {
        List<ClassInfo> out = new ArrayList<>();
        ClassInfo top = ctx;
        while (top != null && top.outer != null) {
            top = top.outer;
        }
        if (top == null || top.decl == null) {
            return out;
        }
        var cu = top.decl.findCompilationUnit();
        if (cu.isEmpty()) {
            return out;
        }
        for (var imp : cu.get().getImports()) {
            if (!imp.isStatic()) {
                continue;
            }
            String[] parts = imp.getNameAsString().split("\\.");
            String cls;
            if (imp.isAsterisk()) {
                cls = parts[parts.length - 1];
            } else if (parts.length >= 2 && parts[parts.length - 1].equals(member)) {
                cls = parts[parts.length - 2];
            } else {
                continue;
            }
            ClassInfo c = lookupClass(cls, ctx);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    ClassInfo runtimeClass(String name) {
        ClassInfo c = runtimeClasses.get(name);
        if (c == null) {
            throw new IllegalStateException("runtime class missing: " + name);
        }
        return c;
    }

    private void resolveHeader(ClassInfo c) {
        TypeDeclaration<?> td = c.decl;
        if (td instanceof ClassOrInterfaceDeclaration cd) {
            c.isInterface = cd.isInterface();
            c.isAbstract = cd.isAbstract() || cd.isInterface();
            cd.getTypeParameters().forEach(tp -> c.typeParams.add(tp.getNameAsString()));
            for (ClassOrInterfaceType ext : cd.getExtendedTypes()) {
                ClassT t = (ClassT) resolveType(ext, c, Map.of());
                if (c.isInterface) {
                    c.interfaces.add(t);
                } else {
                    c.superType = t;
                }
            }
            for (ClassOrInterfaceType impl : cd.getImplementedTypes()) {
                c.interfaces.add((ClassT) resolveType(impl, c, Map.of()));
            }
        } else if (td instanceof EnumDeclaration ed) {
            c.isEnum = true;
            c.superType = new ClassT(runtimeClass("Enum"), List.of(c.selfType()));
            for (ClassOrInterfaceType impl : ed.getImplementedTypes()) {
                c.interfaces.add((ClassT) resolveType(impl, c, Map.of()));
            }
            ed.getEntries().forEach(e -> c.enumConstants.add(e.getNameAsString()));
        } else if (td instanceof RecordDeclaration rd) {
            c.isRecord = true;
            rd.getTypeParameters().forEach(tp -> c.typeParams.add(tp.getNameAsString()));
            c.superType = new ClassT(runtimeClass("Record"), List.of());
            for (ClassOrInterfaceType impl : rd.getImplementedTypes()) {
                c.interfaces.add((ClassT) resolveType(impl, c, Map.of()));
            }
        } else {
            throw new CompileException(td, "Unsupported type declaration " + td.getClass()
                            .getSimpleName());
        }
        if (c.superType == null && !c.isInterface && c != objectClass) {
            c.superType = new ClassT(objectClass, List.of());
        }
        if (c.superclass() != null) {
            c.superclass().subclasses.add(c);
        }
        for (ClassT i : c.interfaces) {
            i.cls().subclasses.add(c);
        }
    }

    private void resolveMembers(ClassInfo c) {
        resolveMembers(c, c.decl.getMembers());
    }

    void resolveMembers(ClassInfo c, List<BodyDeclaration<?>> members) {
        for (BodyDeclaration<?> member : members) {
            switch (member) {
                case FieldDeclaration fd -> {
                    boolean isStatic = fd.isStatic() || c.isInterface;
                    for (VariableDeclarator v : fd.getVariables()) {
                        Type t = resolveType(v.getType(), c, Map.of());
                        FieldInfo f = new FieldInfo(v.getNameAsString(), c, t, isStatic,
                                        fd.isFinal() || c.isInterface, v.getInitializer()
                                                        .orElse(null));
                        if (c.fields.put(f.name, f) != null) {
                            throw new CompileException(v, "Duplicate field " + f.name);
                        }
                        if (f.init != null) {
                            (isStatic ? c.staticInits : c.instanceInits).add(v);
                        }
                    }
                }
                case MethodDeclaration md -> c.methods.add(method(c, md));
                case ConstructorDeclaration cd -> c.constructors.add(method(c, cd));
                case InitializerDeclaration init -> (init.isStatic() ? c.staticInits
                                : c.instanceInits).add(init);
                case TypeDeclaration<?> nested -> {
                }
                default -> throw new CompileException(member, "Unsupported member");
            }
        }
        if (c.isRecord) {
            RecordDeclaration rd = (RecordDeclaration) c.decl;
            for (Parameter p : rd.getParameters()) {
                Type t = resolveType(p.getType(), c, Map.of());
                c.fields.put(p.getNameAsString(), new FieldInfo(p.getNameAsString(), c, t, false,
                                true, null));
            }
        }
        if (c.isEnum) {
            ClassT self = c.selfType();
            for (String constant : c.enumConstants) {
                c.fields.put(constant, new FieldInfo(constant, c, self, true, true, null));
            }
            c.fields.put("$VALUES", new FieldInfo("$VALUES", c, new ArrayT(self), true, true, null));
        }
        if (c.outer != null && c.decl instanceof ClassOrInterfaceDeclaration cd && !cd.isStatic()
                        && !cd.isInterface() && !c.outer.isInterface) {
            c.fields.put("this$0", new FieldInfo("this$0", c, c.outer.selfType(), false, true,
                            null));
        }
        if (c.constructors.isEmpty() && !c.isInterface) {
            MethodInfo m = new MethodInfo("<init>", c, true);
            m.returnType = Prim.VOID;
            m.body = new com.github.javaparser.ast.stmt.BlockStmt();
            m.decl = c.decl;
            c.constructors.add(m);
        }
    }

    private MethodInfo method(ClassInfo c, CallableDeclaration<?> cd) {
        boolean ctor = cd instanceof ConstructorDeclaration;
        MethodInfo m = new MethodInfo(ctor ? "<init>" : cd.getNameAsString(), c, ctor);
        m.decl = cd;
        for (TypeParameter tp : cd.getTypeParameters()) {
            m.typeParams.add(tp.getNameAsString());
        }
        Map<String, TypeParameter> tps = new HashMap<>();
        for (TypeParameter tp : cd.getTypeParameters()) {
            tps.put(tp.getNameAsString(), tp);
        }
        m.typeParamDecls = tps;
        for (Parameter p : cd.getParameters()) {
            Type t = resolveType(p.getType(), c, tps);
            if (p.isVarArgs()) {
                t = new ArrayT(t);
                m.isVarArgs = true;
            }
            m.params.add(t);
            m.paramNames.add(p.getNameAsString());
        }
        if (cd instanceof MethodDeclaration md) {
            m.returnType = resolveType(md.getType(), c, tps);
            m.isStatic = md.isStatic();
            m.body = md.getBody().orElse(null);
            m.isAbstract = m.body == null && !md.isNative();
            if (c.isInterface && !md.isDefault() && !md.isStatic()) {
                m.isAbstract = m.body == null;
            }
        } else {
            m.returnType = Prim.VOID;
            m.body = ((ConstructorDeclaration) cd).getBody();
        }
        return m;
    }

    private final Set<String> resolvingBounds = new HashSet<>();

    Type resolveType(com.github.javaparser.ast.type.Type t, ClassInfo ctx,
                    Map<String, TypeParameter> methodTps) {
        if (t.isPrimitiveType()) {
            return switch (t.asPrimitiveType().getType()) {
                case BOOLEAN -> Prim.BOOLEAN;
                case BYTE -> Prim.BYTE;
                case SHORT -> Prim.SHORT;
                case CHAR -> Prim.CHAR;
                case INT -> Prim.INT;
                case LONG -> Prim.LONG;
                case FLOAT -> Prim.FLOAT;
                case DOUBLE -> Prim.DOUBLE;
            };
        }
        if (t.isVoidType()) {
            return Prim.VOID;
        }
        if (t.isArrayType()) {
            return new ArrayT(resolveType(t.asArrayType().getComponentType(), ctx, methodTps));
        }
        if (t.isWildcardType()) {
            var w = t.asWildcardType();
            if (w.getExtendedType().isPresent()) {
                return resolveType(w.getExtendedType().get(), ctx, methodTps);
            }
            return new ClassT(objectClass, List.of());
        }
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType ct = t.asClassOrInterfaceType();
            String name = ct.getNameWithScope();
            if (ct.getScope().isEmpty()) {
                TypeParameter tp = methodTps.get(name);
                if (tp == null) {
                    tp = classTypeParam(ctx, name);
                }
                if (tp != null || methodTps.containsKey(name)) {
                    Type bound = null;
                    if (tp != null && !tp.getTypeBound().isEmpty() && resolvingBounds.add(name)) {
                        try {
                            bound = resolveType(tp.getTypeBound().get(0), ctx, methodTps);
                        } finally {
                            resolvingBounds.remove(name);
                        }
                    }
                    return new TypeVar(name, bound);
                }
            }
            ClassInfo c = lookupClass(name, ctx);
            if (c == null) {
                c = lookupClass(ct.getNameAsString(), ctx);
            }
            if (c == null) {
                throw new CompileException(t, "Unknown type " + name);
            }
            List<Type> args = new ArrayList<>();
            ct.getTypeArguments().ifPresent(as -> {
                for (var a : as) {
                    args.add(resolveType(a, ctx, methodTps));
                }
            });
            return new ClassT(c, args);
        }
        if (t.isVarType()) {
            return null;
        }
        throw new CompileException(t, "Unsupported type " + t);
    }

    private static TypeParameter classTypeParam(ClassInfo ctx, String name) {
        for (ClassInfo c = ctx; c != null; c = c.outer) {
            if (c.decl instanceof com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters<?> nt) {
                for (TypeParameter tp : nt.getTypeParameters()) {
                    if (tp.getNameAsString().equals(name)) {
                        return tp;
                    }
                }
            }
        }
        return null;
    }

    /** Object layout: header word, superclass fields, own fields. */
    private void layout(ClassInfo c) {
        if (c.instanceWords >= 0) {
            return;
        }
        int next = 1;
        if (c.superclass() != null) {
            layout(c.superclass());
            next = c.superclass().instanceWords;
        }
        for (FieldInfo f : c.fields.values()) {
            if (!f.isStatic) {
                f.slot = next++;
            }
        }
        c.instanceWords = next;
    }

    // ------------------------------------------------------------------ desugaring

    private static void desugarEnum(EnumDeclaration en) {
        String n = en.getNameAsString();
        en.addMember(member("public static " + n + "[] values() { return $VALUES; }"));
        en.addMember(member("public static " + n + " valueOf(String name) { for (" + n
                        + " e : $VALUES) { if (e.name().equals(name)) { return e; } } throw new "
                        + "IllegalArgumentException(\"No enum constant " + n + ".\" + name); }"));
    }

    private static void desugarRecord(RecordDeclaration rd) {
        NodeList<Parameter> ps = rd.getParameters();
        boolean hasCanonical = rd.getConstructors().stream().anyMatch(
                        c -> c.getParameters().size() == ps.size());
        StringBuilder assign = new StringBuilder();
        for (Parameter p : ps) {
            assign.append("this.").append(p.getName()).append(" = ").append(p.getName())
                            .append(';');
        }
        // compact constructors: run their body, then assign the fields
        for (var compact : List.copyOf(rd.getCompactConstructors())) {
            StringBuilder params = new StringBuilder();
            for (Parameter p : ps) {
                params.append(params.isEmpty() ? "" : ", ").append(p);
            }
            String body = compact.getBody().toString();
            body = body.substring(1, body.length() - 1);
            rd.addMember(member("public " + rd.getNameAsString() + "(" + params + ") {" + body
                            + assign + "}"));
            hasCanonical = true;
            compact.remove();
        }
        if (!hasCanonical) {
            StringBuilder params = new StringBuilder();
            for (Parameter p : ps) {
                params.append(params.isEmpty() ? "" : ", ").append(p);
            }
            rd.addMember(member("public " + rd.getNameAsString() + "(" + params + ") {" + assign
                            + "}"));
        }
        StringBuilder eq = new StringBuilder("true");
        StringBuilder hash = new StringBuilder("0");
        StringBuilder str = new StringBuilder("\"" + rd.getNameAsString() + "[\"");
        boolean first = true;
        for (Parameter p : ps) {
            String f = p.getNameAsString();
            boolean prim = p.getType().isPrimitiveType();
            if (rd.getMethodsBySignature(f).isEmpty()) {
                rd.addMember(member("public " + p.getType() + " " + f + "() { return " + f + "; }"));
            }
            eq.append(" && ").append(prim ? f + " == o." + f : "Objects.equals(" + f + ", o." + f + ")");
            hash.append(" * 31 + ").append(prim ? "(" + (p.getType().asString().equals("boolean")
                            ? f + " ? 1 : 0" : "(int) " + f) + ")" : "Objects.hashCode(" + f + ")");
            str.append(first ? " + \"" : " + \", ").append(f).append("=\" + ").append(f);
            first = false;
        }
        str.append(" + \"]\"");
        String n = rd.getNameAsString();
        if (rd.getMethodsBySignature("equals", "Object").isEmpty()) {
            rd.addMember(member("public boolean equals(Object other) { if (!(other instanceof " + n
                            + ")) { return false; } " + n + " o = (" + n + ") other; return " + eq
                            + "; }"));
        }
        if (rd.getMethodsBySignature("hashCode").isEmpty()) {
            rd.addMember(member("public int hashCode() { return " + hash + "; }"));
        }
        if (rd.getMethodsBySignature("toString").isEmpty()) {
            rd.addMember(member("public String toString() { return " + str + "; }"));
        }
    }

    private static BodyDeclaration<?> member(String code) {
        return com.github.javaparser.StaticJavaParser.parseBodyDeclaration(code);
    }

    // ------------------------------------------------------------------ static data

    /** Reserves {@code words} zero words in the static image; returns the distance. */
    int allocStatic(int words) {
        int d = image.size();
        for (int i = 0; i < words; i++) {
            image.add(0L);
        }
        return d;
    }

    private final Map<FieldInfo, Integer> staticSlots = new IdentityHashMap<>();

    /** Heap distance of a static field, allocated (with its constant initial value) on first
     * use. */
    /** Static slot holding the exception that is being thrown ({@code Runtime.pending}). */
    int pendingSlot() {
        return staticSlot(runtimeClass("Runtime").fields.get("pending"));
    }

    int staticSlot(FieldInfo f) {
        Integer slot = staticSlots.get(f);
        if (slot != null) {
            return slot;
        }
        int d = allocStatic(1);
        staticSlots.put(f, d);
        if (f.init != null) {
            if (isHeapStartCall(f.init)) {
                deferredHeapStart.put(f, d);
            } else if (imageObjectClass(f.init) != null) {
                image.set(d, (long) imageObject((ObjectCreationExpr) f.init));
            } else {
                Object k = ConstEval.eval(f.init, this, f.owner);
                if (k != null) {
                    image.set(d, ConstEval.convertWord(k, f.type, this));
                }
            }
        }
        return d;
    }

    /** Whether a static field's initializer is folded into the static image. */
    boolean hasStaticImageInit(FieldInfo f) {
        return f.init != null && (isHeapStartCall(f.init)
                        || f.init instanceof com.github.javaparser.ast.expr.NullLiteralExpr
                        || imageObjectClass(f.init) != null
                        || ConstEval.eval(f.init, this, f.owner) != null);
    }

    /**
     * The wrapper class of an initializer like {@code new Boolean(true)}: a runtime class with
     * a single {@code value} field built from one constant, which can live in the static image.
     */
    private ClassInfo imageObjectClass(Node init) {
        if (!(init instanceof ObjectCreationExpr oc) || oc.getArguments().size() != 1
                        || oc.getAnonymousClassBody().isPresent()) {
            return null;
        }
        ClassInfo c = runtimeClasses.get(oc.getType().getNameAsString());
        if (c == null || !List.of("Boolean", "Integer", "Character", "Long", "Short", "Byte")
                        .contains(c.name)) {
            return null;
        }
        return ConstEval.eval(oc.getArguments().get(0), this, c) != null ? c : null;
    }

    private int imageObject(ObjectCreationExpr oc) {
        ClassInfo c = imageObjectClass(oc);
        Object k = ConstEval.eval(oc.getArguments().get(0), this, c);
        instantiated.add(c);
        int obj = allocStatic(c.instanceWords);
        image.set(obj, (long) c.classId);
        image.set(obj + c.fields.get("value").slot, constWord(k));
        return obj;
    }

    private static boolean isHeapStartCall(Node init) {
        return init instanceof com.github.javaparser.ast.expr.MethodCallExpr mc
                        && mc.getNameAsString().equals("heapStart");
    }

    long constWord(Object k) {
        return switch (k) {
            case Long l -> l;
            case Integer i -> i;
            case Character ch -> ch;
            case Boolean b -> b ? 1 : 0;
            case Float x -> Float.floatToRawIntBits(x) & 0xFFFFFFFFL;
            case Double x -> doubleIsFloat() ? Float.floatToRawIntBits((float) (double) x)
                            & 0xFFFFFFFFL : Double.doubleToRawLongBits(x);
            case String s -> stringLiteral(s);
            default -> throw new IllegalStateException("not a constant: " + k);
        };
    }

    /** Interned string literal in the static image: a String object pointing to a char[]. */
    int stringLiteral(String s) {
        Integer known = literals.get(s);
        if (known != null) {
            return known;
        }
        instantiated.add(stringClass);
        int arr = allocStatic(s.length() + 1);
        image.set(arr, (long) s.length());
        for (int i = 0; i < s.length(); i++) {
            image.set(arr + 1 + i, (long) s.charAt(i));
        }
        int obj = allocStatic(stringClass.instanceWords);
        image.set(obj, (long) stringClass.classId);
        image.set(obj + stringClass.fields.get("value").slot, (long) arr);
        literals.put(s, obj);
        return obj;
    }

    // ------------------------------------------------------------------ functions

    Function functionOf(MethodInfo m) {
        if (m.ir == null) {
            if (!m.hasBody()) {
                throw new CompileException(m.decl, "Method " + m + " has no body");
            }
            int params = m.params.size() + (m.isStatic ? 0 : 1);
            String name = m.owner.name + "." + m.name;
            m.ir = new Function(name, params, m.returnType != Prim.VOID);
            program.functions.add(m.ir);
            pending.add(m);
        }
        return m.ir;
    }

    private void drain() {
        while (true) {
            while (!pending.isEmpty()) {
                MethodInfo m = pending.removeFirst();
                new Lowerer(this, m).lower();
            }
            boolean grew = false;
            for (Dispatcher d : List.copyOf(dispatchers.values())) {
                grew |= d.resolveTargets();
            }
            if (!grew && pending.isEmpty()) {
                return;
            }
        }
    }

    private Function buildClinit(List<CompilationUnit> userUnits) {
        List<ClassInfo> inits = new ArrayList<>();
        for (ClassInfo c : allClasses) {
            if (c.isRuntime) {
                for (Node n : c.staticInits) {
                    if (n instanceof VariableDeclarator v && hasStaticImageInit(c.fields.get(
                                    v.getNameAsString()))) {
                        continue;
                    }
                    throw new CompileException(n, "runtime library static initializers must be "
                                    + "constant");
                }
                continue;
            }
            boolean needed = c.isEnum;
            for (Node n : c.staticInits) {
                if (!(n instanceof VariableDeclarator v) || !hasStaticImageInit(c.fields.get(
                                v.getNameAsString()))) {
                    needed = true;
                }
            }
            if (needed) {
                inits.add(c);
            }
        }
        if (inits.isEmpty()) {
            return null;
        }
        inits = initOrder(inits);
        MethodInfo m = new MethodInfo("<clinit>", inits.getFirst(), false);
        m.isStatic = true;
        m.returnType = Prim.VOID;
        m.synthetic = null;
        m.body = new com.github.javaparser.ast.stmt.BlockStmt();
        m.decl = inits.getFirst().decl;
        Function f = new Function("$clinit", 0, false);
        m.ir = f;
        program.functions.add(f);
        new Lowerer(this, m).lowerStaticInit(inits);
        return f;
    }

    /**
     * Orders static initialization so that a class runs after the classes its static
     * initializers mention. The JVM initializes a class lazily on first use; running the
     * dependencies first gives the same result for acyclic programs (e.g. an outer class whose
     * static block fills a map with constants of its nested enum).
     */
    private List<ClassInfo> initOrder(List<ClassInfo> inits) {
        Set<ClassInfo> pending = new HashSet<>(inits);
        Set<ClassInfo> visited = new HashSet<>();
        List<ClassInfo> out = new ArrayList<>();
        for (ClassInfo c : inits) {
            visitInit(c, pending, visited, out);
        }
        return out;
    }

    private void visitInit(ClassInfo c, Set<ClassInfo> pending, Set<ClassInfo> visited,
                    List<ClassInfo> out) {
        if (!visited.add(c)) {
            return;
        }
        List<Node> roots = new ArrayList<>(c.staticInits);
        if (c.isEnum) {
            roots.addAll(enumEntries(c));
        }
        for (Node root : roots) {
            List<ClassInfo> deps = new ArrayList<>();
            for (var n : root.findAll(com.github.javaparser.ast.expr.NameExpr.class)) {
                deps.add(lookupClass(n.getNameAsString(), c));
                // statically imported members name their class only through the import
                deps.addAll(staticImports(c, n.getNameAsString()));
            }
            root.findAll(ClassOrInterfaceType.class)
                            .forEach(t -> deps.add(lookupClass(t.getNameAsString(), c)));
            for (ClassInfo d : deps) {
                if (d != null && d != c && pending.contains(d)) {
                    visitInit(d, pending, visited, out);
                }
            }
        }
        out.add(c);
    }

    private Function printIntFn;

    Function printIntFunction() {
        if (printIntFn == null) {
            printIntFn = Function.builtin(de.zorro909.brainfuck.compiler.ir.Builtin.PRINT_INT);
        }
        return printIntFn;
    }

    /** Constructors that do nothing: empty body, no initializers, trivial super chain. */
    boolean isTrivialConstructor(MethodInfo ctor) {
        if (ctor.owner == objectClass) {
            return true;
        }
        if (!ctor.params.isEmpty() || ctor.body == null || !ctor.body.getStatements().isEmpty()
                        || !ctor.owner.instanceInits.isEmpty() || ctor.initOnly) {
            return false;
        }
        ClassInfo sup = ctor.owner.superclass();
        if (sup == null) {
            return true;
        }
        for (MethodInfo s : sup.constructors) {
            if (s.params.isEmpty()) {
                return isTrivialConstructor(s);
            }
        }
        return false;
    }

    String freshLambdaName(ClassInfo outer) {
        return outer.name + "$" + (++lambdaCounter);
    }

    /** Registers a synthetic class (lambdas, anonymous classes) after the initial setup. */
    ClassInfo addSyntheticClass(ClassInfo c) {
        (c.isRuntime ? runtimeClasses : userClasses).put(c.name, c);
        allClasses.add(c);
        classOfDecl.put(c.decl, c);
        c.classId = allClasses.size();
        if (c.superclass() != null) {
            c.superclass().subclasses.add(c);
        }
        for (ClassT i : c.interfaces) {
            i.cls().subclasses.add(c);
        }
        layout(c);
        return c;
    }

    // ------------------------------------------------------------------ types & members

    /** Type-variable environment of {@code target} as seen through {@code t}. */
    Map<String, Type> envFor(Type t, ClassInfo target) {
        Map<String, Type> env = new HashMap<>();
        if (t instanceof ClassT ct) {
            collectEnv(ct, target, env, new HashSet<>());
        }
        return env;
    }

    private boolean collectEnv(ClassT ct, ClassInfo target, Map<String, Type> env,
                    Set<ClassInfo> seen) {
        if (!seen.add(ct.cls())) {
            return false;
        }
        Map<String, Type> local = new HashMap<>();
        ClassInfo c = ct.cls();
        for (int i = 0; i < c.typeParams.size() && i < ct.args().size(); i++) {
            local.put(c.typeParams.get(i), ct.args().get(i));
        }
        if (c == target) {
            env.putAll(local);
            return true;
        }
        List<ClassT> supers = new ArrayList<>();
        if (c.superType != null) {
            supers.add(c.superType);
        }
        supers.addAll(c.interfaces);
        for (ClassT s : supers) {
            ClassT substituted = (ClassT) Type.subst(s, local);
            if (collectEnv(substituted, target, env, seen)) {
                return true;
            }
        }
        return false;
    }

    /** Erasure of a type for override matching. */
    Type erase(Type t) {
        return switch (t) {
            case TypeVar v -> v.bound() != null ? erase(v.bound()) : new ClassT(objectClass,
                            List.of());
            case ClassT c -> new ClassT(c.cls(), List.of());
            case ArrayT a -> new ArrayT(erase(a.elem()));
            default -> t;
        };
    }

    boolean overrides(MethodInfo sub, MethodInfo base) {
        if (!sub.name.equals(base.name) || sub.params.size() != base.params.size()
                        || sub.isStatic || base.isStatic || sub.isConstructor) {
            return false;
        }
        for (int i = 0; i < sub.params.size(); i++) {
            Type a = erase(sub.params.get(i));
            Type b = base.params.get(i);
            if (containsTypeVar(b)) {
                if (!a.isReference() && !(b instanceof TypeVar)) {
                    return false;
                }
                if (a.isReference() != erase(b).isReference()) {
                    return false;
                }
                continue;
            }
            if (!a.equals(erase(b))) {
                return false;
            }
        }
        return true;
    }

    static boolean containsTypeVar(Type t) {
        return switch (t) {
            case TypeVar v -> true;
            case ArrayT a -> containsTypeVar(a.elem());
            case ClassT c -> c.args().stream().anyMatch(Frontend::containsTypeVar);
            default -> false;
        };
    }

    /** The implementation of {@code m} that runs for an object of exact class {@code c}. */
    MethodInfo implementationIn(ClassInfo c, MethodInfo m) {
        for (ClassInfo k = c; k != null; k = k.superclass()) {
            for (MethodInfo cand : k.methods) {
                if ((cand == m || overrides(cand, m)) && cand.hasBody() && !cand.isAbstract) {
                    return cand;
                }
            }
        }
        // default methods
        Deque<ClassInfo> work = new ArrayDeque<>();
        for (ClassInfo k = c; k != null; k = k.superclass()) {
            k.interfaces.forEach(i -> work.add(i.cls()));
        }
        Set<ClassInfo> seen = new HashSet<>();
        while (!work.isEmpty()) {
            ClassInfo i = work.removeFirst();
            if (!seen.add(i)) {
                continue;
            }
            for (MethodInfo cand : i.methods) {
                if ((cand == m || overrides(cand, m)) && cand.hasBody() && !cand.isStatic) {
                    return cand;
                }
            }
            i.interfaces.forEach(s -> work.add(s.cls()));
        }
        return null;
    }

    /** All classes that are subtypes of {@code c} (including itself). */
    List<ClassInfo> subtypes(ClassInfo c) {
        List<ClassInfo> out = new ArrayList<>();
        Deque<ClassInfo> work = new ArrayDeque<>(List.of(c));
        Set<ClassInfo> seen = new HashSet<>();
        while (!work.isEmpty()) {
            ClassInfo k = work.removeFirst();
            if (seen.add(k)) {
                out.add(k);
                work.addAll(k.subclasses);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ virtual dispatch

    /** Function to call for a virtual invocation of {@code m}. */
    Function virtualCall(MethodInfo m, Block site) {
        Dispatcher d = dispatchers.computeIfAbsent(m, Dispatcher::new);
        d.sites.add(site);
        return d.fn;
    }

    private void finishDispatchers() {
        for (Dispatcher d : dispatchers.values()) {
            d.generate();
        }
        if (classNameFn != null) {
            generateClassName();
        }
    }

    final class Dispatcher {
        final MethodInfo method;
        final Function fn;
        final List<Block> sites = new ArrayList<>();
        final Map<ClassInfo, MethodInfo> targets = new LinkedHashMap<>();

        Dispatcher(MethodInfo method) {
            this.method = method;
            this.fn = new Function(method.owner.name + "." + method.name + "$virtual",
                            method.params.size() + 1, method.returnType != Prim.VOID);
        }

        boolean resolveTargets() {
            boolean grew = false;
            for (ClassInfo c : List.copyOf(instantiated)) {
                if (targets.containsKey(c) || !c.isSubclassOf(method.owner)) {
                    continue;
                }
                MethodInfo impl = implementationIn(c, method);
                if (impl == null) {
                    continue;
                }
                targets.put(c, impl);
                functionOf(impl);
                grew = true;
            }
            return grew;
        }

        void generate() {
            Set<MethodInfo> impls = new LinkedHashSet<>(targets.values());
            if (impls.size() <= 1) {
                Function target = impls.isEmpty() ? null : impls.iterator().next().ir;
                for (Block site : sites) {
                    Terminator.Call c = (Terminator.Call) site.terminator;
                    if (target == null) {
                        // receiver can never be instantiated: the call is unreachable
                        site.terminator = new Terminator.Halt();
                    } else {
                        site.terminator = new Terminator.Call(target, c.args(), c.result(),
                                        c.next(), c.handler());
                    }
                }
                return;
            }
            program.functions.add(fn);
            Block entry = fn.newBlock("dispatch");
            fn.entry = entry;
            Reg cid = fn.newReg();
            entry.insns.add(new Insn.Load(cid, fn.param(0), Operand.imm(0), 0));
            List<Operand> args = new ArrayList<>();
            for (int i = 0; i < fn.params; i++) {
                args.add(fn.param(i));
            }
            Block cur = entry;
            List<MethodInfo> order = new ArrayList<>(impls);
            for (int k = 0; k < order.size(); k++) {
                MethodInfo impl = order.get(k);
                Block call = fn.newBlock("call");
                Reg result = fn.returnsValue ? fn.newReg() : null;
                Block ret = fn.newBlock("ret");
                call.terminator = new Terminator.Call(impl.ir, args, result, ret);
                ret.terminator = new Terminator.Return(result);
                if (k == order.size() - 1) {
                    cur.terminator = new Terminator.Jump(call);
                    break;
                }
                for (var e : targets.entrySet()) {
                    if (e.getValue() != impl) {
                        continue;
                    }
                    Reg eq = fn.newReg();
                    cur.insns.add(new Insn.Binary(BinOp.EQ, eq, cid,
                                    Operand.imm(e.getKey().classId)));
                    Block next = fn.newBlock("test");
                    cur.terminator = new Terminator.Branch(eq, call, next);
                    cur = next;
                }
            }
        }
    }

    /** {@code Bf.className(o)}: a function mapping class ids to name literals. */
    Function classNameFunction() {
        if (classNameFn == null) {
            classNameFn = new Function("$className", 1, true);
            program.functions.add(classNameFn);
        }
        return classNameFn;
    }

    private void generateClassName() {
        Function fn = classNameFn;
        Block entry = fn.newBlock("entry");
        fn.entry = entry;
        Reg cid = fn.newReg();
        entry.insns.add(new Insn.Load(cid, fn.param(0), Operand.imm(0), 0));
        Block cur = entry;
        for (ClassInfo c : instantiated) {
            Reg eq = fn.newReg();
            cur.insns.add(new Insn.Binary(BinOp.EQ, eq, cid, Operand.imm(c.classId)));
            Block hit = fn.newBlock("hit");
            hit.terminator = new Terminator.Return(Operand.imm(stringLiteral(javaName(c))));
            Block next = fn.newBlock("next");
            cur.terminator = new Terminator.Branch(eq, hit, next);
            cur = next;
        }
        cur.terminator = new Terminator.Return(Operand.imm(stringLiteral("Object")));
    }

    /** Fully qualified name as the JVM would print it. */
    String javaName(ClassInfo c) {
        if (!c.isRuntime) {
            return c.name.replace('.', '$');
        }
        String simple = c.name;
        if (simple.equals("IOException")) {
            return "java.io.IOException";
        }
        return (JAVA_UTIL.contains(simple) ? "java.util." : "java.lang.") + simple;
    }

    // ------------------------------------------------------------------ helpers

    MethodInfo runtimeMethod(String cls, String name, int arity) {
        for (MethodInfo m : runtimeClass(cls).methods) {
            if (m.name.equals(name) && m.params.size() == arity) {
                return m;
            }
        }
        throw new IllegalStateException("runtime method missing: " + cls + "." + name);
    }

    MethodInfo runtimeMethod(String cls, String name, Type param) {
        for (MethodInfo m : runtimeClass(cls).methods) {
            if (m.name.equals(name) && m.params.size() == 1 && m.params.getFirst().equals(param)) {
                return m;
            }
        }
        throw new IllegalStateException("runtime method missing: " + cls + "." + name + "("
                        + param + ")");
    }

    ClassT classType(String runtimeName) {
        return new ClassT(runtimeClass(runtimeName), List.of());
    }

    static boolean hasModifier(Node n, Modifier.Keyword k) {
        return n instanceof com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> m
                        && m.hasModifier(k);
    }

    static Collection<EnumConstantDeclaration> enumEntries(ClassInfo c) {
        return ((EnumDeclaration) c.decl).getEntries();
    }
}
