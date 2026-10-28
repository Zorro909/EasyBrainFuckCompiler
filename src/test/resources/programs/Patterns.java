import java.util.*;

/** Pattern matching: type patterns, record deconstruction, guards, sealed hierarchies. */
public class Patterns {
    sealed interface Expr permits Num, Add, Mul, Neg {}
    record Num(int v) implements Expr {}
    record Add(Expr l, Expr r) implements Expr {}
    record Mul(Expr l, Expr r) implements Expr {}
    record Neg(Expr e) implements Expr {}
    record Pair<A, B>(A first, B second) {}

    static int eval(Expr e) {
        return switch (e) {
            case Num n -> n.v();
            case Add(Expr l, Expr r) -> eval(l) + eval(r);
            case Mul(Num(var a), Num(var b)) -> a * b;
            case Mul m -> eval(m.l()) * eval(m.r());
            case Neg(var inner) -> -eval(inner);
        };
    }

    static Expr simplify(Expr e) {
        return switch (e) {
            case Add(Num(int a), Num(int b)) -> new Num(a + b);
            case Mul(Num n, Expr r) when n.v() == 1 -> simplify(r);
            case Mul(Num n, Expr r) when n.v() == 0 -> new Num(0);
            case Add(var l, var r) -> new Add(simplify(l), simplify(r));
            case Mul(var l, var r) -> new Mul(simplify(l), simplify(r));
            case Neg(Neg(var x)) -> simplify(x);
            default -> e;
        };
    }

    static String describe(Object o) {
        switch (o) {
            case null:
                return "null!";
            case Integer i when i > 100:
                return "big " + i;
            case Integer i:
                return "int " + i;
            case String s:
                return "string of " + s.length();
            case Pair<?, ?>(String k, Integer v):
                return k + " -> " + v;
            default:
                return "other " + o;
        }
    }

    public static void main(String[] args) {
        Expr e = new Add(new Mul(new Num(1), new Add(new Num(2), new Num(3))),
                        new Neg(new Neg(new Mul(new Num(4), new Num(5)))));
        System.out.println(e);
        System.out.println(eval(e));
        Expr s = simplify(e);
        System.out.println(s + " = " + eval(s));
        Object[] things = {42, 1000, "hello", new Pair<>("k", 7), new Pair<>(1, 2), null};
        for (Object o : things) {
            System.out.println(describe(o));
        }
        Object o = new Pair<>("x", new Num(9));
        if (o instanceof Pair<?, ?>(String name, Num(int v)) && v > 5) {
            System.out.println(name + "=" + v);
        }
        if (!(o instanceof Num n)) {
            System.out.println("not a num");
        }
    }
}
