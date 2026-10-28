public class InnerClasses {
    private int counter = 0;
    private final String name;

    InnerClasses(String name) {
        this.name = name;
    }

    class Incrementer {
        int step;

        Incrementer(int step) {
            this.step = step;
        }

        void apply() {
            counter += step;
        }

        String owner() {
            return name + ":" + counter;
        }
    }

    static class Static {
        static int twice(int x) {
            return 2 * x;
        }
    }

    void run() {
        Incrementer a = new Incrementer(2);
        Incrementer b = new Incrementer(5);
        a.apply();
        b.apply();
        a.apply();
        System.out.println(a.owner() + " " + this.counter);
        Runnable r = new Runnable() {
            public void run() {
                counter *= 10;
                System.out.println("anon sees " + counter + " of " + name);
            }
        };
        r.run();
        System.out.println(counter);
    }

    public static void main(String[] args) {
        new InnerClasses("outer").run();
        System.out.println(Static.twice(21));
    }
}
