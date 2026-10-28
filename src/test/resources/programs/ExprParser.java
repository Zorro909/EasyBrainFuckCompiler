import java.util.HashMap;
import java.util.Map;

/** Recursive-descent parser and evaluator for integer expressions with variables. */
public class ExprParser {
    interface Node {
        int eval(Map<String, Integer> env);
    }

    record Num(int value) implements Node {
        public int eval(Map<String, Integer> env) {
            return value;
        }
    }

    record Var(String name) implements Node {
        public int eval(Map<String, Integer> env) {
            Integer v = env.get(name);
            if (v == null) {
                throw new IllegalStateException("unbound " + name);
            }
            return v;
        }
    }

    record Bin(char op, Node l, Node r) implements Node {
        public int eval(Map<String, Integer> env) {
            int a = l.eval(env), b = r.eval(env);
            return switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> a / b;
                case '%' -> a % b;
                default -> throw new IllegalStateException("op " + op);
            };
        }
    }

    private final String src;
    private int pos;

    ExprParser(String src) {
        this.src = src;
    }

    char peek() {
        while (pos < src.length() && src.charAt(pos) == ' ') {
            pos++;
        }
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    Node expr() {
        Node n = term();
        while (peek() == '+' || peek() == '-') {
            char op = src.charAt(pos++);
            n = new Bin(op, n, term());
        }
        return n;
    }

    Node term() {
        Node n = factor();
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            char op = src.charAt(pos++);
            n = new Bin(op, n, factor());
        }
        return n;
    }

    Node factor() {
        char c = peek();
        if (c == '(') {
            pos++;
            Node n = expr();
            peek();
            pos++;
            return n;
        }
        if (c == '-') {
            pos++;
            return new Bin('-', new Num(0), factor());
        }
        int start = pos;
        if (Character.isDigit(c)) {
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            return new Num(Integer.parseInt(src.substring(start, pos)));
        }
        while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
            pos++;
        }
        return new Var(src.substring(start, pos));
    }

    public static void main(String[] args) {
        Map<String, Integer> env = new HashMap<>();
        env.put("x", 6);
        env.put("y", -4);
        String[] exprs = {"1 + 2 * 3", "(1 + 2) * 3", "x * x - y", "100 / (x + y) % 7",
                "-(x - 10) * -y", "2 * (3 + (4 - 1) * 5) - 17"};
        for (String e : exprs) {
            Node n = new ExprParser(e).expr();
            System.out.println(e + " = " + n.eval(env));
        }
        System.out.println(new ExprParser("x + 1").expr());
    }
}
