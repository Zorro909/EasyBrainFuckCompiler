package de.zorro909.brainfuck.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Compiles a Java program in memory and runs it on the JVM to obtain the expected output. */
final class JvmOracle {

    private JvmOracle() {
    }

    static synchronized String run(String className, String source, String input) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager fm = new ForwardingJavaFileManager<>(
                        javac.getStandardFileManager(null, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name,
                            JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + name + ".class"), kind) {
                    @Override
                    public java.io.OutputStream openOutputStream() {
                        return classes.computeIfAbsent(name, k -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject src = new SimpleJavaFileObject(URI.create("string:///" + className
                        + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        var diagnostics = new java.io.StringWriter();
        boolean ok = javac.getTask(diagnostics, fm, null, List.of("-nowarn", "-Xlint:none"), null,
                        List.of(src)).call();
        if (!ok) {
            throw new IllegalStateException("javac failed:\n" + diagnostics);
        }
        ClassLoader loader = new ClassLoader(JvmOracle.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream b = classes.get(name);
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = b.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        PrintStream oldOut = System.out;
        InputStream oldIn = System.in;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            loader.loadClass(className).getMethod("main", String[].class).invoke(null,
                            (Object) new String[0]);
        } catch (InvocationTargetException e) {
            // uncaught exception: the JVM prints it to stderr; the Brainfuck program prints it to
            // stdout, so append the same line for comparison
            System.out.flush();
            Throwable t = e.getCause();
            out.writeBytes(("Exception in thread \"main\" " + t + "\n").getBytes(
                            StandardCharsets.UTF_8));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            System.out.flush();
            System.setOut(oldOut);
            System.setIn(oldIn);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
