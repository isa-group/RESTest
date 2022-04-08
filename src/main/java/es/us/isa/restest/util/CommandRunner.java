package es.us.isa.restest.util;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.Charset;

public class CommandRunner {

    // Run external command (e.g., script) and wait until it finishes
    public static String runCommand(String command, String[] commandArgs) throws RESTestException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, String.join(" ", commandArgs));
//            pb.inheritIO(); // Print output of program to stdout. Line removed: if stdout is printed, cannot parse it
            Process proc = pb.start();
            proc.getOutputStream();
            String stdout = IOUtils.toString(proc.getInputStream(), Charset.defaultCharset());
            String stderr = IOUtils.toString(proc.getErrorStream(), Charset.defaultCharset());
            proc.waitFor();
            System.out.println(stdout); // For debugging
            System.out.println(stderr); // For debugging
            return stdout;
        } catch (IOException|InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            throw new RESTestException("Error running command '" + command + " " + String.join(" ", commandArgs) + "': " + e.getMessage());
        }
    }
}
