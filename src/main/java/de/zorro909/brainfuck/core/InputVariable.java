package de.zorro909.brainfuck.core;

/** A {@link Variable} allocated in the input memory region, filled by {@code readString}. */
public class InputVariable extends Variable {

    private final BrainFuckScript script;

    InputVariable(int cell, int maxLength, BrainFuckScript script) {
        super(cell, maxLength);
        this.script = script;
    }

    /**
     * Moves the content into a freshly allocated normal variable and clears this one.
     * Note that this is a destructive move, not a copy.
     */
    public Variable copyToNormal() {
        VariableManager manager = script.variableManager();
        Variable target = manager.createVariable(maxLength());
        manager.copyVariable(this, target);
        return target;
    }
}
