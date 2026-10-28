package de.zorro909.brainfuck.compiler.frontend;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import de.zorro909.brainfuck.compiler.frontend.ClassInfo.FieldInfo;
import java.util.HashSet;
import java.util.Set;

/**
 * Compile-time constant expressions (JLS 15.29): literals, operators on constants and
 * references to constant {@code static final} fields. Integral results are {@link Long}s
 * truncated to the target word size, chars are {@link Character}s, floating point values are
 * {@link Float}s and {@link Double}s. Where {@code double} is binary32 (words below 8 bytes),
 * double constants are kept in float precision and computed in float arithmetic, exactly like
 * the generated code does.
 */
final class ConstEval {

    private static final ThreadLocal<Set<FieldInfo>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    private ConstEval() {
    }

    /** The constant value of {@code e}, or null if it is not a constant expression. */
    static Object eval(Expression e, Frontend fe, ClassInfo ctx) {
        try {
            return new ConstEval().go(e, fe, ctx);
        } catch (NotConstant nc) {
            return null;
        }
    }

    /** Constant value of a {@code static final} field, or null. */
    static Object fieldConstant(FieldInfo f, Frontend fe) {
        if (!f.isStatic || !f.isFinal || f.init == null) {
            return null;
        }
        if (f.constant != null) {
            return f.constant;
        }
        if (!ACTIVE.get().add(f)) {
            return null;
        }
        try {
            Object v = eval(f.init, fe, f.owner);
            if (v != null) {
                try {
                    v = convert(v, f.type, fe);
                } catch (NotConstant nc) {
                    v = null;
                }
            }
            f.constant = v;
            return v;
        } finally {
            ACTIVE.get().remove(f);
        }
    }

    private static final class NotConstant extends RuntimeException {
        NotConstant() {
            super(null, null, false, false);
        }
    }

