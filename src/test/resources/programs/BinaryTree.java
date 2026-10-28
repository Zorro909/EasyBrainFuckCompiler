public class BinaryTree {
    static class Node {
        int key;
        Node left, right;

        Node(int key) {
            this.key = key;
        }
    }

    Node root;
    int size;

    void insert(int key) {
        root = insert(root, key);
    }

    private Node insert(Node n, int key) {
        if (n == null) {
            size++;
            return new Node(key);
        }
        if (key < n.key) {
            n.left = insert(n.left, key);
        } else if (key > n.key) {
            n.right = insert(n.right, key);
        }
        return n;
    }

    boolean contains(int key) {
        Node n = root;
        while (n != null) {
            if (key == n.key) {
                return true;
            }
            n = key < n.key ? n.left : n.right;
        }
        return false;
    }

    int height(Node n) {
        return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right));
    }

    void inorder(Node n, StringBuilder sb) {
        if (n == null) {
            return;
        }
        inorder(n.left, sb);
        sb.append(n.key).append(' ');
        inorder(n.right, sb);
    }

    public static void main(String[] args) {
        BinaryTree t = new BinaryTree();
        int[] keys = {50, 30, 70, 20, 40, 60, 80, 35, 45, 65, 30, 50};
        for (int k : keys) {
            t.insert(k);
        }
        StringBuilder sb = new StringBuilder();
        t.inorder(t.root, sb);
        System.out.println(sb.toString().trim());
        System.out.println("size " + t.size + " height " + t.height(t.root));
        System.out.println(t.contains(45) + " " + t.contains(46));
    }
}
