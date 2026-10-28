# EasyBrainFuckCompiler

A compiler from (a large subset of) **Java to Brainfuck**, plus a fast Brainfuck interpreter.
Classes, objects, interfaces, generics, lambdas, records, enums, pattern matching,
exceptions, recursion, strings, `float`/`double`, `ArrayList`/`HashMap`/`TreeMap`,
`Scanner` input and 32-bit `int` arithmetic all compile to plain Brainfuck that runs on any
interpreter with 8-bit wrapping cells.

```java
// examples/Calculator.java (excerpt)
private int term() {
    int v = factor();
    while (peek() == '*' || peek() == '/' || peek() == '%') {
        char op = src.charAt(pos++);
        int r = factor();
        v = op == '*' ? v * r : op == '/' ? v / r : v % r;
    }
    return v;
}
```

```console
$ printf 'x = 6 * 7\nx / 4 + x %% 4\ny = x * x * x\n' | java -jar target/easy-brainfuck-compiler-*.jar run examples/Calculator.java
= 42
= 12
= 74088
```

## Build & run

```console
$ mvn package                                    # needs JDK 25
$ java -jar target/easy-brainfuck-compiler-1.0.0-SNAPSHOT.jar
Usage:
  compile <File.java>... [-o out.bf] [options]   Java -> Brainfuck
  run <File.java...|file.bf> [options]           compile if needed, then run
  profile <File.java>... [options]               run and show where time goes

Options:
  --word-bytes N   bytes per int value: 4 (default, exact Java int), 2, 8
  --heap-words N   heap size in words (default 16384, max 65535)
  --tape N         interpreter tape size in cells (default 4194304)
  --stats          print code size and memory layout to stderr
```

Every program under `examples/` and `src/test/resources/programs/` is ordinary Java: it
compiles and runs unchanged with `javac`/`java`, and the test suite checks that the
Brainfuck output is identical to the JVM's.

| Example | Shows |
|---|---|
| `FizzBuzz` | strings, `+=` concatenation, ternaries |
| `Calculator` | recursive-descent parser, `HashMap`, `Scanner` on stdin, exceptions |
| `Statistics` | `double` math, `printf("%.3f")`, `try`/`catch` around `Double.parseDouble` |
| `BrainfuckInJava` | a Brainfuck interpreter written in Java, compiled to Brainfuck |
| `NQueens` | recursion and backtracking |
| `GameOfLife` | 2-D arrays |

### A real program: jlox

The tree-walking [Lox](https://craftinginterpreters.com) interpreter from *Crafting
Interpreters* (`java/com/craftinginterpreters/lox`, MIT licensed, ~2,000 lines across 15
files) compiles unchanged except for `Lox.main`, which now reads the script from `System.in`
instead of a file. It runs closures, recursion, classes with inheritance and `super`, and
string and number printing, with stdout identical to the JVM's (Lox reports errors on
`System.err`, which the Brainfuck build drops):

```console
$ java -jar target/easy-brainfuck-compiler-*.jar run lox/*.java --heap-words 65535 < sample.lox
```

The result is 21.8 million Brainfuck commands (389 functions). A dozen-line sample script
takes about 78 billion instructions, roughly two minutes in the bundled interpreter. Most
of that time goes to `HashMap` lookups on environments and to string handling.

## Supported Java

- **Types:** `int`, `char`, `short`, `byte`, `boolean`, `long` (one machine word, see
  below), `float` and `double` (IEEE 754 in software, see below), classes, interfaces, abstract classes, enums (`values()`, `ordinal()`, `switch`),
  records (accessors, `equals`/`hashCode`/`toString`), arrays (multi-dimensional,
  initializers, `clone()`), generics with bounds and inference (including `<>`).
