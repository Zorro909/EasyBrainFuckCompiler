package de.zorro909.brainfuck.compiler.frontend;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Static types of the supported Java subset. Every value is one machine word at run time. */
sealed interface Type {

    enum Prim implements Type {
        BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE, VOID;

        boolean isNumeric() {
            return this != BOOLEAN && this != VOID;
        }

        boolean isFloating() {
            return this == FLOAT || this == DOUBLE;
        }

        /** Binary numeric promotion. */
        static Prim promote(Prim a, Prim b) {
            if (a == DOUBLE || b == DOUBLE) {
                return DOUBLE;
            }
            if (a == FLOAT || b == FLOAT) {
                return FLOAT;
            }
            return a == LONG || b == LONG ? LONG : INT;
        }

        /** Widening primitive conversion (JLS 5.1.2) or identity. */
        boolean widensTo(Prim to) {
            if (this == to) {
                return true;
            }
            return switch (this) {
                case BYTE -> to == SHORT || to == INT || to == LONG || to.isFloating();
                case SHORT, CHAR -> to == INT || to == LONG || to.isFloating();
                case INT -> to == LONG || to.isFloating();
                case LONG -> to.isFloating();
                case FLOAT -> to == DOUBLE;
                default -> false;
            };
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** The type of {@code null}. */
    enum Null implements Type {
        NULL;

        @Override
        public String toString() {
            return "null";
        }
    }

    record ClassT(ClassInfo cls, List<Type> args) implements Type {
        @Override
        public String toString() {
            return args.isEmpty() ? cls.name
                            : cls.name + args.stream().map(Object::toString)
                                            .collect(Collectors.joining(",", "<", ">"));
        }
    }

    record ArrayT(Type elem) implements Type {
        @Override
        public String toString() {
            return elem + "[]";
        }
    }

    /** A type variable; erased to its bound for code generation. */
    record TypeVar(String name, Type bound) implements Type {
        @Override
        public String toString() {
            return name;
        }
    }

    default boolean isReference() {
        return !(this instanceof Prim);
    }

    default boolean isPrim(Prim p) {
        return this == p;
    }

    /** Replaces type variables using {@code env}. */
    static Type subst(Type t, Map<String, Type> env) {
        if (env.isEmpty()) {
            return t;
        }
        return switch (t) {
            case TypeVar v -> {
                Type r = env.get(v.name());
                // keep the bound when a type variable is only renamed to itself
                if (r == null || r instanceof TypeVar rv && rv.name().equals(v.name())
                                && rv.bound() == null) {
                    yield v;
                }
                yield r;
            }
            case ArrayT a -> new ArrayT(subst(a.elem(), env));
            case ClassT c -> c.args().isEmpty() ? c
                            : new ClassT(c.cls(), c.args().stream().map(x -> subst(x, env)).toList());
            default -> t;
        };
    }
}
