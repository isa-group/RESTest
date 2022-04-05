package es.us.isa.restest.util;

import java.io.IOException;

public class CommandRunner {

    // Run external command (e.g., script) and wait until it finishes
    public static Process runCommand(String command, String[] commandArgs) throws RESTestException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, String.join(" ", commandArgs));
            pb.inheritIO(); // Print output of program to stdout
            Process proc = pb.start();
            proc.waitFor();
            return proc;
        } catch (IOException|InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            throw new RESTestException("Error running command '" + command + " " + String.join(" ", commandArgs) + "': " + e.getMessage());
        }
    }
}
