package de.zorro909.brainfuck.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IoTest {

    @Test
    void printsStringLiteralsWithAndWithoutNewline() {
        var output = TranspilerTestSupport.runBody("""
                        System.out.print("Hello, ");
                        System.out.println("World!");
                        System.out.println();
                        """);
        assertEquals("Hello, World!\n\n", output);
    }

    @Test
    void printlnOfIntExpression() {
        assertEquals("8\n", TranspilerTestSupport.runBody("System.out.println(3 + 5);"));
    }

    @Test
    void readsSingleCharactersAndEchoesThem() {
        var output = TranspilerTestSupport.runBody("""
                        int first = System.in.read();
                        int second = System.in.read();
                        System.out.print((char) second);
                        System.out.print((char) first);
                        """, "ab");
        assertEquals("ba", output);
    }

    @Test
    void systemInReadReturnsZeroAtEndOfInput() {
        assertEquals("0", TranspilerTestSupport.runBody(
                        "System.out.print(System.in.read());", ""));
    }

    @Test
    void echoLoopUntilEndOfInput() {
        var output = TranspilerTestSupport.runBody("""
                        int c = System.in.read();
                        while (c != 0) {
                            System.out.print((char) c);
                            c = System.in.read();
                        }
                        """, "echo this!\nplease");
        assertEquals("echo this!\nplease", output);
    }

    @Test
    void readIntParsesNumbersAndComputes() {
        var output = TranspilerTestSupport.runBody("""
                        int a = Bf.readInt();
                        int b = Bf.readInt();
                        System.out.print(a + b);
                        System.out.print(" ");
                        System.out.print(a * b);
                        """, "12\n9\n");
        assertEquals("21 108", output);
    }

    @Test
    void readIntInsideLoopAccumulates() {
        var output = TranspilerTestSupport.runBody("""
                        int sum = 0;
                        int i = 0;
                        while (i < 3) {
                            sum += Bf.readInt();
                            i++;
                        }
                        System.out.print(sum);
                        """, "10\n20\n12\n");
        assertEquals("42", output);
    }

    @Test
    void printsMixedTypesInSequence() {
        var output = TranspilerTestSupport.runBody("""
                        int x = 7;
                        System.out.print("x=");
                        System.out.print(x);
                        System.out.print(';');
                        """);
        assertEquals("x=7;", output);
    }
}
