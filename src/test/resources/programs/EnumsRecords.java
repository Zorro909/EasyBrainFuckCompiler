public class EnumsRecords {
    enum Color {
        RED, GREEN, BLUE;

        Color next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    enum Planet {
        MERCURY(3, 38), EARTH(6, 100), JUPITER(71, 253);

        final int radius;
        final int gravity;

        Planet(int radius, int gravity) {
            this.radius = radius;
            this.gravity = gravity;
        }

        int weight(int mass) {
            return mass * gravity / 100;
        }
    }

    record Point(int x, int y) {
        Point {
            if (x < 0) {
                throw new IllegalArgumentException("negative");
            }
        }

        int dist() {
            return Math.abs(x) + Math.abs(y);
        }
    }

    record Pair<A, B>(A first, B second) {
    }

    public static void main(String[] args) {
        for (Color c : Color.values()) {
            System.out.println(c + " " + c.ordinal() + " -> " + c.next());
        }
        Color c = Color.valueOf("GREEN");
        switch (c) {
            case RED -> System.out.println("red!");
            case GREEN -> System.out.println("green!");
            default -> System.out.println("other");
        }
        System.out.println(c == Color.GREEN);
        System.out.println(c.compareTo(Color.BLUE) + " " + c.name());
        for (Planet p : Planet.values()) {
            System.out.println(p + " " + p.radius + " " + p.weight(70));
        }
        Point p = new Point(3, 4);
        Point q = new Point(3, 4);
        System.out.println(p + " " + p.equals(q) + " " + (p == q) + " " + p.dist() + " "
                        + (p.hashCode() == q.hashCode()) + " " + p.x());
        Pair<String, Integer> pair = new Pair<>("answer", 42);
        System.out.println(pair + " " + pair.first().length() + " " + (pair.second() + 1));
    }
}
