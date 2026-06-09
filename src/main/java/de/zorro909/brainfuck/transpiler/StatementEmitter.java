package de.zorro909.brainfuck.transpiler;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.PrimitiveType;

import de.zorro909.brainfuck.core.BfOps;

/** Emits Brainfuck code for the supported Java statement subset. */
class StatementEmitter {

    private final BfOps ops;
    private final MemoryLayout layout;
    private final ExpressionEmitter expressions;

    StatementEmitter(BfOps ops, MemoryLayout layout) {
        this.ops = ops;
        this.layout = layout;
        this.expressions = new ExpressionEmitter(ops, layout);
    }

    void emit(Statement statement) {
        switch (statement) {
            case BlockStmt block -> block.getStatements().forEach(this::emit);
            case ExpressionStmt expression -> emitExpressionStatement(expression.getExpression());
            case IfStmt ifStmt -> emitIf(ifStmt);
            case WhileStmt whileStmt -> emitWhile(whileStmt);
            case ForStmt forStmt -> emitFor(forStmt);
            case EmptyStmt ignored -> {
            }
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported statement: " + statement.getClass().getSimpleName(),
                            statement);
        }
    }

    private void emitExpressionStatement(Expression expression) {
        switch (expression) {
            case VariableDeclarationExpr declaration -> emitDeclaration(declaration);
            case AssignExpr assignment -> emitAssignment(assignment);
            case UnaryExpr unary -> emitIncrementDecrement(unary);
            case MethodCallExpr call -> emitCallStatement(call);
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported expression statement: '" + expression + "'",
                            expression);
        }
    }

    /** Cells are pre-allocated by the hoisting pass; here we only (re-)initialize. */
    private void emitDeclaration(VariableDeclarationExpr declaration) {
        declaration.getVariables().forEach(variable -> {
            int cell = layout.variableCell(variable.getNameAsString());
            ops.clear(cell);
            variable.getInitializer().ifPresent(initializer -> {
                int value = expressions.emit(initializer);
                ops.moveAdd(value, cell);
                expressions.popTemp(value);
            });
        });
    }

    private void emitAssignment(AssignExpr assignment) {
        if (!(assignment.getTarget() instanceof NameExpr target)) {
            throw new UnsupportedJavaConstructException(
                            "Assignment target must be a variable: '" + assignment + "'",
                            assignment);
        }
        int cell = layout.variableCell(target.getNameAsString());
        int value = expressions.emit(assignment.getValue());
        switch (assignment.getOperator()) {
            case ASSIGN -> {
                ops.clear(cell);
                ops.moveAdd(value, cell);
            }
            case PLUS -> ops.moveAdd(value, cell);
            case MINUS -> ops.moveSub(value, cell);
            case MULTIPLY -> {
                int result = layout.pushTemp();
                ops.mul(cell, value, result);
                ops.clear(cell);
                ops.moveAdd(result, cell);
                layout.popTemp(result);
            }
            case DIVIDE, REMAINDER -> {
                int quotient = layout.pushTemp();
                int remainder = layout.pushTemp();
                ops.divmod(cell, value, quotient, remainder);
                ops.clear(cell);
                ops.moveAdd(assignment.getOperator() == AssignExpr.Operator.DIVIDE
                                ? quotient
                                : remainder, cell);
                ops.clear(remainder);
                layout.popTemp(remainder);
                ops.clear(quotient);
                layout.popTemp(quotient);
            }
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported assignment operator '"
                                            + assignment.getOperator().asString() + "'",
                            assignment);
        }
        expressions.popTemp(value);
    }

    private void emitIncrementDecrement(UnaryExpr unary) {
        if (!(unary.getExpression() instanceof NameExpr target)) {
            throw new UnsupportedJavaConstructException(
                            "Increment/decrement target must be a variable: '" + unary + "'",
                            unary);
        }
        int cell = layout.variableCell(target.getNameAsString());
        switch (unary.getOperator()) {
            case PREFIX_INCREMENT, POSTFIX_INCREMENT -> ops.addConst(cell, 1);
            case PREFIX_DECREMENT, POSTFIX_DECREMENT -> ops.subConst(cell, 1);
            default -> throw new UnsupportedJavaConstructException(
                            "Unsupported unary statement '" + unary + "'", unary);
        }
    }

    private void emitIf(IfStmt ifStmt) {
        int condition = expressions.emit(ifStmt.getCondition());
        if (ifStmt.getElseStmt().isPresent()) {
            ops.ifElse(condition, () -> emit(ifStmt.getThenStmt()),
                            () -> emit(ifStmt.getElseStmt().get()));
        } else {
            ops.ifThen(condition, () -> emit(ifStmt.getThenStmt()));
        }
        expressions.popTemp(condition);
    }

    private void emitWhile(WhileStmt whileStmt) {
        int flag = layout.pushTemp();
        ops.whileLoop(flag, () -> {
            int condition = expressions.emit(whileStmt.getCondition());
            ops.toBool(condition);
            ops.clear(flag);
            ops.moveAdd(condition, flag);
            expressions.popTemp(condition);
        }, () -> emit(whileStmt.getBody()));
        ops.clear(flag);
        layout.popTemp(flag);
    }

    /** Desugars {@code for (init; cond; update) body} into init + while. */
    private void emitFor(ForStmt forStmt) {
        forStmt.getInitialization().forEach(this::emitExpressionStatement);
        if (forStmt.getCompare().isEmpty()) {
            throw new UnsupportedJavaConstructException(
                            "for loops need a condition (no break support)", forStmt);
        }
        int flag = layout.pushTemp();
        ops.whileLoop(flag, () -> {
            int condition = expressions.emit(forStmt.getCompare().get());
            ops.toBool(condition);
            ops.clear(flag);
            ops.moveAdd(condition, flag);
            expressions.popTemp(condition);
        }, () -> {
            emit(forStmt.getBody());
            forStmt.getUpdate().forEach(this::emitExpressionStatement);
        });
        ops.clear(flag);
        layout.popTemp(flag);
    }

    private void emitCallStatement(MethodCallExpr call) {
        String scope = call.getScope().map(Object::toString).orElse("");
        if (scope.equals("System.out")
                        && (call.getNameAsString().equals("print")
                                        || call.getNameAsString().equals("println"))) {
            emitPrint(call);
            return;
        }
        // expression intrinsics used as statements (e.g. Bf.syscall): evaluate and drop
        int result = expressions.emit(call);
        expressions.popTemp(result);
    }

    private void emitPrint(MethodCallExpr call) {
        boolean newline = call.getNameAsString().equals("println");
        if (call.getArguments().size() > 1) {
            throw new UnsupportedJavaConstructException(
                            "print/println with multiple arguments", call);
        }
        if (call.getArguments().isEmpty()) {
            if (newline) {
                ops.printString("\n");
            }
            return;
        }
        Expression argument = call.getArgument(0);
        if (argument instanceof StringLiteralExpr literal) {
            ops.printString(newline ? literal.asString() + "\n" : literal.asString());
            return;
        }
        int value = expressions.emit(argument);
        if (isCharValued(argument)) {
            ops.printChar(value);
        } else {
            ops.printInt(value);
        }
        expressions.popTemp(value);
        if (newline) {
            ops.printString("\n");
        }
    }

    private boolean isCharValued(Expression expression) {
        return switch (expression) {
            case CharLiteralExpr ignored -> true;
            case CastExpr cast -> cast.getType().equals(PrimitiveType.charType());
            case NameExpr name -> layout.isDeclared(name.getNameAsString())
                            && layout.variableType(name.getNameAsString()) == MemoryLayout.VarType.CHAR;
            default -> false;
        };
    }
}
