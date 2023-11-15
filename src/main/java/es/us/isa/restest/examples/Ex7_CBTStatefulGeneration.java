package es.us.isa.restest.examples;

import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * This example shows how to generate a set of test cases using the Constraint-Based Stateful test case generator and write them to a file using the RESTAssured writer.
 * Stateful test cases store certain values from responses, creating a dictionary to store them, and subsequently use those values in other tests.
 * This is reflected within the configuration file using BodyGenerator and ParameterGenerator.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex7_CBTStatefulGeneration.
 *
 */
public class Ex7_CBTStatefulGeneration {

    public static final String PROPERTY_FILE_PATH = "src/main/resources/Examples/Ex7_CBTStatefulGeneration/props.properties"; 		// Path to user properties file with configuration options

    private static final Logger logger = LogManager.getLogger(Ex7_CBTStatefulGeneration.class.getName());

    public static void main(String[] args) throws RESTestException {
        // Load properties
        RESTestLoader loader = new RESTestLoader(PROPERTY_FILE_PATH);

        // Create test case generator
        ConstraintBasedTestCaseGenerator generator = (ConstraintBasedTestCaseGenerator) loader.createGenerator();
        Collection<TestCase> testCases = generator.generate();

        // Create target directory for test cases if it does not exist
        createDir(loader.getTargetDirJava());

        // Write (RestAssured) test cases
        RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
        writer.write(testCases);

        logger.info(testCases.size() + " test cases generated and written to " + loader.getTargetDirJava());
    }
}
