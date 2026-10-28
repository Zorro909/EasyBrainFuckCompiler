package de.zorro909.brainfuck.compiler.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole program: functions plus the initial heap image.
 *
 * <p>The heap is addressed by <em>distance</em> from its top word; distance 0 is reserved so
 * that 0 can serve as {@code null}. {@link #staticImage} holds the initial value of the words at
 * distances {@code 0..staticImage.length-1} (static fields, string literals).
 */
public final class Program {

    public final List<Function> functions = new ArrayList<>();
    public Function main;
    public long[] staticImage = new long[1];
}
