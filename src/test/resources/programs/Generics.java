import java.util.ArrayList;
import java.util.List;

public class Generics {
    static class Box<T extends Comparable<T>> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        boolean isBigger(Box<T> other) {
            return value.compareTo(other.value) > 0;
        }
    }

    static class Node<T> {
        T value;
        Node<T> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    static class MyList<T> implements Iterable<T> {
        Node<T> head;
        int size;

        void push(T v) {
            head = new Node<>(v, head);
            size++;
        }

        public java.util.Iterator<T> iterator() {
            return new java.util.Iterator<T>() {
                Node<T> cur = head;

                public boolean hasNext() {
                    return cur != null;
                }

                public T next() {
                    T v = cur.value;
                    cur = cur.next;
                    return v;
                }
            };
        }
    }

    static <T extends Comparable<T>> T max(List<T> xs) {
        T best = xs.get(0);
        for (T x : xs) {
            if (x.compareTo(best) > 0) {
                best = x;
            }
        }
        return best;
    }

    static <T> void swap(T[] arr, int i, int j) {
        T t = arr[i];
        arr[i] = arr[j];
        arr[j] = t;
    }

    public static void main(String[] args) {
        Box<Integer> a = new Box<>(5);
        Box<Integer> b = new Box<>(9);
        System.out.println(a.isBigger(b) + " " + b.isBigger(a) + " " + (a.get() + b.get()));
        Box<String> s = new Box<>("zebra");
        System.out.println(s.isBigger(new Box<>("apple")) + " " + s.get().toUpperCase());
        MyList<String> list = new MyList<>();
        list.push("c");
        list.push("b");
        list.push("a");
        for (String x : list) {
            System.out.print(x);
        }
        System.out.println(" size=" + list.size);
        List<Integer> nums = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nums.add((i * 7) % 11);
        }
        System.out.println(max(nums));
        List<String> strs = new ArrayList<>();
        strs.add("pear");
        strs.add("zucchini");
        strs.add("apple");
        System.out.println(max(strs));
        Integer[] arr = {1, 2, 3};
        swap(arr, 0, 2);
        System.out.println(arr[0] + "" + arr[1] + arr[2]);
        Integer boxed = 1000;
        int unboxed = boxed + 1;
        Integer other = 1000;
        System.out.println(unboxed + " " + boxed.equals(other) + " " + boxed.compareTo(999));
        List<List<Integer>> nested = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<Integer> row = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                row.add(j);
            }
            nested.add(row);
        }
        System.out.println(nested);
    }
}
