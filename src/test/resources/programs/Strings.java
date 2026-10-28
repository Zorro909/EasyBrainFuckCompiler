public class Strings {
    static boolean isPalindrome(String s) {
        int i = 0, j = s.length() - 1;
        while (i < j) {
            if (s.charAt(i) != s.charAt(j)) {
                return false;
            }
            i++;
            j--;
        }
        return true;
    }

    public static void main(String[] args) {
        String s = "Hello, Brainfuck";
        System.out.println(s.length() + " " + s.charAt(4) + " " + s.indexOf('B') + " "
                        + s.indexOf("fuck") + " " + s.lastIndexOf('l'));
        System.out.println(s.substring(7) + "|" + s.substring(0, 5) + "|" + s.toUpperCase()
                        + "|" + s.toLowerCase());
        System.out.println(s.contains("Brain") + " " + s.startsWith("Hell") + " "
                        + s.endsWith("k") + " " + s.equals("hello") + " "
                        + s.equalsIgnoreCase("HELLO, BRAINFUCK"));
        String a = "abc", b = "abd";
        System.out.println(a.compareTo(b) + " " + b.compareTo(a) + " " + a.compareTo("abc"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i).append(',');
        }
        sb.setLength(sb.length() - 1);
        System.out.println(sb.toString() + " reversed: " + sb.reverse());
        String csv = "one,two,,three";
        String[] parts = csv.split(",");
        System.out.println(parts.length + " " + parts[3]);
        String[] ws = "  the quick  brown fox ".trim().split("\\s+");
        System.out.println(ws.length + " " + ws[2]);
        System.out.println(isPalindrome("racecar") + " " + isPalindrome("brainfuck"));
        String concat = "";
        for (char c = 'a'; c <= 'e'; c++) {
            concat += c;
        }
        System.out.println(concat + concat.length());
        System.out.println("x" + 1 + 2 + " " + (1 + 2) + "x" + 'c' + true + null);
        System.out.println(String.valueOf(42) + String.valueOf('z') + Integer.toString(-77));
        System.out.println("  padded ".trim() + "-" + "ab".repeat(3) + "-" + "a-b-c".replace('-', '+'));
        System.out.println("hash " + "hello".hashCode() + " " + "".isEmpty());
        String text = "the cat sat on the mat with the hat";
        int count = 0, idx = 0;
        while ((idx = text.indexOf("the", idx)) >= 0) {
            count++;
            idx += 3;
        }
        System.out.println(count + " " + text.replace("at", "og"));
        System.out.printf("%d + %d = %d%n", 2, 3, 5);
        System.out.printf("[%5d] [%-5s] [%05d] [%c] [%x] [%s]%n", 42, "ab", -42, 'q', 255, true);
        String formatted = String.valueOf(3) + "/" + 4;
        System.out.println(formatted);
        System.out.println("Unicode: äöü € ✓");
        char[] letters = {'d', 'a', 'c', 'b'};
        java.util.Arrays.sort(letters);
        System.out.println(letters);
        System.out.println(Character.isDigit('7') + " " + Character.isLetter('x') + " "
                        + Character.toUpperCase('q') + Character.getNumericValue('8'));
    }
}
