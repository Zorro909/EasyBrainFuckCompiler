// Runtime library: java.util / java.io subset.

interface Comparator<T> {
    int compare(T a, T b);

    default Comparator<T> reversed() {
        return new ReversedComparator<T>(this);
    }

    static <T> Comparator<T> naturalOrder() {
        return new NaturalOrder<T>();
    }

    static <T> Comparator<T> reverseOrder() {
        return new ReversedComparator<T>(new NaturalOrder<T>());
    }
}

final class ReversedComparator<T> implements Comparator<T> {
    final Comparator<T> inner;

    ReversedComparator(Comparator<T> inner) {
        this.inner = inner;
    }

    public int compare(T a, T b) {
        return inner.compare(b, a);
    }
}

final class NaturalOrder<T> implements Comparator<T> {
    public int compare(T a, T b) {
        return ((Comparable) a).compareTo(b);
    }
}

interface Collection<E> extends Iterable<E> {
    int size();

    boolean add(E e);

    boolean contains(Object o);

    default boolean isEmpty() {
        return size() == 0;
    }
}

interface List<E> extends Collection<E> {
    E get(int index);

    E set(int index, E e);

    void add(int index, E e);

    E remove(int index);

    boolean remove(Object o);

    int indexOf(Object o);

    void clear();

    default void sort(Comparator<E> c) {
        Collections.sortList(this, c);
    }

    default boolean addAll(Collection<E> other) {
        for (E e : other) {
            add(e);
        }
        return true;
    }

    static <E> List<E> of(E... items) {
        ArrayList<E> l = new ArrayList<E>(items.length);
        for (int i = 0; i < items.length; i++) {
            l.add(items[i]);
        }
        return l;
    }
}

interface Queue<E> extends Collection<E> {
    boolean offer(E e);

    E poll();

    E peek();

    E remove();
}

interface Deque<E> extends Queue<E> {
    void addFirst(E e);

    void addLast(E e);

    E pollFirst();

    E pollLast();

    E peekFirst();

    E peekLast();

    E removeFirst();

    E removeLast();

    void push(E e);

    E pop();
}

interface Set<E> extends Collection<E> {
    boolean remove(Object o);

    void clear();
}

class ArrayList<E> implements List<E> {
    Object[] elems;
    int size;

    public ArrayList() {
        elems = new Object[8];
    }

    public ArrayList(int capacity) {
        elems = new Object[Math.max(capacity, 1)];
    }

    public ArrayList(Collection<E> other) {
        elems = new Object[Math.max(other.size(), 1)];
        for (E e : other) {
            add(e);
        }
    }

    void grow() {
        Object[] n = new Object[elems.length * 2];
        for (int i = 0; i < size; i++) {
            n[i] = elems[i];
        }
        elems = n;
    }

    private void check(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length "
                            + size);
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean add(E e) {
        if (size == elems.length) {
            grow();
        }
        elems[size] = e;
        size++;
        return true;
    }

    public void add(int index, E e) {
        if (size == elems.length) {
            grow();
        }
        for (int i = size; i > index; i--) {
            elems[i] = elems[i - 1];
        }
        elems[index] = e;
        size++;
    }

    public E get(int index) {
        check(index);
        return (E) elems[index];
    }

    public E set(int index, E e) {
        check(index);
        E old = (E) elems[index];
        elems[index] = e;
        return old;
    }

