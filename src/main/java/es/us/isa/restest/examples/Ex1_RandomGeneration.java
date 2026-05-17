package es.us.isa.restest.examples;

import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * This example shows how to generate a set of random test cases and write them to a file using the RESTAssured writer.
 * The values of each parameter are generated using different types of generators (e.g., fully random, random from a list,
 * random from a range, etc.), specified in the test configuration file (and implemented in package es.us.isa.restest.inputs
 * In this example, test cases are generated for three different API operations: those included in the test configuration file (see src/main/resources/Examples/Ex1_RandomGeneration/test_conf.yaml).
 * See test cases for more examples.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex1_RandomGeneration.
 *
 */
public class Ex1_RandomGeneration {

    public static final String PROPERTY_FILE_PATH="src/main/resources/Examples/Ex1_RandomGeneration/user_config.properties";    // Path to user properties file with configuration options

    private static final Logger logger = LogManager.getLogger(Ex1_RandomGeneration.class.getName());

    public static void main(String[] args) throws RESTestException {
        // Load properties
        RESTestLoader loader = new RESTestLoader(PROPERTY_FILE_PATH);

        // Create test case generator
        RandomTestCaseGenerator generator = (RandomTestCaseGenerator) loader.createGenerator();
        Collection<TestCase> testCases = generator.generate();

        // Create target directory for test cases if it does not exist
        createDir(loader.getTargetDirJava());

        // Write (RestAssured) test cases
        RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
        writer.write(testCases);
        
        logger.info(testCases.size() + " test cases generated and written to " + loader.getTargetDirJava());
    }
}
