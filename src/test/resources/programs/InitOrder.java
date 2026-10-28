public class InitOrder {
    static int counter = next("static field");
    static final int[] TABLE;

    static {
        TABLE = new int[5];
        for (int i = 0; i < TABLE.length; i++) {
            TABLE[i] = i * i;
        }
        System.out.println("static block, counter=" + counter);
    }

    static int next(String what) {
        System.out.println("init " + what);
        return ++counter;
    }

    int a = next("instance field a");
    int b;

    {
        b = a * 10;
        System.out.println("instance block b=" + b);
    }

    InitOrder() {
        this(7);
        System.out.println("no-arg constructor");
    }

    InitOrder(int x) {
        System.out.println("int constructor " + x + " a=" + a + " b=" + b);
    }

    static class Parent {
        Parent() {
            System.out.println("Parent()");
            hello();
        }

        void hello() {
            System.out.println("Parent.hello");
        }
    }

    static class Child extends Parent {
        String name = "child";

        Child() {
            super();
            System.out.println("Child() " + name);
        }

        @Override
        void hello() {
            System.out.println("Child.hello name=" + name);
        }
    }

    public static void main(String[] args) {
        System.out.println("main " + TABLE[4]);
        new InitOrder();
        new Child();
    }
}
