package de.zorro909.brainfuck.compiler.ir;

import java.util.ArrayList;
import java.util.List;

public final class Block {

    public final List<Insn> insns = new ArrayList<>();
    public Terminator terminator;
    /** Debug label. */
    public final String label;

    public Block(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