- **Classes:** fields with initializers, static and instance initializer blocks in Java
  order (a class's static initialization runs after the classes it uses), static imports, constructors with `this(...)`/`super(...)`, overloading (resolved like `javac`),
  overriding with virtual dispatch, `super.m()`, static/inner/local/anonymous classes
  with captured variables, sealed interfaces.
- **Pattern matching:** `instanceof` and `switch` with type patterns, record
  deconstruction patterns (nested, `var`), `when` guards and `case null`.
- **Statements:** everything structured: `if`, `while`, `do`, `for`, for-each over arrays
  and `Iterable`s, labeled `break`/`continue`, `switch` statements and expressions (arrow
  and colon forms, strings, enums, `yield`), `return`.
- **Exceptions:** `throw`, `try`/`catch` (including multi-catch) and `finally` with real
  stack unwinding across method calls; `finally` also runs on `break`, `continue` and
  `return`; try-with-resources closes in reverse order. Division by zero throws a catchable
  `ArithmeticException`, a failing reference cast a `ClassCastException`.
- **Expressions:** full operator set with Java precedence and short-circuiting,
  compound assignments, `++`/`--`, casts with truncation and sign extension, the ternary
  operator, string concatenation, lambdas and method references, varargs, compile-time
  constant folding.
- **Library** (`src/main/resources/.../runtime`, written in Java and compiled on demand):
  `String`, `StringBuilder`, wrapper types with boxing and caches, `Math`, `Objects`,
  `ArrayList`, `LinkedList`, `ArrayDeque`, `Stack`, `HashMap`/`LinkedHashMap`/`TreeMap`,
  `HashSet`/`LinkedHashSet`/`TreeSet`, `Collections`, `Arrays`, `Comparator`, functional
  interfaces, `Random`, `Scanner`, `BufferedReader`, `StringTokenizer`, `System.in.read()`,
  `System.out.print/println/printf`, `String.format`/`formatted` (`%d %s %c %b %x %f %e`
  with widths and precisions), `Float`/`Double` and the common exceptions.

### Floating point

`float` and `double` are implemented in software (`runtime/SoftFloat.java`, compiled once
per format): addition, subtraction, multiplication, division, `%`, `sqrt`, conversions and
comparisons are correctly rounded IEEE 754 operations, including NaN, infinities, signed
zeros and subnormals. `Double.toString`, `Float.toString` and parsing produce exactly the
JVM's digits; `%.2f` rounds like `java.util.Formatter`. The test suite fuzzes the template
against the JVM before it is ever compiled to Brainfuck.

- `float` is always exact binary32.
- `double` is exact binary64 with `--word-bytes 8`. With the default 4-byte words it is
  binary32, so `0.1 + 0.2` prints `0.3` instead of `0.30000000000000004`.
- `Math.floor/ceil/rint/round/abs/min/max/sqrt` are exact; `exp`, `log`, `sin`, `cos`,
  `tan`, `atan`, `atan2`, `pow` (for non-integer exponents) and friends are accurate to
  about one unit in the last place, so their last digit can differ from the JVM's.
- Each floating point operation costs on the order of 100,000 Brainfuck instructions:
  plenty for averages and formatting, slow for numerical simulations.

### Deliberate differences from the JVM

Exact equivalence is not a goal. These are the known gaps:

- **`long` is one word.** With the default `--word-bytes 4` it behaves like `int`.
  `--word-bytes 8` makes `long` and `double` exact, but `int` becomes 64-bit as well, and
  everything runs about twice as slow.
- **No null or bounds checks.** Dereferencing `null` reads zeros and indexing outside an
  array touches neighbouring heap words instead of throwing. Exceptions that the library
  throws itself (`NumberFormatException`, `IndexOutOfBoundsException` from `ArrayList`,
  ...) work. An uncaught exception prints `Exception in thread "main" <type>: <message>`
  like the JVM's handler, without a stack trace.
- **Arrays have no runtime type**, so `o instanceof int[]` is only accepted when the
  static type of `o` decides the answer.
- `System.err` output is discarded. There is no reflection, threads or garbage
  collection; the heap is a bump allocator of `--heap-words` words.
- `HashMap` iterates in the same order as `java.util.HashMap` because it uses the same
  hashing and table sizes. `TreeMap`/`TreeSet` keep their elements sorted for iteration,
  but lookup is hash-based.

## How it works

```
Java source ──JavaParser──▶ Frontend ──▶ IR (CFG, virtual registers) ──▶ BfBackend ──▶ Brainfuck
             + runtime library  types, overloads,     calls end blocks       frames, dispatch,
                                generics, lowering                           word arithmetic
```

**Frontend** (`compiler/frontend`). Resolves classes, types and overloads for the user
program and the runtime library together. Only methods that are reachable get compiled,
using a worklist plus rapid type analysis for virtual calls: a call site with one possible
target becomes a direct call. The `Lowerer` turns the AST into a control-flow graph of
three-address instructions.

**Backend** (`compiler/backend`). Brainfuck has no addressing, so the generated program
is a state machine:

```
tape: [ heap: H words × (1 lane cell + W data bytes) ][ sentinel ][ frame 0 ][ frame 1 ] ...
frame: [ heap carrier | R FN PC CONT XPC | switch cells | scratch | return value | registers ]

main loop:  R[  PC = CONT;  switch (FN) { case f: switch (PC) { case b: run b } }  ]
```

- **Frames and calls.** Every frame has the same size F, and the pointer always sits in
  the current frame. A call writes the arguments into the next frame, sets its function and
  block ids, and shifts the pointer by +F. A return writes the result into the caller's
  frame and shifts by −F.
- **Running blocks.** A block's code is emitted with compile-time pointer tracking. The
  register allocator puts virtual registers into frame slots. Both switches are nested
  count-down loops (`t[- t[- … e[- case 2 ] ] e[- case 1 ] ]`), so reaching case k costs
  k decrements; frequently called functions get the small ids.
- **Exceptions.** A call inside a `try` stores the handler's block id in the caller's XPC
  cell. `throw` stores the exception in a static slot, then one Brainfuck loop pops frames
  until it finds a nonzero XPC and resumes that frame at its handler.
- **Words.** A word is W little-endian bytes. Addition and subtraction ripple carries
  byte by byte. Comparisons decide on the most significant differing byte.
  Multiplication, division, bitwise operations and `printInt` are shared builtin functions,
  so their code is emitted only once.
- **Heap access.** The heap sits at fixed addresses that frames can't reach directly. A
  load or store carries `(ref, index, value)` down to the heap and walks it there: each step
  moves the carrier one word to the right and leaves a breadcrumb in that word's lane
  cell. On the way back, the carrier follows the breadcrumbs. The cost is proportional to
  the address, not the heap size.

**Interpreter** (`bf`). Compiles Brainfuck into an op array, folding runs, `[-]`, scan
loops and balanced multiply/move loops. It reports an error when the pointer leaves the
tape instead of wrapping. `profile` attributes executed instructions to the Java
functions that produced them.

### Running on other interpreters

The output is standard Brainfuck: 8-bit wrapping cells, no negative pointer moves, and
`,` at end of input either stores 0 or leaves the cell unchanged. It needs
`heapWords × (W+1) + F` cells, plus F cells per call level. The defaults need roughly 82k
cells, so for classic 30,000-cell interpreters pass something like `--heap-words 2048`
(`--stats` prints the exact requirement).

## Tests

```console
$ mvn test
```

- `BackendTest` runs hand-built IR at word sizes 2, 4 and 8.
- `ProgramsTest` compiles every program in `src/test/resources/programs`, along with its
  optional `.in` file. It runs each program on the JVM (in-memory `javax.tools`) and as
  Brainfuck, and requires identical output.

The pre-existing C-Compiler tooling lives in `legacy/`. Its original `bin/BF2OC.jar` cannot
handle compiled programs (it uses `int` cells and translates in quadratic time), so
`legacy/C-Compiler/BfToC.java` replaces it:

```console
$ java legacy/C-Compiler/BfToC.java prog.bf prog.c && gcc -O1 -o prog prog.c
```

Compiled with gcc this runs 12–15× faster than the bundled interpreter, which manages about
0.8 billion Brainfuck instructions per second. jlox takes 8.8 s instead of 108 s, but gcc needs
7 minutes and 4 GB of memory to compile its 490,000-line `main`.
