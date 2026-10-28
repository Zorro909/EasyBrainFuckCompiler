import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Brainfuck to C translator, replacing the old {@code bin/BF2OC.jar} (8-bit wrapping cells,
 * a heap-allocated tape, EOF leaves the cell unchanged, linear-time translation).
 *
 * <p>Usage: {@code java BfToC.java input.bf output.c [tapeCells]}, then e.g.
 * {@code gcc -O1 -o prog output.c}.
 *
 * <p>Straight-line code is folded with pointer offsets ({@code p[3] += 2; p += 5;}), {@code [-]}
 * becomes a store, {@code [>]}-style loops become scans, and balanced loops that only add
 * multiples of the counter cell become multiply-adds.
 */
public class BfToC {

    private final String src;
    private int pos;
    private final StringBuilder out = new StringBuilder();
    private int depth = 1;

    BfToC(String src) {
        this.src = src.replaceAll("[^+\\-<>.,\\[\\]]", "");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java BfToC.java input.bf output.c [tapeCells]");
            System.exit(2);
        }
        long cells = args.length > 2 ? Long.parseLong(args[2]) : 1L << 22;
        BfToC t = new BfToC(Files.readString(Path.of(args[0])));
        t.block();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            w.println("#include <stdio.h>\n#include <stdlib.h>\n");
            w.println("int main(void) {");
            w.println("    unsigned char *p = calloc(" + cells + ", 1);");
            w.println("    int c;");
            w.print(t.out);
            w.println("    return 0;\n}");
        }
    }

    /** Translates until the matching {@code ]} or the end of input. */
    private void block() {
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (ch == ']') {
                pos++;
                return;
            }
            if (ch == '[') {
                pos++;
                loop();
            } else {
                straight();
            }
        }
    }

    /** A run of {@code + - < > . ,} with deferred pointer movement. */
    private void straight() {
        TreeMap<Integer, Integer> adds = new TreeMap<>();
        int off = 0;
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            switch (ch) {
                case '+' -> adds.merge(off, 1, Integer::sum);
                case '-' -> adds.merge(off, -1, Integer::sum);
                case '>' -> off++;
                case '<' -> off--;
                case '.', ',' -> {
                    flushAdds(adds);
                    line(ch == '.' ? "putchar(p[" + off + "]);"
                                    : "fflush(stdout); if ((c = getchar()) != EOF) p[" + off
                                                    + "] = (unsigned char) c;");
                }
                default -> {
                    flushAdds(adds);
                    movePointer(off);
                    return;
                }
            }
            pos++;
        }
        flushAdds(adds);
        movePointer(off);
    }

    private void loop() {
        int start = pos;
        int end = matching(start);
        String body = src.substring(start, end);
        pos = end + 1;
        if (body.equals("-") || body.equals("+")) {
            line("p[0] = 0;");
            return;
        }
        if (!body.isEmpty() && body.chars().allMatch(ch -> ch == '>' || ch == '<')) {
            int step = (int) body.chars().filter(ch -> ch == '>').count()
                            - (int) body.chars().filter(ch -> ch == '<').count();
            if (step != 0) {
                line("while (p[0]) p += " + step + ";");
                return;
            }
        }
        if (multiplyLoop(body)) {
            return;
        }
        line("while (p[0]) {");
        depth++;
        pos = start;
        block();
        depth--;
        line("}");
    }

    /** {@code [->+>++<<]}: no I/O or nesting, pointer returns, counter decremented by one. */
    private boolean multiplyLoop(String body) {
        if (!body.chars().allMatch(ch -> "+-<>".indexOf(ch) >= 0)) {
            return false;
        }
        TreeMap<Integer, Integer> adds = new TreeMap<>();
        int off = 0;
        for (char ch : body.toCharArray()) {
            switch (ch) {
                case '+' -> adds.merge(off, 1, Integer::sum);
                case '-' -> adds.merge(off, -1, Integer::sum);
                case '>' -> off++;
                default -> off--;
            }
        }
        if (off != 0 || adds.getOrDefault(0, 0) != -1) {
            return false;
        }
        adds.remove(0);
        List<String> parts = new ArrayList<>();
        adds.forEach((k, v) -> {
            if (v != 0) {
                parts.add("p[" + k + "] += " + (v == 1 ? "p[0]" : v + " * p[0]") + ";");
            }
        });
        if (!parts.isEmpty()) {
            line("if (p[0]) { " + String.join(" ", parts) + " p[0] = 0; }");
        } else {
            line("p[0] = 0;");
        }
        return true;
    }

    private int matching(int start) {
        int level = 1;
        for (int i = start; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '[') {
                level++;
            } else if (ch == ']' && --level == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("unmatched [ at command " + (start - 1));
    }

    private void flushAdds(TreeMap<Integer, Integer> adds) {
        adds.forEach((k, v) -> {
            int n = (byte) (int) v;
            if (n != 0) {
                line("p[" + k + "] " + (n > 0 ? "+= " + n : "-= " + -n) + ";");
            }
        });
        adds.clear();
    }

    private void movePointer(int off) {
        if (off != 0) {
            line("p += " + off + ";");
        }
    }

    private void line(String s) {
        // compiled dispatch loops nest hundreds of levels deep; cap the indentation
        out.repeat("    ", Math.min(depth, 8)).append(s).append('\n');
    }
}
