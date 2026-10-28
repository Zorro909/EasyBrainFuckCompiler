package de.zorro909.brainfuck.compiler.backend;

import java.util.BitSet;

/**
 * Allocator for temporary cells. Every cell handed out is zero, and callers must zero it again
 * before freeing it - this invariant is what lets generated code use temporaries without
 * clearing them first.
 */
final class Scratch {

    private final int base;
    private final int size;
    private final BitSet used = new BitSet();
    private int highWater;

    Scratch(int base, int size) {
        this.base = base;
        this.size = size;
    }

    int alloc() {
        int i = used.nextClearBit(0);
        return take(i, 1);
    }

    /** {@code n} adjacent cells, e.g. a word. */
    int allocRun(int n) {
        int i = 0;
        while (true) {
            i = used.nextClearBit(i);
            int end = used.nextSetBit(i);
            if (end < 0 || end - i >= n) {
                return take(i, n);
            }
            i = end;
        }
    }

    private int take(int i, int n) {
        if (i + n > size) {
            throw new IllegalStateException("Out of scratch cells (" + size + ")");
        }
        used.set(i, i + n);
        highWater = Math.max(highWater, i + n);
        return base + i;
    }

    void free(int cell) {
        used.clear(cell - base);
    }

    void freeRun(int cell, int n) {
        used.clear(cell - base, cell - base + n);
    }

    boolean allFree() {
        return used.isEmpty();
    }

    int highWater() {
        return highWater;
    }
}
