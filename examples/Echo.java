import java.io.IOException;

public class Echo {

    public static void main(String[] args) throws IOException {
        // c > 0 works both transpiled (end of input reads 0) and on the JVM (-1)
        int c = System.in.read();
        while (c > 0) {
            System.out.print((char) c);
            c = System.in.read();
        }
    }
}
