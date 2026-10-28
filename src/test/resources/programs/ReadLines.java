import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ReadLines {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            StringBuilder rev = new StringBuilder(line).reverse();
            System.out.println(lineNo + ": " + rev + " (" + line.length() + ")");
        }
        int c = System.in.read();
        System.out.println("eof " + c);
    }
}
