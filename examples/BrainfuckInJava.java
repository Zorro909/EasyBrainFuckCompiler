public class BrainfuckInJava {
    static String run(String code, String input) {
        int[] tape = new int[300];
        int ptr = 0, in = 0;
        StringBuilder out = new StringBuilder();
        int[] jump = new int[code.length()];
        int[] stack = new int[64];
        int sp = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '[') {
                stack[sp++] = i;
            } else if (c == ']') {
                int open = stack[--sp];
                jump[open] = i;
                jump[i] = open;
            }
        }
        for (int pc = 0; pc < code.length(); pc++) {
            switch (code.charAt(pc)) {
                case '>' -> ptr++;
                case '<' -> ptr--;
                case '+' -> tape[ptr] = (tape[ptr] + 1) & 255;
                case '-' -> tape[ptr] = (tape[ptr] + 255) & 255;
                case '.' -> out.append((char) tape[ptr]);
                case ',' -> tape[ptr] = in < input.length() ? input.charAt(in++) : 0;
                case '[' -> {
                    if (tape[ptr] == 0) pc = jump[pc];
                }
                case ']' -> {
                    if (tape[ptr] != 0) pc = jump[pc];
                }
                default -> {
                }
            }
        }
        return out.toString();
    }

    public static void main(String[] args) {
        String hello = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.";
        System.out.print(run(hello, ""));
        System.out.println(run(",[.,]", "echo"));
    }
}
