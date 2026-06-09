package de.zorro909.brainfuck.transpiler;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.PrimitiveType;

import de.zorro909.brainfuck.core.BfOps;
import de.zorro909.brainfuck.core.CodeBuilder;

/**
 * Transpiles a small Java subset to Brainfuck.
 *
 * <p>Input: a single class with one {@code public static void main} method. Supported
 * inside it: {@code int}/{@code char} variables (8-bit values, 0–255, mod 256),
 * assignments incl. {@code += -= *= /= %=} and {@code ++}/{@code --}, the operators
 * {@code + - * / % == != < > <= >= && || !}, {@code if}/{@code else}, {@code while},
 * {@code for}, {@code System.out.print/println} of string literals, int and char
 * expressions, {@code System.in.read()} (one byte, 0 at end of input) and the
 * intrinsics {@code Bf.readInt()} / {@code Bf.syscall(id)}.
 *
 * <p>Everything else — methods, arrays, Strings as variables, negative numbers,
 * {@code break}/{@code continue}, short-circuit evaluation order — is rejected with an
 * {@link UnsupportedJavaConstructException}.
 */
public class JavaToBrainfuckTranspiler {

    static final int SCRATCH_START = 0;
    static final int SCRATCH_SIZE = 16;

    public String transpile(String javaSource) {
        CompilationUnit unit = StaticJavaParser.parse(javaSource);
        MethodDeclaration main = findMain(unit);
        var builder = new CodeBuilder();
        var ops = new BfOps(builder, SCRATCH_START, SCRATCH_SIZE);
        var layout = new MemoryLayout(SCRATCH_START + SCRATCH_SIZE);
        hoistVariables(main, layout);
        var statements = new StatementEmitter(ops, layout);
        main.getBody().orElseThrow(() -> new UnsupportedJavaConstructException(
                        "main method has no body")).getStatements().forEach(statements::emit);
        return builder.code();
    }

    private MethodDeclaration findMain(CompilationUnit unit) {
        var methods = unit.findAll(MethodDeclaration.class);
        if (methods.size() != 1) {
            throw new UnsupportedJavaConstructException(
                            "Expected exactly one method (main), found " + methods.size());
        }
        var fields = unit.findAll(FieldDeclaration.class);
        if (!fields.isEmpty()) {
            throw new UnsupportedJavaConstructException("Fields are not supported",
                            fields.getFirst());
        }
        MethodDeclaration main = methods.getFirst();
        if (!main.getNameAsString().equals("main") || !main.isStatic()) {
            throw new UnsupportedJavaConstructException(
                            "The single method must be 'public static void main'", main);
        }
        return main;
    }

    /**
     * Allocates a cell for every declared variable up front, so declarations inside
     * loop bodies cannot collide with active loop temporaries.
     */
    private void hoistVariables(MethodDeclaration main, MemoryLayout layout) {
        for (var declaration : main.findAll(VariableDeclarationExpr.class)) {
            for (var variable : declaration.getVariables()) {
                MemoryLayout.VarType type;
                if (variable.getType().equals(PrimitiveType.intType())) {
                    type = MemoryLayout.VarType.INT;
                } else if (variable.getType().equals(PrimitiveType.charType())) {
                    type = MemoryLayout.VarType.CHAR;
                } else {
                    throw new UnsupportedJavaConstructException("Unsupported variable type '"
                                    + variable.getType() + "' (only int and char)", variable);
                }
                layout.declareVariable(variable.getNameAsString(), type);
            }
        }
    }
}
