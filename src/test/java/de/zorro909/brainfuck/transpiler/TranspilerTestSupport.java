package de.zorro909.brainfuck.transpiler;

import de.zorro909.brainfuck.BfTestSupport;

/** Wraps a main-method body into a class, transpiles it and runs the Brainfuck. */
final class TranspilerTestSupport {

    private TranspilerTestSupport() {
    }

    static String transpileBody(String mainBody) {
        var source = """
                        public class Program {
                            public static void main(String[] args) {
                        %s
                            }
                        }
                        """.formatted(mainBody.indent(8));
        return new JavaToBrainfuckTranspiler().transpile(source);
    }

    static String runBody(String mainBody) {
        return runBody(mainBody, "");
    }

    static String runBody(String mainBody, String input) {
        return BfTestSupport.run(transpileBody(mainBody), input).output();
    }
}
