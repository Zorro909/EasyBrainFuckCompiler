package de.zorro909.brainfuck.cli;

import de.zorro909.brainfuck.bf.BfExecutionException;
import de.zorro909.brainfuck.bf.BfInterpreter;
import de.zorro909.brainfuck.bf.BfProgram;
import de.zorro909.brainfuck.bf.BfSyntaxException;
import de.zorro909.brainfuck.compiler.CompileException;
import de.zorro909.brainfuck.compiler.JavaToBrainfuck;
import de.zorro909.brainfuck.compiler.backend.BfBackend;
import de.zorro909.brainfuck.compiler.frontend.Frontend;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Command line entry point. */
public final class Main {

    private static final String USAGE = """
                    Usage:
                      compile <File.java>... [-o out.bf] [options]   Java -> Brainfuck
                      run <File.java...|file.bf> [options]           compile if needed, then run
                      profile <File.java>... [options]               run and show where time goes

                    Options:
                      --word-bytes N   bytes per value: 4 (default: exact int and float),
                                       8 (also exact long and double, slower), 2
                      --heap-words N   heap size in words (default 16384, max 65535)
                      --tape N         interpreter tape size in cells (default 4194304)
                      --stats          print code size and memory layout to stderr
                    """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length < 2) {
            System.err.print(USAGE);
            return 2;
        }
        String command = args[0];
        List<Path> files = new ArrayList<>();
        Map<String, String> opts = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--stats")) {
                opts.put(a, "true");
            } else if (a.startsWith("-")) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + a);
                    return 2;
                }
                opts.put(a, args[++i]);
            } else {
                files.add(Path.of(a));
            }
        }
        try {
            JavaToBrainfuck.Options options = JavaToBrainfuck.Options.defaults()
                            .withWordBytes(Integer.parseInt(opts.getOrDefault("--word-bytes", "4")))
                            .withHeapWords(Integer.parseInt(opts.getOrDefault("--heap-words",
                                            "16384")));
            BfInterpreter.Config config = BfInterpreter.Config.defaults().withTapeSize(
                            Integer.parseInt(opts.getOrDefault("--tape", "4194304")));
            boolean stats = opts.containsKey("--stats");
            return switch (command) {
                case "compile" -> {
                    var result = compile(files, options, stats);
                    String out = opts.get("-o");
                    if (out == null) {
                        System.out.print(result.code());
                        System.out.flush();
                    } else {
                        Files.writeString(Path.of(out), result.code());
                    }
                    yield 0;
                }
                case "run" -> {
                    String code;
                    if (files.size() == 1 && !files.getFirst().toString().endsWith(".java")) {
                        code = Files.readString(files.getFirst());
                    } else {
                        code = compile(files, options, stats).code();
                    }
                    execute(BfProgram.compile(code), config, null);
                    yield 0;
                }
                case "profile" -> {
                    var result = compile(files, options, true);
                    BfInterpreter interp = new BfInterpreter(config);
                    interp.enableProfiling();
                    BfProgram program = BfProgram.compile(result.code());
                    execute(program, config, interp);
                    printProfile(program, interp.profile(), result.markers());
                    yield 0;
                }
                default -> {
                    System.err.print(USAGE);
                    yield 2;
                }
            };
        } catch (CompileException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (BfSyntaxException | BfExecutionException e) {
            System.err.println("brainfuck: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("io: " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }
    }

    private static JavaToBrainfuck.Result compile(List<Path> files, JavaToBrainfuck.Options options,
                    boolean stats) throws IOException {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("no input files");
        }
        List<Frontend.Source> sources = new ArrayList<>();
        for (Path f : files) {
            sources.add(new Frontend.Source(f.getFileName().toString(), Files.readString(f)));
        }
        long t0 = System.nanoTime();
        var result = new JavaToBrainfuck(options).compile(sources);
        if (stats) {
            System.err.printf("compiled in %d ms: %,d Brainfuck commands, %d functions, %d blocks%n"
                            + "frame %d cells, %d cells needed before the first call%n",
                            (System.nanoTime() - t0) / 1_000_000, result.code().length(),
                            result.functions(), result.blocks(), result.frameSize(),
                            result.tapeCells());
        }
        return result;
    }

    private static void execute(BfProgram program, BfInterpreter.Config config,
                    BfInterpreter interp) {
        BfInterpreter runner = interp != null ? interp : new BfInterpreter(config);
        PrintStream out = new PrintStream(new BufferedOutputStream(System.out), false,
                        StandardCharsets.UTF_8);
        runner.run(program, System.in, out);
        out.flush();
    }

    private static void printProfile(BfProgram program, long[] counts,
                    List<BfBackend.Marker> markers) {
        int[] offsets = markers.stream().mapToInt(BfBackend.Marker::offset).toArray();
        Map<String, Long> perFunction = new HashMap<>();
        long total = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) {
                continue;
            }
            int pos = Arrays.binarySearch(offsets, program.sourceOffset(i));
            if (pos < 0) {
                pos = -pos - 2;
            }
            String label = pos < 0 ? "prologue" : markers.get(pos).label();
            int slash = label.indexOf('/');
            perFunction.merge(slash < 0 ? label : label.substring(0, slash), counts[i], Long::sum);
            total += counts[i];
        }
        final long all = total;
        System.err.printf("%n%,d instructions executed%n", all);
        perFunction.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(20)
                        .forEach(e -> System.err.printf("%6.2f%%  %s%n",
                                        100.0 * e.getValue() / all, e.getKey()));
    }
}
