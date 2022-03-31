package es.us.isa.restest.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class CommandRunner {

    private static Logger logger = LogManager.getLogger(CommandRunner.class.getName());

    // Run external command (e.g., script) and wait until it finishes
    public static boolean runCommand(String command, String[] commandArgs) {

        boolean commandOk = false;
        try {
            ProcessBuilder pb = new ProcessBuilder(command, String.join(" ", commandArgs));
            pb.inheritIO(); // Print output of program to stdout
            Process proc = pb.start();
            proc.waitFor();
            commandOk = true;
        } catch (IOException e) {
            logger.error("Error running command");
            logger.error("Exception: ", e);
        } catch (InterruptedException e) {
            logger.error("Error running command");
            logger.error("Exception: ", e);
            Thread.currentThread().interrupt();
        }

        return commandOk;
    }
}
