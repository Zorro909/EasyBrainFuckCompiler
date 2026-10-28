package de.zorro909.brainfuck.compiler.frontend;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import de.zorro909.brainfuck.compiler.ir.Function;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class, interface, enum or record of the program (user code or runtime library).
 *
 * <p>Object layout on the heap: word 0 holds {@link #classId}, followed by the instance fields
 * of all superclasses and then this class.
 */
final class ClassInfo {

    final String name;
    final TypeDeclaration<?> decl;
    final boolean isRuntime;
    boolean isInterface;
    boolean isAbstract;
    boolean isEnum;
    boolean isRecord;
    final List<String> typeParams = new ArrayList<>();
    Type.ClassT superType;
    final List<Type.ClassT> interfaces = new ArrayList<>();
    final Map<String, FieldInfo> fields = new LinkedHashMap<>();
    final List<MethodInfo> methods = new ArrayList<>();
    final List<MethodInfo> constructors = new ArrayList<>();
    /** Instance initializer blocks and field initializers, in declaration order. */
    final List<Node> instanceInits = new ArrayList<>();
    final List<Node> staticInits = new ArrayList<>();
    final List<ClassInfo> subclasses = new ArrayList<>();
    final List<String> enumConstants = new ArrayList<>();
    /** Outer class for nested declarations (name resolution falls back to it). */
    ClassInfo outer;
    int classId;
    int instanceWords = -1;

    ClassInfo(String name, TypeDeclaration<?> decl, boolean isRuntime) {
        this.name = name;
        this.decl = decl;
        this.isRuntime = isRuntime;
    }

    ClassInfo superclass() {
        return superType == null ? null : superType.cls();
    }

    boolean isSubclassOf(ClassInfo other) {
        if (this == other) {
            return true;
        }
        if (superType != null && superType.cls().isSubclassOf(other)) {
            return true;
        }
        for (Type.ClassT i : interfaces) {
            if (i.cls().isSubclassOf(other)) {
                return true;
            }
        }
        return false;
    }

    /** Field lookup including superclasses and interfaces (for constants). */
    FieldInfo findField(String fieldName) {
        FieldInfo f = fields.get(fieldName);
        if (f != null) {
            return f;
        }
        if (superType != null) {
            f = superType.cls().findField(fieldName);
            if (f != null) {
                return f;
            }
        }
        for (Type.ClassT i : interfaces) {
            f = i.cls().findField(fieldName);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    Type.ClassT selfType() {
        List<Type> args = new ArrayList<>();
        for (String p : typeParams) {
            args.add(new Type.TypeVar(p, null));
        }
        return new Type.ClassT(this, args);
    }

    @Override
    public String toString() {
        return name;
    }

    static final class FieldInfo {
        final String name;
        final ClassInfo owner;
        final Type type;
        final boolean isStatic;
        final boolean isFinal;
        final Expression init;
        /** Instance fields: word offset in the object. Static fields: heap distance. */
        int slot = -1;
        /** Compile-time constant value (static final primitives / strings), or null. */
        Object constant;

        FieldInfo(String name, ClassInfo owner, Type type, boolean isStatic, boolean isFinal,
                        Expression init) {
            this.name = name;
            this.owner = owner;
            this.type = type;
            this.isStatic = isStatic;
            this.isFinal = isFinal;
            this.init = init;
        }

        @Override
        public String toString() {
            return owner.name + "." + name;
        }
    }

    static final class MethodInfo {
        final String name;
        final ClassInfo owner;
        final List<String> typeParams = new ArrayList<>();
        Map<String, com.github.javaparser.ast.type.TypeParameter> typeParamDecls = Map.of();
        final List<Type> params = new ArrayList<>();
        final List<String> paramNames = new ArrayList<>();
        Type returnType;
        boolean isStatic;
        boolean isAbstract;
        boolean isVarArgs;
        final boolean isConstructor;
        /** Method body; null for abstract/interface methods and intrinsics. */
        BlockStmt body;
        Node decl;
        /** Lambda / synthetic bodies: an expression body instead of a block. */
        Expression exprBody;
        Function ir;
        /** Synthetic method generator for records/enums, or null. */
        Synthetic synthetic;
        /** Constructor that only runs instance initializers (anonymous classes). */
        boolean initOnly;

        MethodInfo(String name, ClassInfo owner, boolean isConstructor) {
            this.name = name;
            this.owner = owner;
            this.isConstructor = isConstructor;
        }

        boolean hasBody() {
            return body != null || exprBody != null || synthetic != null;
        }

        String signature() {
            return owner.name + "." + name + params;
        }

        @Override
        public String toString() {
            return signature();
        }
    }

    /** Compiler-generated method bodies. */
    enum Synthetic {
        RECORD_CONSTRUCTOR, RECORD_ACCESSOR, RECORD_EQUALS, RECORD_HASHCODE, RECORD_TOSTRING,
        ENUM_VALUES, ENUM_VALUE_OF
    }
}
