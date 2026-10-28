public class Varargs {
    static int sum(int... xs) {
        int s = 0;
        for (int x : xs) {
            s += x;
        }
        return s;
    }

    static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    static int max(int first, int... rest) {
        int m = first;
        for (int r : rest) m = Math.max(m, r);
        return m;
    }

    public static void main(String[] args) {
        System.out.println(sum() + " " + sum(1) + " " + sum(1, 2, 3) + " " + sum(new int[] {4, 5}));
        System.out.println(join("-", "a", "b", "c") + " " + join("+"));
        System.out.println(max(3) + " " + max(3, 9, 2));
        var list = new java.util.ArrayList<String>();
        list.add("var");
        var n = list.size();
        System.out.println(list.get(0) + n);
        String block = """
            Text block
              indented
            end""";
        System.out.println(block);
    }
}
