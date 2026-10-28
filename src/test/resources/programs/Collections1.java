import java.util.*;

public class Collections1 {
    public static void main(String[] args) {
        List<Integer> nums = new ArrayList<>();
        for (int i = 10; i > 0; i--) {
            nums.add(i * i % 17);
        }
        System.out.println(nums + " size " + nums.size());
        Collections.sort(nums);
        System.out.println(nums);
        nums.sort((a, b) -> b - a);
        System.out.println(nums);
        int sum = 0;
        for (int n : nums) {
            sum += n;
        }
        System.out.println("sum " + sum + " contains 13: " + nums.contains(13) + " index "
                        + nums.indexOf(13));
        nums.remove(Integer.valueOf(13));
        nums.remove(0);
        System.out.println(nums);

        Map<String, Integer> freq = new HashMap<>();
        String text = "the quick brown fox jumps over the lazy dog the end";
        for (String w : text.split(" ")) {
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }
        System.out.println(freq.get("the") + " " + freq.get("fox") + " " + freq.get("cat")
                        + " " + freq.containsKey("dog") + " " + freq.size());
        List<String> keys = new ArrayList<>(freq.keySet());
        Collections.sort(keys);
        System.out.println(keys);
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > 1) {
                System.out.println(e.getKey() + "=" + e.getValue());
            }
        }

        Deque<Integer> stack = new ArrayDeque<>();
        Queue<String> queue = new LinkedList<>();
        for (int i = 0; i < 4; i++) {
            stack.push(i);
            queue.offer("q" + i);
        }
        System.out.println(stack.pop() + " " + stack.peek() + " " + queue.poll() + " "
                        + queue.peek() + " " + queue.size());
        Stack<Character> st = new Stack<>();
        String expr = "{[()()]}";
        boolean ok = true;
        for (char ch : expr.toCharArray()) {
            if (ch == '(' || ch == '[' || ch == '{') {
                st.push(ch);
            } else {
                char open = st.pop();
                if (open == '(' && ch != ')' || open == '[' && ch != ']') {
                    ok = false;
                }
            }
        }
        System.out.println("balanced " + (ok && st.isEmpty()));
        Set<Integer> seen = new HashSet<>();
        int dup = -1;
        for (int v : new int[] {4, 8, 15, 16, 23, 8, 42}) {
            if (!seen.add(v)) {
                dup = v;
            }
        }
        System.out.println("dup " + dup + " " + seen.size() + " " + seen.contains(42));
        List<String> names = new ArrayList<>(List.of("carol", "alice", "bob"));
        names.sort(Comparator.naturalOrder());
        System.out.println(names);
    }
}
