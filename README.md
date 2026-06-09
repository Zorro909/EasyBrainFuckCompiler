# EasyBrainFuckCompiler

A Java-to-Brainfuck transpiler with a built-in Brainfuck code generator and interpreter.
Java 25, Maven, no runtime dependencies except [JavaParser](https://javaparser.org/).

```java
// examples/FizzBuzz.java
for (int i = 1; i <= 30; i++) {
    int byThree = i % 3;
    int byFive = i % 5;
    if (byThree == 0 && byFive == 0) {
        System.out.println("FizzBuzz");
    } else if (byThree == 0) {
        ...
```

```console
$ java -jar target/easy-brainfuck-compiler-*.jar run examples/FizzBuzz.java
1
2
Fizz
4
Buzz
...
```

## Build & run

```console
$ mvn package                       # needs JDK 25
$ java -jar target/easy-brainfuck-compiler-1.0.0-SNAPSHOT.jar
Usage:
  transpile <file.java> [-o <out.bf>]      transpile Java to Brainfuck
  run <file.java|file.bf> [--extensions]   transpile if needed, then interpret
  demo                                     run the original bit-import demo
```

## Supported Java subset

The transpiler (`JavaToBrainfuckTranspiler`) accepts a single class with one
`public static void main` method containing:

| Construct | Notes |
|---|---|
| `int x;` `int x = expr;` `char c = 'A';` | one 8-bit cell per variable, values 0–255 (mod 256) |
| `x = expr`, `+=`, `-=`, `*=`, `/=`, `%=`, `x++`, `x--` | statement position |
| `+ - * / %` | `/ 0` runs into the interpreter's step limit |
| `== != < > <= >=` | result 0/1 |
| `&& \|\| !` | **not** short-circuit: both operands are always evaluated |
| `if`/`else`, `while`, `for` | `for` is desugared to `while` |
| `System.out.print/println(...)` | string literals, int expressions, char expressions / `(char)` casts |
| `System.in.read()` | one byte; **0** at end of input (not -1) |
| `Bf.readInt()` | reads decimal digits up to and including a newline |
| `Bf.syscall(id)` | interpreter syscall, see below |

Everything else — methods, arrays, `String` variables, negative numbers, `break`/
`continue`, shadowing — is rejected with an `UnsupportedJavaConstructException`
that names the construct and source line. All arithmetic is on 8-bit cells: values wrap
mod 256 and there are no negative numbers (`3 - 5 == 254`).

The `Bf` intrinsics class ships with real JVM implementations, so every example under
`examples/` also compiles and runs with plain `javac`/`java` — handy for differential
testing the transpiler against the JVM.

## Architecture

```
de.zorro909.brainfuck
├── core         BrainFuckScript (string/variable codegen), BfOps (cell-level
│                instruction set: arithmetic, comparisons, control flow, decimal I/O),
│                CodeBuilder (low-level emitter with compile-time pointer tracking),
│                VariableManager + Variable/InputVariable/IntegerVariable
├── interpreter  Interpreter (jump-table loops, injectable I/O, step limit,
│                optional syscall extension), InterpreterConfig
├── transpiler   JavaToBrainfuckTranspiler + Statement/ExpressionEmitter (JavaParser
│                AST -> BfOps), MemoryLayout, Bf intrinsics
└── cli          Main
```

Transpiled programs use this tape layout:

```
cells 0..15    scratch region for BfOps temporaries (zero between operations)
cells 16..N    named variables, one cell each, in declaration order (hoisted up front)
cells N+1..    expression temporaries (LIFO stack, empty between statements)
```

`CodeBuilder` tracks the data pointer at compile time, so all codegen works with
absolute cell addresses, and loop emission (`loopAt`) enforces the classic Brainfuck
discipline that the pointer sits on the tested cell at both `[` and `]`.

## Interpreter

8-bit wrapping cells, 65536-cell tape (configurable), pointer wraps by default,
non-command characters are ignored, `,` stores 0 at end of input. A configurable step
limit (default 10⁸) aborts runaway programs. Bracket matching is precomputed into a
jump table; unbalanced brackets fail with `BrainfuckSyntaxException`.

### Syscalls (extension)

With `InterpreterConfig.extensionsEnabled()` (CLI: `--extensions`) the extra opcode
`@` invokes a syscall: the current cell selects the id, the cell right of it carries
the argument/result. Without extensions, programs containing `@` are rejected, so pure
Brainfuck output stays portable.

| id | effect |
|---|---|
| 0 | halt the program |
| 1 | store a random byte in the argument cell |
| 2 | flush the output stream |

Custom syscalls can be registered with `Interpreter.registerSyscall(id, syscall)`.

## What was fixed during modernization

The original (Java 8, Eclipse-only) code had several bugs, each now covered by a
regression test:

- **Interpreter bracket matching** used a shared nesting counter with an `||`
  condition; nested loops like `[[-]]` were skipped incorrectly.
- **`IntegerVariable.importFromBit`** emitted pointer-return loops with inverted
  conditions that never executed, and targeted the wrong destination cell — any
  multi-bit import looped forever or produced garbage.
- **`VariableManager.createInputVariable`** allocated *downward* out of its memory
  region, colliding with other variables.
- Input used `Scanner.next()` and could not read spaces or newlines; script assembly
  used O(n²) string concatenation; a full-length string's trailing newline overlapped
  the next variable's memory.

The original demo (`demo` subcommand: import the bit string `"1111"` and print `15`)
works unchanged. The pre-existing C-Compiler tooling was moved untouched to `legacy/`.

## Tests

```console
$ mvn test    # 230+ end-to-end tests
```

All tests run generated Brainfuck through the interpreter with captured I/O streams —
from single-operation truth tables (every comparison operator over a value grid) to
transpiled FizzBuzz compared line by line against the JVM's output.
