package de.Zorro909.BrainFuck;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class Main {

    public static void main(String[] args) throws Exception {
        BrainFuckScript bfs = new BrainFuckScript();

        VariableManager vm = bfs.setupVariableManager(3, 40, 50, 60);
        
        Variable in = vm.createVariable(4);
        vm.setVariable(in, "1111");
        IntegerVariable iv = vm.createIntegerVariable();
        iv.importFromBit(in);
        iv.print();

        System.out.println("Compiling Finished (Length: " + bfs.getScript().length() + ")");
        System.out.println(bfs.getScript());
        new Interpreter().interpret(bfs.getScript());
    }

}
