package es.us.isa.restest.examples;

import es.us.isa.restest.runners.RESTestIterativeRunner;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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

        private static final Logger logger = LogManager.getLogger(Ex10_Iterative_Generation_Execution.class.getName());

    public static void main(String[] args) throws RESTestException {

        // Load properties
        RESTestIterativeRunner runner = new RESTestIterativeRunner(PROPERTY_FILE_PATH);

        // Run workflow
        runner.run();

        logger.info(runner.getNumberOfTestCases() + " test cases generated and written to " + runner.getTargetDirJava());
        logger.info("Allure report available at " + runner.getAllureReportsPath());
        logger.info("CSV stats available at " + PropertyManager.readProperty("data.tests.dir") + "/" + runner.getExperimentName());
        logger.info("Coverage report available at " + PropertyManager.readProperty("data.coverage.dir") + "/" + runner.getExperimentName());


    }
}
