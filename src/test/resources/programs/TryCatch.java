import java.util.ArrayList;
import java.util.List;

/** try/catch/finally semantics: propagation, finally on every exit, rethrow, resources. */
public class TryCatch {
    static class AppException extends Exception {
        final int code;

        AppException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    static class Resource implements AutoCloseable {
        final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        public void close() {
            System.out.println("close " + name);
        }
    }

    interface Op {
        int apply(int x) throws AppException;
    }

    static int depth(int n) throws AppException {
        if (n == 0) {
            throw new AppException("bottom reached", 42);
        }
        return depth(n - 1) + 1;
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            System.out.println("bad number: " + s);
            return -1;
        } finally {
            System.out.println("parsed " + s);
        }
    }

    static String finallyOverrides() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            try {
                if (i == 1) {
                    continue;
                }
                if (i == 3) {
                    break;
                }
                log.append("body").append(i).append(' ');
            } finally {
                log.append("fin").append(i).append(' ');
            }
        }
        return log.toString();
    }

    static int returnValueKept() {
        int x = 1;
        try {
            return x;
        } finally {
            x = 99;
        }
    }

    static void nested() {
        try {
            try {
                throw new IllegalStateException("inner");
            } catch (IllegalArgumentException e) {
                System.out.println("wrong handler");
            } finally {
                System.out.println("inner finally");
            }
        } catch (RuntimeException e) {
            System.out.println("outer caught " + e.getMessage());
        }
    }

    static void rethrow() throws AppException {
        try {
            depth(3);
        } catch (AppException e) {
            System.out.println("rethrowing " + e.code);
            throw new AppException("wrapped: " + e.getMessage(), e.code + 1);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(depth(5));
        } catch (AppException e) {
            System.out.println("caught " + e.getMessage() + " code " + e.code);
        }
        System.out.println(parse("123") + parse("12x"));
        System.out.println(finallyOverrides());
        System.out.println(returnValueKept());
        nested();
        try {
            rethrow();
        } catch (AppException e) {
            System.out.println(e.getMessage() + " " + e.code);
        }
        int[] data = {10, 0, 5};
        for (int d : data) {
            try {
                System.out.println(100 / d);
            } catch (ArithmeticException e) {
                System.out.println("div: " + e.getMessage());
            }
        }
        List<Op> ops = new ArrayList<>();
        ops.add(x -> x * 2);
        ops.add(x -> {
            if (x > 3) {
                throw new AppException("too big", x);
            }
            return x;
        });
        for (int i = 2; i <= 5; i += 3) {
            for (Op op : ops) {
                try {
                    System.out.println("op " + op.apply(i));
                } catch (AppException e) {
                    System.out.println("op failed " + e.code);
                }
            }
        }
        try (Resource a = new Resource("a"); Resource b = new Resource("b")) {
            System.out.println("using " + a.name + b.name);
            throw new UnsupportedOperationException("inside");
        } catch (UnsupportedOperationException | IllegalStateException e) {
            System.out.println("after close: " + e.getMessage());
        } finally {
            System.out.println("all done");
        }
        List<Integer> list = new ArrayList<>();
        try {
            list.get(3);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("index problem");
        }
        Object o = "str";
        try {
            Integer i = (Integer) o;
            System.out.println("cast ok " + i);
        } catch (ClassCastException e) {
            System.out.println("cast failed");
        }
        throw new IllegalStateException("the end");
    }
}
