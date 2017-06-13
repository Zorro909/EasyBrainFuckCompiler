package de.Zorro909.BrainFuck;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

public class BrainFuckScript {

    String script = "";
    VariableManager vm;
    static BrainFuckScript bfs;
    HashMap<String, BrainFuckScript> replacements = new HashMap<String, BrainFuckScript>();

    public BrainFuckScript() {
        bfs = this;
    }

    public BrainFuckScript(boolean nonStatic) {

    }

    public String getScript() {
        return script;
    }

    public Variable saveString(Variable va, String s) {
        int v = va.cell;
        if (!s.endsWith("\n")) {
            s += "\n";
        }
        for (int i = 0; i < v; i++) {
            script += ">";
        }
        try {
            for (byte b : s.getBytes("ASCII")) {
                script += ">";
                add(b);
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        for (int i = 0; i < v + s.length(); i++) {
            script += "<";
        }
        return va;
    }

    public InputVariable readString(int maxLength) {
        InputVariable v = vm.createInputVariable(maxLength);
        return readString(v);
    }

    public void printString(Variable print) {
        int cellStart = print.cell;
        for (int i = 0; i < cellStart + 1; i++) {
            script += ">";
        }
        script += "[.>]<[<]";
        for (int i = 0; i < cellStart; i++) {
            script += "<";
        }
    }

    public void printString(String s) {
        try {
            int last = 0;
            for (byte b : s.getBytes("ASCII")) {
                printCharacter(b, last);
                last = b;
            }
            script += ">";
            sub(last);
            script += "<";
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void printCharacter(byte c) {
        script += ">";
        add(Integer.valueOf(c));
        script += ".";
        sub(Integer.valueOf(c));
        script += "<";
    }

    public void printCharacter(byte c, int knownCellValue) {
        script += ">";
        if (Integer.valueOf(c) - knownCellValue > 0) {
            add(Integer.valueOf(c) - knownCellValue);
        } else if (Integer.valueOf(c) - knownCellValue < 0) {
            sub(-1 * (Integer.valueOf(c) - knownCellValue));
        }
        script += ".<";

    }

    public void add(int p) {
        int highest = 1;
        int need = -1;
        for (int i = 1; i <= 25 && i <= p; i++) {
            if (p % i == 0) {
                int n = i + (p / i);
                if (n < need || need == -1) {
                    need = n;
                    highest = i;
                }
            }
        }
        int secondMultiplier = 1;
        try {
            secondMultiplier = p / highest;
        } catch (Exception e) {
            System.out.println("Tried to calculate " + p + " / " + highest);
        }
        script += ">";
        for (int i = 0; i < highest; i++) {
            script += "+";
        }
        script += "[<";
        for (int i = 0; i < secondMultiplier; i++) {
            script += "+";
        }
        script += ">-]<";
    }

    public void sub(int s) {
        int highest = 1;
        int need = -1;
        for (int i = 1; i <= 25 && i <= s; i++) {
            if (s % i == 0) {
                int n = i + (s / i);
                if (n < need || need == -1) {
                    need = n;
                    highest = i;
                }
            }
        }
        int secondMultiplier = 1;
        try {
            secondMultiplier = s / highest;
        } catch (Exception e) {
            System.out.println("Tried to calculate " + s + " / " + highest);
        }
        script += ">";
        for (int i = 0; i < highest; i++) {
            script += "+";
        }
        script += "[<";
        for (int i = 0; i < secondMultiplier; i++) {
            script += "-";
        }
        script += ">-]<";
    }

    public VariableManager setupVariableManager(int stringVariableStart, int stringVariableEnd,
                    int inputVariableStart, int inputVariableEnd) {
        if (vm != null) { return vm; }
        vm = new VariableManager(stringVariableStart, stringVariableEnd, inputVariableStart,
                        inputVariableEnd, this);
        return vm;
    }

    public InputVariable readString(InputVariable input) {
        int cellStart = input.cell;
        for (int i = 0; i < cellStart; i++) {
            script += ">";
        }
        script += "+[->,----------]++++++++++<[+++++++++++<]";
        for (int i = 0; i < cellStart; i++) {
            script += "<";
        }
        return input;
    }

}

class Variable {
    int cell = -1;
    int maxLength = 1;

    public Variable(int cell, int maxLength) {
        this.cell = cell;
        this.maxLength = maxLength;
    }

    public void forEach(Consumer<Pointer> c) {
        Pointer.moveTo(new Pointer(cell), BrainFuckScript.bfs);
        Pointer p = new Pointer(cell);
        for(int ce = cell; ce < cell + maxLength;ce++){
            c.accept(p);
            p = new Pointer(ce+1);
            p.moveTo(BrainFuckScript.bfs);
        }
        Pointer.moveTo(new Pointer(0), BrainFuckScript.bfs);
    }

    public Pointer getPointer(int index){
        return new Pointer(cell+index);
    }
    
    public Pointer getPointer(){
        return getPointer(0);
    }
    
}

class InputVariable extends Variable {

    public InputVariable(int cell, int maxLength) {
        super(cell, maxLength);
    }

    public Variable copyToNormal() {
        Variable v = BrainFuckScript.bfs.vm.createVariable(maxLength);
        BrainFuckScript.bfs.vm.copyVariable(this, v);
        BrainFuckScript.bfs.vm.clearVariable(this);
        return v;
    }

}

class IntegerVariable extends Variable {

    public IntegerVariable(int cell) {
        super(cell, 10);
    }

    public void importFromBit(Variable from) throws Exception {
        if (from.maxLength % 4 != 0) { throw new Exception(
                        "Variable Importation only works with bits"); }
        // [48,49,49,48,0,0,0,-0]
        for (int i = 0; i < from.cell; i++) {
            BrainFuckScript.bfs.script += ">";
        }
        for (int i = from.cell; i < from.maxLength + from.cell; i++) {
            BrainFuckScript.bfs.script += ">";
            for (int i2 = 0; i2 < 48; i2++) {
                BrainFuckScript.bfs.script += "-";
            }
        }
        for (int bit = 0; bit < from.maxLength; bit++) {
            long d = Math.round(Math.pow(2, bit));
            BrainFuckScript.bfs.script += "[-";
            for (int i = 0; i < (from.cell + from.maxLength) - this.cell + bit; i++) {
                BrainFuckScript.bfs.script += "<";
            }
            for (int i = 0; i > (from.cell + from.maxLength) - this.cell + bit; i--) {
                BrainFuckScript.bfs.script += ">";
            }
            for (int i = 0; i < d; i++) {
                BrainFuckScript.bfs.script += "+";
            }
            for (int i = 0; i < (from.cell + from.maxLength) - this.cell + bit; i++) {
                BrainFuckScript.bfs.script += ">";
            }
            for (int i = 0; i > (from.cell + from.maxLength) - this.cell + bit; i--) {
                BrainFuckScript.bfs.script += "<";
            }
            BrainFuckScript.bfs.script += "]<";
        }

        for (int i = 0; i < from.cell; i++) {
            BrainFuckScript.bfs.script += "<";
        }

    }

    public void print() {
        for (int i = 0; i < cell + 1; i++) {
            BrainFuckScript.bfs.script += ">";
        }
        BrainFuckScript.bfs.script += "[>>+>+<<<-]>>>[<<<+>>>-]<<+>[<->[>++++++++++<[->-[>+>>]>[+[-<+>]>+>>]<<<<<]>[-]++++++++[<++++++>-]>[<<+>>-]>[<<+>>-]<<]>]<[->>++++++++[<++++++>-]]<[.[-]<]<";
        for (int i = 0; i < cell; i++) {
            BrainFuckScript.bfs.script += "<";
        }
    }

}

class VariableManager {

    int stringVariableStart;
    int stringVariableEnd;
    int stringVariableSize;
    int inputVariableStart;
    int inputVariableEnd;
    int inputVariableSize;
    BrainFuckScript bfs;
    ArrayList<Variable> stringVariables = new ArrayList<Variable>();
    ArrayList<Variable> inputVariables = new ArrayList<Variable>();

    public VariableManager(int stringVariableStart, int stringVariableEnd, int inputVariableStart,
                    int inputVariableEnd, BrainFuckScript bfs) {
        this.stringVariableStart = stringVariableStart;
        this.stringVariableEnd = stringVariableEnd;
        stringVariableSize = stringVariableEnd - stringVariableStart - 1;
        this.inputVariableStart = inputVariableStart;
        this.inputVariableEnd = inputVariableEnd;
        inputVariableSize = inputVariableEnd - inputVariableStart - 1;
        this.bfs = bfs;
    }

    public InputVariable createInputVariable(int maxLength) {
        if (inputVariableSize < maxLength) { throw new OutOfMemoryError(
                        "Not Enough Memory in inputVariableMemory"); }
        InputVariable v = new InputVariable(inputVariableStart, maxLength);
        inputVariableStart -= maxLength + 1;
        inputVariableSize = inputVariableEnd - inputVariableStart - 1;
        return v;
    }

    public void setVariable(Variable v, String content) {
        bfs.saveString(v, content);
    }

    public void copyVariable(Variable from, Variable to) {
        clearVariable(to);
        for (int i = 0; i < from.cell + 1; i++) {
            bfs.script += ">";
        }
        bfs.script += "[[";
        for (int i = 0; i < to.cell - from.cell; i++) {
            bfs.script += ">";
        }
        for (int i = 0; i < from.cell - to.cell; i++) {
            bfs.script += "<";
        }
        bfs.script += "+";
        for (int i = 0; i < to.cell - from.cell; i++) {
            bfs.script += "<";
        }
        for (int i = 0; i < from.cell - to.cell; i++) {
            bfs.script += ">";
        }
        bfs.script += "-]>";
        bfs.script += "]";
        for (int i = 0; i < from.cell + from.maxLength + 1; i++) {
            bfs.script += "<";
        }
    }

    public void clearVariable(Variable to) {
        for (int i = 0; i < to.cell; i++) {
            bfs.script += ">";
        }
        for (int i = to.cell; i < to.maxLength + to.cell; i++) {
            bfs.script += ">[-]";
        }
        for (int i = 0; i < to.cell + to.maxLength; i++) {
            bfs.script += "<";
        }
    }

    public Variable createVariable(int maxLength) {
        if (stringVariableSize < maxLength) { throw new OutOfMemoryError(
                        "Not Enough Memory in stringVariableMemory"); }
        Variable v = new Variable(stringVariableStart, maxLength);
        stringVariableStart += maxLength + 1;
        stringVariableSize = stringVariableEnd - stringVariableStart - 1;
        return v;
    }

    public IntegerVariable createIntegerVariable() {
        if (stringVariableSize < 10) { throw new OutOfMemoryError(
                        "Not Enough Memory in stringVariableMemory"); }
        IntegerVariable v = new IntegerVariable(stringVariableStart);
        stringVariableStart += 10 + 1;
        stringVariableSize = stringVariableEnd - stringVariableStart - 1;
        return v;
    }

}