    private Object go(Expression e, Frontend fe, ClassInfo ctx) {
        switch (e) {
            case IntegerLiteralExpr i -> {
                return fe.wordBits >= 64 ? (Long) i.asNumber().longValue()
                                : trunc(i.asNumber().longValue(), fe);
            }
            case LongLiteralExpr l -> {
                return trunc(l.asNumber().longValue(), fe);
            }
            case DoubleLiteralExpr d -> {
                String text = d.getValue().replace("_", "");
                char last = Character.toLowerCase(text.charAt(text.length() - 1));
                if (last == 'f') {
                    return Float.parseFloat(text);
                }
                return fe.doubleIsFloat() ? (double) Float.parseFloat(text)
                                : Double.parseDouble(text);
            }
            case CharLiteralExpr c -> {
                return c.asChar();
            }
            case BooleanLiteralExpr b -> {
                return b.getValue();
            }
            case StringLiteralExpr s -> {
                return s.asString();
            }
            case TextBlockLiteralExpr t -> {
                return t.asString();
            }
            case EnclosedExpr en -> {
                return go(en.getInner(), fe, ctx);
            }
            case NameExpr n -> {
                FieldInfo f = ctx == null ? null : findStatic(ctx, n.getNameAsString());
                if (f == null && ctx != null) {
                    for (ClassInfo c : fe.staticImports(ctx, n.getNameAsString())) {
                        FieldInfo g = c.findField(n.getNameAsString());
                        if (g != null && g.isStatic) {
                            f = g;
                            break;
                        }
                    }
                }
                if (f == null) {
                    throw new NotConstant();
                }
                Object v = fieldConstant(f, fe);
                if (v == null) {
                    throw new NotConstant();
                }
                return v;
            }
            case FieldAccessExpr fa -> {
                ClassInfo c = fe.lookupClass(fa.getScope().toString(), ctx);
                if (c == null) {
                    throw new NotConstant();
                }
                FieldInfo f = c.findField(fa.getNameAsString());
                Object v = f == null ? null : fieldConstant(f, fe);
                if (v == null) {
                    throw new NotConstant();
                }
                return v;
            }
            case MethodCallExpr mc when mc.getScope().map(s -> s.toString().equals("Bf"))
                            .orElse(false) && mc.getArguments().isEmpty() -> {
                long max = fe.wordBits >= 64 ? Long.MAX_VALUE : (1L << (fe.wordBits - 1)) - 1;
                boolean single = fe.doubleIsFloat();
                return switch (mc.getNameAsString()) {
                    case "maxInt" -> max;
                    case "minInt" -> -max - 1;
                    case "maxDouble" -> single ? (double) Float.MAX_VALUE : Double.MAX_VALUE;
                    case "minDouble" -> single ? (double) Float.MIN_VALUE : Double.MIN_VALUE;
                    case "minNormalDouble" -> single ? (double) Float.MIN_NORMAL
                                    : Double.MIN_NORMAL;
                    case "heapLimit" -> (long) fe.options.heapWords() - 1;
                    default -> throw new NotConstant();
                };
            }
            case UnaryExpr u -> {
                Object v = go(u.getExpression(), fe, ctx);
                if (v instanceof Double x) {
                    return switch (u.getOperator()) {
                        case PLUS -> x;
                        case MINUS -> -x;
                        default -> throw new NotConstant();
                    };
                }
                if (v instanceof Float x) {
                    return switch (u.getOperator()) {
                        case PLUS -> x;
                        case MINUS -> -x;
                        default -> throw new NotConstant();
                    };
                }
                return switch (u.getOperator()) {
                    case PLUS -> num(v);
                    case MINUS -> trunc(-num(v), fe);
                    case BITWISE_COMPLEMENT -> trunc(~num(v), fe);
                    case LOGICAL_COMPLEMENT -> !(Boolean) v;
                    default -> throw new NotConstant();
                };
            }
            case CastExpr c -> {
                Object v = go(c.getExpression(), fe, ctx);
                Type t = fe.resolveType(c.getType(), ctx, java.util.Map.of());
                return convert(v, t, fe);
            }
            case ConditionalExpr c -> {
                Object cond = go(c.getCondition(), fe, ctx);
                return (Boolean) cond ? go(c.getThenExpr(), fe, ctx) : go(c.getElseExpr(), fe, ctx);
            }
            case BinaryExpr b -> {
                Object l = go(b.getLeft(), fe, ctx);
                Object r = go(b.getRight(), fe, ctx);
                if (b.getOperator() == BinaryExpr.Operator.PLUS && (l instanceof String
                                || r instanceof String)) {
                    return str(l, fe) + str(r, fe);
                }
                if (l instanceof Boolean x && r instanceof Boolean y) {
                    return switch (b.getOperator()) {
                        case AND, BINARY_AND -> x && y;
                        case OR, BINARY_OR -> x || y;
                        case XOR -> x ^ y;
                        case EQUALS -> x == y;
                        case NOT_EQUALS -> x != y;
                        default -> throw new NotConstant();
                    };
                }
                if (l instanceof String || r instanceof String) {
                    throw new NotConstant();
                }
                if (l instanceof Double || r instanceof Double
                                || (l instanceof Float || r instanceof Float) && fe.doubleIsFloat()) {
                    boolean single = fe.doubleIsFloat() || !(l instanceof Double || r instanceof Double);
                    Object res = single ? floatOp(b.getOperator(), (float) dbl(l), (float) dbl(r))
                                    : doubleOp(b.getOperator(), dbl(l), dbl(r));
                    // in single precision, a double result stays a Double
                    if (res instanceof Float f && (l instanceof Double || r instanceof Double)) {
                        return (double) f;
                    }
                    return res;
                }
                if (l instanceof Float || r instanceof Float) {
                    return floatOp(b.getOperator(), (float) dbl(l), (float) dbl(r));
                }
                long x = num(l);
                long y = num(r);
                int bits = fe.wordBits;
                return switch (b.getOperator()) {
                    case PLUS -> trunc(x + y, fe);
                    case MINUS -> trunc(x - y, fe);
                    case MULTIPLY -> trunc(x * y, fe);
                    case DIVIDE -> {
                        if (y == 0) {
                            throw new NotConstant();
                        }
                        yield trunc(x / y, fe);
                    }
                    case REMAINDER -> {
                        if (y == 0) {
                            throw new NotConstant();
                        }
                        yield trunc(x % y, fe);
                    }
                    case BINARY_AND -> x & y;
                    case BINARY_OR -> x | y;
                    case XOR -> x ^ y;
                    case LEFT_SHIFT -> trunc(x << Math.floorMod(y, bits), fe);
                    case SIGNED_RIGHT_SHIFT -> x >> Math.floorMod(y, bits);
                    case UNSIGNED_RIGHT_SHIFT -> trunc((x & mask(bits)) >>> Math.floorMod(y, bits),
                                    fe);
                    case LESS -> x < y;
                    case LESS_EQUALS -> x <= y;
                    case GREATER -> x > y;
                    case GREATER_EQUALS -> x >= y;
                    case EQUALS -> x == y;
                    case NOT_EQUALS -> x != y;
                    default -> throw new NotConstant();
                };
            }
            default -> throw new NotConstant();
        }
    }

