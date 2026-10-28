import java.util.*;

public class WordStats {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Map<String, Integer> counts = new HashMap<>();
        int words = 0, chars = 0, lines = 0;
        String longest = "";
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            lines++;
            for (String w : line.toLowerCase().split("\\s+")) {
                StringBuilder clean = new StringBuilder();
                for (char c : w.toCharArray()) {
                    if (Character.isLetter(c)) clean.append(c);
                }
                if (clean.length() == 0) continue;
                String word = clean.toString();
                words++;
                chars += word.length();
                counts.put(word, counts.getOrDefault(word, 0) + 1);
                if (word.length() > longest.length()) longest = word;
            }
        }
        List<String> sorted = new ArrayList<>(counts.keySet());
        sorted.sort((a, b) -> counts.get(b) - counts.get(a) != 0 ? counts.get(b) - counts.get(a)
                        : a.compareTo(b));
        System.out.println(lines + " lines, " + words + " words, " + chars + " letters");
        System.out.println("longest: " + longest);
        for (int i = 0; i < 5 && i < sorted.size(); i++) {
            String w = sorted.get(i);
            System.out.printf("%-6s %d%n", w, counts.get(w));
        }
    }
}
