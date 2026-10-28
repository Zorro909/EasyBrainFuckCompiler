import java.util.ArrayList;
import java.util.List;

public class Shapes {
    interface Visitor<R> {
        R visitCircle(Circle c);

        R visitRect(Rect r);
    }

    static abstract class Shape implements Comparable<Shape> {
        abstract int area();

        abstract <R> R accept(Visitor<R> v);

        public int compareTo(Shape o) {
            return Integer.compare(area(), o.area());
        }
    }

    static class Circle extends Shape {
        final int r;

        Circle(int r) {
            this.r = r;
        }

        int area() {
            return 3 * r * r;
        }

        <R> R accept(Visitor<R> v) {
            return v.visitCircle(this);
        }

        public String toString() {
            return "Circle(" + r + ")";
        }
    }

    static class Rect extends Shape {
        final int w, h;

        Rect(int w, int h) {
            this.w = w;
            this.h = h;
        }

        int area() {
            return w * h;
        }

        <R> R accept(Visitor<R> v) {
            return v.visitRect(this);
        }

        public String toString() {
            return "Rect(" + w + "x" + h + ")";
        }
    }

    public static void main(String[] args) {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Rect(4, 5));
        shapes.add(new Circle(2));
        shapes.add(new Rect(1, 30));
        shapes.add(new Circle(3));
        java.util.Collections.sort(shapes);
        System.out.println(shapes);
        Visitor<String> describe = new Visitor<>() {
            public String visitCircle(Circle c) {
                return "round " + c.r;
            }

            public String visitRect(Rect r) {
                return "square-ish " + r.w + "/" + r.h;
            }
        };
        Visitor<Integer> perimeter = new Visitor<>() {
            public Integer visitCircle(Circle c) {
                return 6 * c.r;
            }

            public Integer visitRect(Rect r) {
                return 2 * (r.w + r.h);
            }
        };
        int total = 0;
        for (Shape s : shapes) {
            System.out.println(s.accept(describe) + " p=" + s.accept(perimeter));
            total += s.accept(perimeter);
        }
        System.out.println(total);
        Shape biggest = java.util.Collections.max(shapes);
        System.out.println("biggest " + biggest + " " + biggest.area());
    }
}
