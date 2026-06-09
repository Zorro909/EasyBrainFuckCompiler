package de.zorro909.brainfuck.transpiler;

import com.github.javaparser.ast.Node;

/** Thrown when the transpiler encounters Java syntax outside the supported subset. */
public class UnsupportedJavaConstructException extends RuntimeException {

    public UnsupportedJavaConstructException(String message) {
        super(message);
    }

    public UnsupportedJavaConstructException(String message, Node node) {
        super(message + node.getBegin().map(p -> " (line " + p.line + ")").orElse(""));
    }
}
