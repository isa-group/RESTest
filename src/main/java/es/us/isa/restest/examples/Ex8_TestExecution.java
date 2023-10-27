package es.us.isa.restest.examples;

import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.runners.RESTestExecutor;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;

import java.util.Collection;
import java.util.logging.Logger;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * This example demonstrates how to execute a previously generated set of tests using the RESTestExecutor class. To do this,
 * the following parameters need to be specified: targetDir (destination directory of tests generated), testClassName (test class name),
 * and packageName (package name), this information can be found in the user properties file (see src/main/resources/Examples/Ex8_TestExecution/user_conf.properties).
 *
 * The resources for this example are located at src/main/resources/Examples/Ex8_TestExecution.
 *
 */
public class Ex8_TestExecution {


    public static final String PROPERTY_FILE_PATH = "src/main/resources/Examples/Ex8_TestExecution/user_config.properties"; 		// Path to user properties file with configuration options

    public static final Logger logger = Logger.getLogger(Ex8_TestExecution.class.getName());


    public static void main(String[] args) throws RESTestException {

        // Create tests if they do not exist

        // Load properties
        RESTestLoader loader = new RESTestLoader(PROPERTY_FILE_PATH);

        // Create test case generator
        ConstraintBasedTestCaseGenerator generator = (ConstraintBasedTestCaseGenerator) loader.createGenerator();
        Collection<TestCase> testCases = generator.generate();

        // Create target directory for test cases if it does not exist
        createDir(loader.getTargetDirJava());

        // Create stats report manager
        loader.createStatsReportManager();

        // Write (RestAssured) test cases
        RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
        writer.write(testCases);

        if(logger.isLoggable(java.util.logging.Level.INFO)) {
            String message = String.format("%d test cases generated and written to %s", testCases.size(), loader.getTargetDirJava());
            logger.info(message);
        }


        // Execute tests

        RESTestExecutor executor = new RESTestExecutor(PROPERTY_FILE_PATH);
        executor.execute();


    }
}
