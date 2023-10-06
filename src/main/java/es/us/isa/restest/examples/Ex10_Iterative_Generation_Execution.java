package es.us.isa.restest.examples;

import es.us.isa.restest.runners.RESTestIterativeRunner;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;

import java.util.logging.Logger;

/**
 * This example shows how to generate test cases, execute them, and generate an Allure report iteratively using RESTestIterativeRunner.
 * This can be used to generate and run test cases in small batches, adding a delay among iterations, to prevent API quota violations.
 * Also, this is helpful for generating and executing test cases for long periods of time, or even indefinitely until de process is stopped.
 *
 * For opening Allure reports in a local browser check cross-origin restrictions: https://stackoverflow.com/questions/51081754/cross-origin-request-blocked-when-loading-local-file
 *
 * The resources for this example are located at src/main/resources/Examples/Ex10_Iterative_Generation_Execution.
 *
 */
public class Ex10_Iterative_Generation_Execution {

    public static final String PROPERTY_FILE_PATH = "src/main/resources/Examples/Ex10_Iterative_Generation_Execution/user_config.properties"; 		// Path to user properties file with configuration options

    public static final Logger logger = Logger.getLogger(Ex10_Iterative_Generation_Execution.class.getName());

    public static void main(String[] args) throws RESTestException {

        // Load properties
        RESTestIterativeRunner runner = new RESTestIterativeRunner(PROPERTY_FILE_PATH);

        // Run workflow
        runner.run();

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            String message1 = String.format("%d test cases generated and written to %s", runner.getNumberOfTestCases(), runner.getTargetDirJava());
            String message2 = String.format("Allure report available at %s", runner.getAllureReportsPath());
            String message3 = String.format("CSV stats available at %s/%s", PropertyManager.readProperty("data.tests.dir"), runner.getExperimentName());
            String message4 = String.format("Coverage report available at %s/%s", PropertyManager.readProperty("data.coverage.dir"), runner.getExperimentName());
            logger.info(message1);
            logger.info(message2);
            logger.info(message3);
            logger.info(message4);
        }

    }
}
