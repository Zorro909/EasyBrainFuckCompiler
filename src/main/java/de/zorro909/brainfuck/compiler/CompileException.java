package de.zorro909.brainfuck.compiler;

import com.github.javaparser.ast.Node;

/** A Java construct that the compiler rejects, with its source position when known. */
public class CompileException extends RuntimeException {

    public CompileException(String message) {
        super(message);
    }

    public CompileException(Node at, String message) {
        super(position(at) + message);
    }

    private static String position(Node at) {
        if (at == null || at.getRange().isEmpty()) {
            return "";
        }
        var begin = at.getRange().get().begin;
        String file = at.findCompilationUnit()
                        .flatMap(cu -> cu.getStorage().map(s -> s.getFileName()))
                        .orElse(null);
        return (file != null ? file + ":" : "line ") + begin.line + ":" + begin.column + ": ";
    }
}