    private static FieldInfo findStatic(ClassInfo ctx, String name) {
        for (ClassInfo c = ctx; c != null; c = c.outer) {
            FieldInfo f = c.findField(name);
            if (f != null) {
                return f.isStatic ? f : null;
            }
        }
        return null;
    }

    private static long mask(int bits) {
        return bits >= 64 ? -1L : (1L << bits) - 1;
    }

    static long trunc(long v, Frontend fe) {
        int bits = fe.wordBits;
        return bits >= 64 ? v : (v << (64 - bits)) >> (64 - bits);
    }

    private static double dbl(Object v) {
        return switch (v) {
            case Double d -> d;
            case Float f -> f;
            case Long l -> l;
            case Character c -> c;
            default -> throw new NotConstant();
        };
    }

    private static Object floatOp(BinaryExpr.Operator op, float x, float y) {
        return switch (op) {
            case PLUS -> x + y;
            case MINUS -> x - y;
            case MULTIPLY -> x * y;
            case DIVIDE -> x / y;
            case REMAINDER -> x % y;
            case LESS -> x < y;
            case LESS_EQUALS -> x <= y;
            case GREATER -> x > y;
            case GREATER_EQUALS -> x >= y;
            case EQUALS -> x == y;
            case NOT_EQUALS -> x != y;
            default -> throw new NotConstant();
        };
    }

    private static Object doubleOp(BinaryExpr.Operator op, double x, double y) {
        return switch (op) {
            case PLUS -> x + y;
            case MINUS -> x - y;
            case MULTIPLY -> x * y;
            case DIVIDE -> x / y;
            case REMAINDER -> x % y;
            case LESS -> x < y;
            case LESS_EQUALS -> x <= y;
            case GREATER -> x > y;
            case GREATER_EQUALS -> x >= y;
            case EQUALS -> x == y;
            case NOT_EQUALS -> x != y;
            default -> throw new NotConstant();
        };
    }

    /** Narrowing of a floating constant to an integer word, saturating like the runtime. */
    private static long toWord(double d, Frontend fe) {
        return fe.wordBits >= 64 ? (long) d : trunc((int) d, fe);
    }

    private static long num(Object v) {
        return switch (v) {
            case Long l -> l;
            case Character c -> c;
            default -> throw new NotConstant();
        };
    }

    private String str(Object v, Frontend fe) {
        if (v instanceof Double d && fe.doubleIsFloat()) {
            return Float.toString((float) (double) d);
        }
        return v instanceof Character c ? String.valueOf((char) c) : String.valueOf(v);
    }

    /** Word for a static field initialized with constant {@code k}, converted to its type. */
    static long convertWord(Object k, Type t, Frontend fe) {
        try {
            return fe.constWord(convert(k, t, fe));
        } catch (NotConstant nc) {
            return fe.constWord(k);
        }
    }

    /** Assignment/cast conversion of a constant to type {@code t}. */
    static Object convert(Object v, Type t, Frontend fe) {
        if (t == Type.Prim.DOUBLE && (v instanceof Number || v instanceof Character)) {
            double d = dbl(v);
            return fe.doubleIsFloat() ? (double) (float) d : d;
        }
        if (t == Type.Prim.FLOAT && (v instanceof Number || v instanceof Character)) {
            return (float) dbl(v);
        }
        if ((v instanceof Double || v instanceof Float) && t instanceof Type.Prim p
                        && p.isNumeric()) {
            // floating -> integral narrowing goes through int (or the word) first
            v = toWord(dbl(v), fe);
        }
        if (t == Type.Prim.CHAR) {
            return (char) num(v);
        }
        if (t == Type.Prim.BYTE) {
            return (long) (byte) num(v);
        }
        if (t == Type.Prim.SHORT) {
            return (long) (short) num(v);
        }
        if (t == Type.Prim.INT || t == Type.Prim.LONG) {
            return trunc(num(v), fe);
        }
        if (t == Type.Prim.BOOLEAN && v instanceof Boolean) {
            return v;
        }
        if (v instanceof String && t instanceof Type.ClassT c && c.cls() == fe.stringClass) {
            return v;
        }
        throw new NotConstant();
    }
}