    public E remove(int index) {
        check(index);
        E old = (E) elems[index];
        for (int i = index; i < size - 1; i++) {
            elems[i] = elems[i + 1];
        }
        size--;
        elems[size] = null;
        return old;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(o, elems[i])) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            elems[i] = null;
        }
        size = 0;
    }

    public Iterator<E> iterator() {
        return new ArrayListIterator<E>(this);
    }

    public boolean equals(Object o) {
        if (!(o instanceof List)) {
            return false;
        }
        List<E> other = (List<E>) o;
        if (other.size() != size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!Objects.equals(elems[i], other.get(i))) {
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        int h = 1;
        for (int i = 0; i < size; i++) {
            h = 31 * h + Objects.hashCode(elems[i]);
        }
        return h;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(elems[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}

final class ArrayListIterator<E> implements Iterator<E> {
    final ArrayList<E> list;
    int index;

    ArrayListIterator(ArrayList<E> list) {
        this.list = list;
    }

    public boolean hasNext() {
        return index < list.size;
    }

    public E next() {
        E e = (E) list.elems[index];
        index++;
        return e;
    }
}

final class LinkedNode<E> {
    E value;
    LinkedNode<E> prev;
    LinkedNode<E> next;

    LinkedNode(E value) {
        this.value = value;
    }
}

class LinkedList<E> implements List<E>, Deque<E> {
    LinkedNode<E> head;
    LinkedNode<E> tail;
    int size;

    public LinkedList() {
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void addFirst(E e) {
        LinkedNode<E> n = new LinkedNode<E>(e);
        n.next = head;
        if (head != null) {
            head.prev = n;
        } else {
            tail = n;
        }
        head = n;
        size++;
    }

    public void addLast(E e) {
        LinkedNode<E> n = new LinkedNode<E>(e);
        n.prev = tail;
        if (tail != null) {
            tail.next = n;
        } else {
            head = n;
        }
        tail = n;
        size++;
    }

    public boolean add(E e) {
        addLast(e);
        return true;
    }

    public boolean offer(E e) {
        addLast(e);
        return true;
    }

    public void push(E e) {
        addFirst(e);
    }

    public E peekFirst() {
        return head == null ? null : head.value;
    }

    public E peekLast() {
        return tail == null ? null : tail.value;
    }

    public E peek() {
        return peekFirst();
    }

    public E pollFirst() {
        if (head == null) {
            return null;
        }
        return unlink(head);
    }

    public E pollLast() {
        if (tail == null) {
            return null;
        }
        return unlink(tail);
    }

    public E poll() {
        return pollFirst();
    }

    public E removeFirst() {
        if (head == null) {
            throw new NoSuchElementException();
        }
        return unlink(head);
    }

    public E removeLast() {
        if (tail == null) {
            throw new NoSuchElementException();
        }
        return unlink(tail);
    }

    public E remove() {
        return removeFirst();
    }

    public E pop() {
        return removeFirst();
    }

    public E getFirst() {
        if (head == null) {
            throw new NoSuchElementException();
        }
        return head.value;
    }

    public E getLast() {
        if (tail == null) {
            throw new NoSuchElementException();
        }
        return tail.value;
    }

    E unlink(LinkedNode<E> n) {
        if (n.prev != null) {
            n.prev.next = n.next;
        } else {
            head = n.next;
        }
        if (n.next != null) {
            n.next.prev = n.prev;
        } else {
            tail = n.prev;
        }
        size--;
        return n.value;
    }

    LinkedNode<E> node(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        LinkedNode<E> n = head;
        for (int i = 0; i < index; i++) {
            n = n.next;
        }
        return n;
    }

    public E get(int index) {
        return node(index).value;
    }

    public E set(int index, E e) {
        LinkedNode<E> n = node(index);
        E old = n.value;
        n.value = e;
        return old;
    }

    public void add(int index, E e) {
        if (index == size) {
            addLast(e);
            return;
        }
        LinkedNode<E> at = node(index);
        LinkedNode<E> n = new LinkedNode<E>(e);
        n.next = at;
        n.prev = at.prev;
        if (at.prev != null) {
            at.prev.next = n;
        } else {
            head = n;
        }
        at.prev = n;
        size++;
    }

    public E remove(int index) {
        return unlink(node(index));
    }

    public boolean remove(Object o) {
        for (LinkedNode<E> n = head; n != null; n = n.next) {
            if (Objects.equals(o, n.value)) {
                unlink(n);
                return true;
            }
        }
        return false;
    }

    public int indexOf(Object o) {
        int i = 0;
        for (LinkedNode<E> n = head; n != null; n = n.next) {
            if (Objects.equals(o, n.value)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    public Iterator<E> iterator() {
        return new LinkedListIterator<E>(head);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (LinkedNode<E> n = head; n != null; n = n.next) {
            if (n != head) {
                sb.append(", ");
            }
            sb.append(String.valueOf(n.value));
        }
        sb.append(']');
        return sb.toString();
    }
}

final class LinkedListIterator<E> implements Iterator<E> {
    LinkedNode<E> node;

    LinkedListIterator(LinkedNode<E> start) {
        node = start;
    }

    public boolean hasNext() {
        return node != null;
    }

    public E next() {
        E v = node.value;
        node = node.next;
        return v;
    }
}

class ArrayDeque<E> extends LinkedList<E> {
    public ArrayDeque() {
    }
}

class Stack<E> extends ArrayList<E> {
    public Stack() {
    }

    public E push(E e) {
        add(e);
        return e;
    }

    public E pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        return remove(size - 1);
    }

    public E peek() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        return get(size - 1);
    }

    public boolean empty() {
        return size == 0;
    }
}

final class Objects {
    public static boolean equals(Object a, Object b) {
        return a == b || a != null && a.equals(b);
    }

    public static int hashCode(Object o) {
        return o == null ? 0 : o.hashCode();
    }

    public static int hash(Object a, Object b) {
        return 31 * (31 + hashCode(a)) + hashCode(b);
    }

    public static boolean isNull(Object o) {
        return o == null;
    }

    public static boolean nonNull(Object o) {
        return o != null;
    }

    public static <T> T requireNonNull(T o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }

    public static String toString(Object o) {
        return String.valueOf(o);
    }
}

interface Map<K, V> {
    V get(Object key);

    V put(K key, V value);

    boolean containsKey(Object key);

    V remove(Object key);

    int size();

    boolean isEmpty();

    V getOrDefault(Object key, V defaultValue);

    Set<K> keySet();

    Collection<V> values();

    Set<Map.Entry<K, V>> entrySet();

    void clear();

    default V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            put(key, value);
        }
        return v;
    }

    default V merge(K key, V value, BiFunction<V, V, V> f) {
        V old = get(key);
        V nv = old == null ? value : f.apply(old, value);
        put(key, nv);
        return nv;
    }

    interface Entry<K, V> {
        K getKey();

        V getValue();

        V setValue(V v);
    }
}

final class HashMapNode<K, V> implements Map.Entry<K, V> {
    final K key;
    V value;
    final int hash;
    HashMapNode<K, V> next;
    HashMapNode<K, V> after;
    HashMapNode<K, V> before;

    HashMapNode(K key, V value, int hash) {
        this.key = key;
        this.value = value;
        this.hash = hash;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public V setValue(V v) {
        V old = value;
        value = v;
        return old;
    }

    public String toString() {
        return key + "=" + value;
    }
}

/**
 * Hash map with the same bucket layout, resize policy and therefore iteration order as
 * java.util.HashMap (minus treeification of long chains).
 */
class HashMap<K, V> implements Map<K, V> {
    HashMapNode<K, V>[] table;
    HashMapNode<K, V> first;
    HashMapNode<K, V> last;
    int size;

    public HashMap() {
        table = new HashMapNode[16];
    }

    public HashMap(int capacity) {
        int n = 1;
        while (n < capacity) {
            n = n * 2;
        }
        table = new HashMapNode[n];
    }

    public HashMap(Map<K, V> other) {
        table = new HashMapNode[16];
        for (Map.Entry<K, V> e : other.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    static int spread(Object key) {
        int h = key == null ? 0 : key.hashCode();
        return h ^ (h >>> 16);
    }

    HashMapNode<K, V> find(Object key) {
        int h = spread(key);
        for (HashMapNode<K, V> n = table[h & (table.length - 1)]; n != null; n = n.next) {
            if (n.hash == h && Objects.equals(n.key, key)) {
                return n;
            }
        }
        return null;
    }

    public V get(Object key) {
        HashMapNode<K, V> n = find(key);
        return n == null ? null : n.value;
    }

    public V getOrDefault(Object key, V defaultValue) {
        HashMapNode<K, V> n = find(key);
        return n == null ? defaultValue : n.value;
    }

    public boolean containsKey(Object key) {
        return find(key) != null;
    }

    public boolean containsValue(Object value) {
        for (HashMapNode<K, V> n : nodes()) {
            if (Objects.equals(n.value, value)) {
                return true;
            }
        }
        return false;
    }

    public V put(K key, V value) {
        int h = spread(key);
        int b = h & (table.length - 1);
        HashMapNode<K, V> tail = null;
        for (HashMapNode<K, V> n = table[b]; n != null; n = n.next) {
            if (n.hash == h && Objects.equals(n.key, key)) {
                V old = n.value;
                n.value = value;
                return old;
            }
            tail = n;
        }
        HashMapNode<K, V> n = new HashMapNode<K, V>(key, value, h);
        if (tail == null) {
            table[b] = n;
        } else {
            tail.next = n;
        }
        if (last == null) {
            first = n;
        } else {
            last.after = n;
            n.before = last;
        }
        last = n;
        size++;
        if (size > table.length - table.length / 4) {
            resize();
        }
        return null;
    }

    /** Doubles the table; buckets split in order, exactly like java.util.HashMap. */
    void resize() {
        HashMapNode<K, V>[] old = table;
        HashMapNode<K, V>[] nt = new HashMapNode[old.length * 2];
        HashMapNode<K, V>[] tails = new HashMapNode[old.length * 2];
        for (int i = 0; i < old.length; i++) {
            HashMapNode<K, V> n = old[i];
            while (n != null) {
                HashMapNode<K, V> next = n.next;
                n.next = null;
                int b = n.hash & (nt.length - 1);
                if (tails[b] == null) {
                    nt[b] = n;
                } else {
                    tails[b].next = n;
                }
                tails[b] = n;
                n = next;
            }
        }
        table = nt;
    }

    public V remove(Object key) {
        int h = spread(key);
        int b = h & (table.length - 1);
        HashMapNode<K, V> prev = null;
        for (HashMapNode<K, V> n = table[b]; n != null; n = n.next) {
            if (n.hash == h && Objects.equals(n.key, key)) {
                if (prev == null) {
                    table[b] = n.next;
                } else {
                    prev.next = n.next;
                }
                if (n.before == null) {
                    first = n.after;
                } else {
                    n.before.after = n.after;
                }
                if (n.after == null) {
                    last = n.before;
                } else {
                    n.after.before = n.before;
                }
                size--;
                return n.value;
            }
            prev = n;
        }
        return null;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        table = new HashMapNode[16];
        first = null;
        last = null;
        size = 0;
    }

    /** Entries in iteration order: bucket order (HashMap) or insertion order (Linked). */
    ArrayList<HashMapNode<K, V>> nodes() {
        ArrayList<HashMapNode<K, V>> out = new ArrayList<HashMapNode<K, V>>(size);
        for (int i = 0; i < table.length; i++) {
            for (HashMapNode<K, V> n = table[i]; n != null; n = n.next) {
                out.add(n);
            }
        }
        return out;
    }

    public Set<K> keySet() {
        LinkedHashSet<K> s = new LinkedHashSet<K>();
        for (HashMapNode<K, V> n : nodes()) {
            s.add(n.key);
        }
        return s;
    }

    public Collection<V> values() {
        ArrayList<V> l = new ArrayList<V>();
        for (HashMapNode<K, V> n : nodes()) {
            l.add(n.value);
        }
        return l;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        LinkedHashSet<Map.Entry<K, V>> s = new LinkedHashSet<Map.Entry<K, V>>();
        for (HashMapNode<K, V> n : nodes()) {
            s.add(n);
        }
        return s;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean firstEntry = true;
        for (HashMapNode<K, V> n : nodes()) {
            if (!firstEntry) {
                sb.append(", ");
            }
            firstEntry = false;
            sb.append(String.valueOf(n.key));
            sb.append('=');
            sb.append(String.valueOf(n.value));
        }
        sb.append('}');
        return sb.toString();
    }
}

class LinkedHashMap<K, V> extends HashMap<K, V> {
    public LinkedHashMap() {
    }

    ArrayList<HashMapNode<K, V>> nodes() {
        ArrayList<HashMapNode<K, V>> out = new ArrayList<HashMapNode<K, V>>(size);
        for (HashMapNode<K, V> n = first; n != null; n = n.after) {
            out.add(n);
        }
        return out;
    }
}

/** Sorted map: hash lookup plus sorted iteration (natural order or a comparator). */
class TreeMap<K, V> extends HashMap<K, V> {
    final Comparator<K> comparator;

    public TreeMap() {
        comparator = new NaturalOrder<K>();
    }

    public TreeMap(Comparator<K> c) {
        comparator = c;
    }

    ArrayList<HashMapNode<K, V>> nodes() {
        ArrayList<HashMapNode<K, V>> out = new ArrayList<HashMapNode<K, V>>(size);
        for (HashMapNode<K, V> n = first; n != null; n = n.after) {
            out.add(n);
        }
        out.sort(new EntryKeyOrder<K, V>(comparator));
        return out;
    }

    public K firstKey() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return nodes().get(0).key;
    }

    public K lastKey() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return nodes().get(size - 1).key;
    }
}

final class EntryKeyOrder<K, V> implements Comparator<HashMapNode<K, V>> {
    final Comparator<K> keys;

    EntryKeyOrder(Comparator<K> keys) {
        this.keys = keys;
    }

    public int compare(HashMapNode<K, V> a, HashMapNode<K, V> b) {
        return keys.compare(a.key, b.key);
    }
}

class HashSet<E> implements Set<E> {
    final HashMap<E, Boolean> map;

    public HashSet() {
        map = new HashMap<E, Boolean>();
    }

    HashSet(HashMap<E, Boolean> backing) {
        map = backing;
    }

    public HashSet(Collection<E> other) {
        map = new HashMap<E, Boolean>();
        for (E e : other) {
            add(e);
        }
    }

    public boolean add(E e) {
        if (map.containsKey(e)) {
            return false;
        }
        map.put(e, null);
        return true;
    }

    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    public boolean remove(Object o) {
        if (!map.containsKey(o)) {
            return false;
        }
        map.remove(o);
        return true;
    }

    public int size() {
        return map.size;
    }

    public boolean isEmpty() {
        return map.size == 0;
    }

    public void clear() {
        map.clear();
    }

    public Iterator<E> iterator() {
        return new HashSetIterator<E>(map.nodes());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (HashMapNode<E, Boolean> n : map.nodes()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(String.valueOf(n.key));
        }
        sb.append(']');
        return sb.toString();
    }
}

class LinkedHashSet<E> extends HashSet<E> {
    public LinkedHashSet() {
        super(new LinkedHashMap<E, Boolean>());
    }
}

class TreeSet<E> extends HashSet<E> {
    public TreeSet() {
        super(new TreeMap<E, Boolean>());
    }

    public TreeSet(Comparator<E> c) {
        super(new TreeMap<E, Boolean>(c));
    }

    public E first() {
        return ((TreeMap<E, Boolean>) map).firstKey();
    }

    public E last() {
        return ((TreeMap<E, Boolean>) map).lastKey();
    }
}

final class HashSetIterator<E> implements Iterator<E> {
    final ArrayList<HashMapNode<E, Boolean>> nodes;
    int index;

    HashSetIterator(ArrayList<HashMapNode<E, Boolean>> nodes) {
        this.nodes = nodes;
    }

    public boolean hasNext() {
        return index < nodes.size;
    }

    public E next() {
        E k = nodes.get(index).key;
        index++;
        return k;
    }
}

interface Function<T, R> {
    R apply(T t);
}

interface BiFunction<T, U, R> {
    R apply(T t, U u);
}

interface Supplier<T> {
    T get();
}

interface Consumer<T> {
    void accept(T t);
}

interface Predicate<T> {
    boolean test(T t);
}

interface IntBinaryOperator {
    int applyAsInt(int a, int b);
}

interface IntUnaryOperator {
    int applyAsInt(int a);
}

interface IntPredicate {
    boolean test(int a);
}

final class Collections {
    public static <T> void sort(List<T> list) {
        sortList(list, new NaturalOrder<T>());
    }

    public static <T> void sort(List<T> list, Comparator<T> c) {
        sortList(list, c);
    }

    /** Stable insertion-merge sort through an array copy. */
    static <T> void sortList(List<T> list, Comparator<T> c) {
        int n = list.size();
        Object[] a = new Object[n];
        for (int i = 0; i < n; i++) {
            a[i] = list.get(i);
        }
        Arrays.mergeSort(a, new Object[n], 0, n, (Comparator<Object>) c);
        for (int i = 0; i < n; i++) {
            list.set(i, (T) a[i]);
        }
    }

    public static <T> void reverse(List<T> list) {
        for (int i = 0, j = list.size() - 1; i < j; i++, j--) {
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }

    public static <T> void swap(List<T> list, int i, int j) {
        T t = list.get(i);
        list.set(i, list.get(j));
        list.set(j, t);
    }

    public static <T> T max(Collection<T> c) {
        T best = null;
        for (T e : c) {
            if (best == null || ((Comparable) e).compareTo(best) > 0) {
                best = e;
            }
        }
        return best;
    }

    public static <T> T min(Collection<T> c) {
        T best = null;
        for (T e : c) {
            if (best == null || ((Comparable) e).compareTo(best) < 0) {
                best = e;
            }
        }
        return best;
    }
}

final class Arrays {
    public static String toString(int[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static String toString(long[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static String toString(char[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static String toString(boolean[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static String toString(Object[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(a[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public static String deepToString(Object[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(toString((int[]) a[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public static void fill(int[] a, int v) {
        for (int i = 0; i < a.length; i++) {
            a[i] = v;
        }
    }

    public static void fill(long[] a, long v) {
        for (int i = 0; i < a.length; i++) {
            a[i] = v;
        }
    }

    public static void fill(char[] a, char v) {
        for (int i = 0; i < a.length; i++) {
            a[i] = v;
        }
    }

    public static void fill(boolean[] a, boolean v) {
        for (int i = 0; i < a.length; i++) {
            a[i] = v;
        }
    }

    public static void fill(Object[] a, Object v) {
        for (int i = 0; i < a.length; i++) {
            a[i] = v;
        }
    }

    public static int[] copyOf(int[] a, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n && i < a.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static char[] copyOf(char[] a, int n) {
        char[] r = new char[n];
        for (int i = 0; i < n && i < a.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static int[] copyOfRange(int[] a, int from, int to) {
        int[] r = new int[to - from];
        for (int i = from; i < to; i++) {
            r[i - from] = a[i];
        }
        return r;
    }

    public static boolean equals(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean equals(char[] a, char[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public static int binarySearch(int[] a, int key) {
        int lo = 0;
        int hi = a.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < key) {
                lo = mid + 1;
            } else if (a[mid] > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -(lo + 1);
    }

    public static void sort(int[] a) {
        sort(a, 0, a.length);
    }

    /** Quicksort with insertion sort for short ranges. */
    public static void sort(int[] a, int from, int to) {
        while (to - from > 12) {
            int mid = (from + to) >>> 1;
            int p = a[mid];
            int i = from;
            int j = to - 1;
            while (i <= j) {
                while (a[i] < p) {
                    i++;
                }
                while (a[j] > p) {
                    j--;
                }
                if (i <= j) {
                    int t = a[i];
                    a[i] = a[j];
                    a[j] = t;
                    i++;
                    j--;
                }
            }
            if (j + 1 - from < to - i) {
                sort(a, from, j + 1);
                from = i;
            } else {
                sort(a, i, to);
                to = j + 1;
            }
        }
        for (int i = from + 1; i < to; i++) {
            int v = a[i];
            int j = i - 1;
            while (j >= from && a[j] > v) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = v;
        }
    }

    public static void sort(long[] a) {
        for (int i = 1; i < a.length; i++) {
            long v = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > v) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = v;
        }
    }

    public static void sort(char[] a) {
        for (int i = 1; i < a.length; i++) {
            char v = a[i];
            int j = i - 1;
            while (j >= 0 && a[j] > v) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = v;
        }
    }

    public static void sort(Object[] a) {
        mergeSort(a, new Object[a.length], 0, a.length, new NaturalOrder<Object>());
    }

    public static <T> void sort(T[] a, Comparator<T> c) {
        mergeSort(a, new Object[a.length], 0, a.length, (Comparator<Object>) c);
    }

    static void mergeSort(Object[] a, Object[] tmp, int from, int to, Comparator<Object> c) {
        if (to - from < 2) {
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(a, tmp, from, mid, c);
        mergeSort(a, tmp, mid, to, c);
        if (c.compare(a[mid - 1], a[mid]) <= 0) {
            return;
        }
        int i = from;
        int j = mid;
        int k = from;
        while (i < mid && j < to) {
            if (c.compare(a[j], a[i]) < 0) {
                tmp[k] = a[j];
                j++;
            } else {
                tmp[k] = a[i];
                i++;
            }
            k++;
        }
        while (i < mid) {
            tmp[k] = a[i];
            i++;
            k++;
        }
        while (j < to) {
            tmp[k] = a[j];
            j++;
            k++;
        }
        for (k = from; k < to; k++) {
            a[k] = tmp[k];
        }
    }

    public static <T> List<T> asList(T... items) {
        ArrayList<T> l = new ArrayList<T>(items.length);
        for (int i = 0; i < items.length; i++) {
            l.add(items[i]);
        }
        return l;
    }
}

class Random {
    int seed;

    public Random() {
        seed = 42;
    }

    public Random(long seed) {
        this.seed = (int) seed;
    }

    public int nextInt() {
        seed = seed * 1103515245 + 12345;
        return seed;
    }

    public int nextInt(int bound) {
        int r = (nextInt() >>> 8) % bound;
        return r;
    }

    public boolean nextBoolean() {
        return (nextInt() >>> 12 & 1) != 0;
    }

    public double nextDouble() {
        return (nextInt() >>> 8 & 16777215) / 16777216.0;
    }

    public float nextFloat() {
        return (nextInt() >>> 8 & 16777215) / 16777216.0f;
    }

    public double nextGaussian() {
        double u = 1.0 - nextDouble();
        double v = nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }
}

class NoSuchElementException extends RuntimeException {
    public NoSuchElementException() {
    }

    public NoSuchElementException(String m) {
        super(m);
    }
}

class InputMismatchException extends NoSuchElementException {
    public InputMismatchException() {
    }

    public InputMismatchException(String m) {
        super(m);
    }
}

class EmptyStackException extends RuntimeException {
}

class ConcurrentModificationException extends RuntimeException {
}

class IOException extends Exception {
    public IOException() {
    }

    public IOException(String m) {
        super(m);
    }
}

interface Closeable extends AutoCloseable {
    void close();
}

class InputStream implements Closeable {
    /** One byte of pushback shared by all readers on stdin. */
    static int peeked = -2;

    public int read() {
        if (peeked != -2) {
            int c = peeked;
            peeked = -2;
            return c;
        }
        return Bf.getChar();
    }

    static int peek() {
        if (peeked == -2) {
            peeked = Bf.getChar();
        }
        return peeked;
    }

    public void close() {
    }
}

class InputStreamReader implements Closeable {
    public InputStreamReader(InputStream in) {
    }

    public int read() {
        return System.in.read();
    }

    public void close() {
    }
}

class BufferedReader implements Closeable {
    public BufferedReader(InputStreamReader reader) {
    }

    public int read() {
        return System.in.read();
    }

    public String readLine() {
        int c = System.in.read();
        if (c < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        while (c >= 0 && c != '\n') {
            if (c != '\r') {
                sb.append((char) c);
            }
            c = System.in.read();
        }
        return sb.toString();
    }

    public void close() {
    }
}

class Scanner implements Closeable {
    public Scanner(InputStream in) {
    }

    static void skipWhitespace() {
        int c = InputStream.peek();
        while (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
            System.in.read();
            c = InputStream.peek();
        }
    }

    public boolean hasNext() {
        skipWhitespace();
        return InputStream.peek() >= 0;
    }

    public boolean hasNextInt() {
        skipWhitespace();
        int c = InputStream.peek();
        return c >= '0' && c <= '9' || c == '-';
    }

    public boolean hasNextLine() {
        return InputStream.peek() >= 0;
    }

    public String next() {
        skipWhitespace();
        if (InputStream.peek() < 0) {
            throw new NoSuchElementException();
        }
        StringBuilder sb = new StringBuilder();
        int c = InputStream.peek();
        while (c >= 0 && c != ' ' && c != '\n' && c != '\t' && c != '\r') {
            sb.append((char) System.in.read());
            c = InputStream.peek();
        }
        return sb.toString();
    }

    public int nextInt() {
        String s = next();
        return Integer.parseInt(s);
    }

    public long nextLong() {
        return nextInt();
    }

    public double nextDouble() {
        return Double.parseDouble(next());
    }

    public float nextFloat() {
        return Float.parseFloat(next());
    }

    public boolean hasNextDouble() {
        skipWhitespace();
        int c = InputStream.peek();
        return c >= '0' && c <= '9' || c == '-' || c == '.' || c == '+';
    }

    public boolean nextBoolean() {
        return Boolean.parseBoolean(next());
    }

    public String nextLine() {
        StringBuilder sb = new StringBuilder();
        int c = System.in.read();
        if (c < 0) {
            throw new NoSuchElementException("No line found");
        }
        while (c >= 0 && c != '\n') {
            if (c != '\r') {
                sb.append((char) c);
            }
            c = System.in.read();
        }
        return sb.toString();
    }

    public void close() {
    }
}

class StringTokenizer {
    final String[] tokens;
    int pos;

    public StringTokenizer(String s) {
        tokens = s.split("\\s+");
    }

    public StringTokenizer(String s, String delim) {
        tokens = s.split(delim);
    }

    public boolean hasMoreTokens() {
        return pos < tokens.length;
    }

    public String nextToken() {
        String t = tokens[pos];
        pos++;
        return t;
    }

    public int countTokens() {
        return tokens.length - pos;
    }
}
