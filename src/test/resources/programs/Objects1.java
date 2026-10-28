import java.util.ArrayList;
import java.util.List;

public class Objects1 {
    interface Shape {
        int area();

        default String describe() {
            return name() + " with area " + area();
        }

        String name();
    }

    static abstract class Base implements Shape {
        static int created = 0;
        protected final int id;

        Base() {
            created++;
            id = created;
        }

        public String name() {
            return getClass_() + "#" + id;
        }

        abstract String getClass_();
    }

    static class Rect extends Base {
        final int w, h;

        Rect(int w, int h) {
            super();
            this.w = w;
            this.h = h;
        }

        public int area() {
            return w * h;
        }

        String getClass_() {
            return "Rect";
        }
    }

    static class Square extends Rect {
        Square(int s) {
            super(s, s);
        }

        @Override
        String getClass_() {
            return "Square";
        }

        @Override
        public String describe() {
            return "square! " + super.describe();
        }
    }

    static class Circle extends Base {
        final int r;

        Circle(int r) {
            this.r = r;
        }

        public int area() {
            return 314 * r * r / 100;
        }

        String getClass_() {
            return "Circle";
        }
    }

    static class Point {
        final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point)) {
                return false;
            }
            Point p = (Point) o;
            return p.x == x && p.y == y;
        }

        @Override
        public int hashCode() {
            return x * 31 + y;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        Point plus(Point o) {
            return new Point(x + o.x, y + o.y);
        }
    }

    static class Counter {
        private int value;

        Counter increment() {
            value++;
            return this;
        }

        int get() {
            return value;
        }
    }

    public static void main(String[] args) {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Rect(3, 4));
        shapes.add(new Square(5));
        shapes.add(new Circle(10));
        int total = 0;
        for (Shape s : shapes) {
            System.out.println(s.describe());
            total += s.area();
        }
        System.out.println("total " + total + ", created " + Base.created);
        Point p = new Point(1, 2).plus(new Point(10, 20));
        System.out.println(p + " " + p.equals(new Point(11, 22)) + " " + p.equals("x")
                        + " " + (p.hashCode() == new Point(11, 22).hashCode()));
        Counter c = new Counter();
        c.increment().increment().increment();
        System.out.println(c.get());
        Object o = shapes.get(1);
        if (o instanceof Rect r) {
            System.out.println("rect " + r.w + "x" + r.h);
        }
        System.out.println(o instanceof Circle);
        System.out.println(o instanceof Shape);
        Object nothing = null;
        System.out.println(nothing instanceof Point);
    }
}
