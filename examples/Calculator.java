import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive calculator: reads one statement per line from standard input.
 *
 * <pre>
 *   x = 6 * 7
 *   x / 4 + x % 4
 * </pre>
 *
 * Supports + - * / %, parentheses, unary minus and variables.
 */
public class Calculator {
    private final Map<String, Integer> vars = new HashMap<>();
    private String src;
    private int pos;

    int evaluate(String line) {
        src = line;
        pos = 0;
        int eq = line.indexOf('=');
        if (eq > 0) {
            String name = line.substring(0, eq).trim();
            src = line.substring(eq + 1);
            int value = expr();
            vars.put(name, value);
            return value;
        }
        return expr();
    }

    private char peek() {
        while (pos < src.length() && src.charAt(pos) == ' ') {
            pos++;
        }
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private int expr() {
        int v = term();
        while (peek() == '+' || peek() == '-') {
            v = src.charAt(pos++) == '+' ? v + term() : v - term();
        }
        return v;
    }

    private int term() {
        int v = factor();
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            char op = src.charAt(pos++);
            int r = factor();
            v = op == '*' ? v * r : op == '/' ? v / r : v % r;
        }
        return v;
    }

    private int factor() {
        char c = peek();
        if (c == '-') {
            pos++;
            return -factor();
        }
        if (c == '(') {
            pos++;
            int v = expr();
            if (peek() != ')') {
                throw new IllegalArgumentException("expected ) at " + pos);
            }
            pos++;
            return v;
        }
        int start = pos;
        if (Character.isDigit(c)) {
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            return Integer.parseInt(src.substring(start, pos));
        }
        while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
            pos++;
        }
        String name = src.substring(start, pos);
        Integer v = vars.get(name);
        if (v == null) {
            throw new IllegalArgumentException("unknown variable '" + name + "'");
        }
        return v;
    }

    public static void main(String[] args) {
        Calculator calc = new Calculator();
        Scanner in = new Scanner(System.in);
        while (in.hasNextLine()) {
            String line = in.nextLine().trim();
            if (!line.isEmpty()) {
                System.out.println("= " + calc.evaluate(line));
            }
        }
    }
}
