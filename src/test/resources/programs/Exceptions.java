public class Exceptions {
    static int checkedDivide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return a / b;
    }

    public static void main(String[] args) {
        System.out.println(checkedDivide(10, 3));
        System.out.println("before");
        checkedDivide(1, 0);
        System.out.println("never printed");
    }
}
