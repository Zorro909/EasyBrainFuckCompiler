package de.zorro909.brainfuck.transpiler;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;

import de.zorro909.brainfuck.core.BfOps;

/**
 * Evaluates a Java expression to Brainfuck code. Each evaluation leaves its result in a
 * freshly pushed temporary cell (returned by {@link #emit}), which the caller must pop
 * via {@link #popTemp} once consumed. All arithmetic is on 8-bit cells, mod 256.
 */
class ExpressionEmitter {

    private final BfOps ops;
    private final MemoryLayout layout;

    ExpressionEmitter(BfOps ops, MemoryLayout layout) {
        this.ops = ops;
        this.layout = layout;
    }

    /** Emits code computing {@code expr} into a fresh temporary cell and returns it. */
    int emit(Expression expr) {
        return switch (expr) {
            case IntegerLiteralExpr literal -> {
                int t = layout.pushTemp();
                ops.set(t, literal.asNumber().intValue());
                yield t;
            }
            case CharLiteralExpr literal -> {
                int t = layout.pushTemp();
                ops.set(t, literal.asChar());
                yield t;
            }
            case NameExpr name -> {
                int t = layout.pushTemp();
                ops.copy(layout.variableCell(name.getNameAsString()), t);
                yield t;
            }
            case EnclosedExpr enclosed -> emit(enclosed.getInner());
            case CastExpr cast -> emit(cast.getExpression());
            case UnaryExpr unary -> emitUnary(unary);
            case BinaryExpr binary -> emitBinary(binary);
            case MethodCallExpr call -> emitCall(call);
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported expression: " + expr.getClass().getSimpleName()
                                            + " '" + expr + "'", expr);
        };
    }

    /** Releases a temporary returned by {@link #emit}, clearing it for reuse. */
    void popTemp(int cell) {
        ops.clear(cell);
        layout.popTemp(cell);
    }

    private int emitUnary(UnaryExpr unary) {
        return switch (unary.getOperator()) {
            case LOGICAL_COMPLEMENT -> {
                int t = emit(unary.getExpression());
                ops.not(t);
                yield t;
            }
            case PLUS -> emit(unary.getExpression());
            case MINUS -> throw new UnsupportedJavaConstructException(
                            "Negative values are not supported (cells are 0-255)", unary);
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported unary operator in expression position: '"
                                            + unary + "'", unary);
        };
    }

    private int emitBinary(BinaryExpr binary) {
        int left = emit(binary.getLeft());
        int right = emit(binary.getRight());
        switch (binary.getOperator()) {
            case PLUS -> ops.moveAdd(right, left);
            case MINUS -> ops.moveSub(right, left);
            case MULTIPLY -> replaceLeft(left, right, result -> ops.mul(left, right, result));
            case DIVIDE -> emitDivmod(left, right, true);
            case REMAINDER -> emitDivmod(left, right, false);
            case EQUALS -> replaceLeft(left, right, result -> ops.eq(left, right, result));
            case NOT_EQUALS -> replaceLeft(left, right, result -> ops.neq(left, right, result));
            case LESS -> replaceLeft(left, right, result -> ops.lt(left, right, result));
            case GREATER -> replaceLeft(left, right, result -> ops.gt(left, right, result));
            case LESS_EQUALS -> replaceLeft(left, right, result -> ops.le(left, right, result));
            case GREATER_EQUALS -> replaceLeft(left, right, result -> ops.ge(left, right, result));
            case AND -> replaceLeft(left, right, result -> ops.and(left, right, result));
            case OR -> replaceLeft(left, right, result -> ops.or(left, right, result));
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported binary operator '"
                                            + binary.getOperator().asString() + "'", binary);
        }
        popTemp(right);
        return left;
    }

    /**
     * Runs an operation that writes its result into a fresh cell (because its inputs
     * must not alias the output), then moves the result down into {@code left}.
     */
    private void replaceLeft(int left, int right, java.util.function.IntConsumer operation) {
        int result = layout.pushTemp();
        operation.accept(result);
        ops.clear(left);
        ops.moveAdd(result, left);
        layout.popTemp(result);
        ops.clear(right);
    }

    private void emitDivmod(int left, int right, boolean quotient) {
        int q = layout.pushTemp();
        int r = layout.pushTemp();
        ops.divmod(left, right, q, r);
        ops.clear(left);
        ops.moveAdd(quotient ? q : r, left);
        ops.clear(r);
        layout.popTemp(r);
        ops.clear(q);
        layout.popTemp(q);
        ops.clear(right);
    }

    private int emitCall(MethodCallExpr call) {
        String scope = call.getScope().map(Object::toString).orElse("");
        String name = call.getNameAsString();
        if (scope.equals("Bf") && name.equals("readInt") && call.getArguments().isEmpty()) {
            int t = layout.pushTemp();
            ops.readInt(t);
            return t;
        }
        if (scope.equals("System.in") && name.equals("read") && call.getArguments().isEmpty()) {
            int t = layout.pushTemp();
            ops.readChar(t);
            return t;
        }
        if (scope.equals("Bf") && name.equals("syscall") && call.getArguments().size() == 1) {
            return emitSyscall(call.getArgument(0));
        }
        throw new UnsupportedJavaConstructException(
                        "Unsupported method call '" + call + "' (only System.out.print/"
                                        + "println, System.in.read, Bf.readInt and Bf.syscall"
                                        + " are available)", call);
    }

    /**
     * Emits the syscall opcode {@code '@'}: the id cell selects the syscall, the cell
     * right of it carries the argument/result. Because expression temporaries are
     * consecutive, pushing two temps yields exactly that layout.
     */
    private int emitSyscall(Expression idExpression) {
        int id = emit(idExpression);
        int result = layout.pushTemp();
        ops.builder().moveTo(id);
        ops.builder().raw("@", 0);
        ops.clear(id);
        ops.moveAdd(result, id);
        layout.popTemp(result);
        return id;
    }
}
