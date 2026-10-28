import java.util.HashMap;
import java.util.Map;

/** Static initializers that depend on classes declared after them (the JVM initializes lazily). */
public class StaticInitOrder {
    private static final Map<String, Kind> KEYWORDS;
    private static final int LIMIT = Config.MAX * 2;

    static {
        KEYWORDS = new HashMap<>();
        KEYWORDS.put("if", Kind.IF);
        KEYWORDS.put("while", Kind.WHILE);
        KEYWORDS.put("print", Kind.PRINT);
    }

    enum Kind { IF, WHILE, PRINT, NAME }

    static class Config {
        static final int MAX = Integer.parseInt("21");
        static final String GREETING = "limit is " + MAX;
    }

    static Kind classify(String word) {
        Kind k = KEYWORDS.get(word);
        return k == null ? Kind.NAME : k;
    }

    public static void main(String[] args) {
        String src = "while x print y if";
        for (String w : src.split(" ")) {
            System.out.println(w + " -> " + classify(w));
        }
        System.out.println(LIMIT + " / " + Config.GREETING);
    }
}
