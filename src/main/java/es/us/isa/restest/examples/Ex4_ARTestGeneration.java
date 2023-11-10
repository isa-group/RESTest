package es.us.isa.restest.examples;

import es.us.isa.restest.generators.ARTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * This example shows how to generate a set of test cases using SemanticGenerator and write them to a file using the RESTAssured writer.
 * These types of tests are based on modifying a generated test so that it is entirely different from the previous one.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex4_ARTestGeneration.
 *
 * **/

public class Ex4_ARTestGeneration {

    // Need to create the file src\test\resources\auth\OMDb\apikeys.json
    public static final String PROPERTY_FILE_PATH = "src/main/resources/Examples/Ex4_ARTestGeneration/omdb.properties"; 		// Path to user properties file with configuration options

    private static final Logger logger = LogManager.getLogger(Ex4_ARTestGeneration.class.getName());

    public static void main(String[] args) throws RESTestException {
        // Load properties
        RESTestLoader loader = new RESTestLoader(PROPERTY_FILE_PATH);

        // Create test case generator
        ARTestCaseGenerator generator = (ARTestCaseGenerator) loader.createGenerator();
        Collection<TestCase> testCases = generator.generate();

        // Create target directory for test cases if it does not exist
        createDir(loader.getTargetDirJava());

        // Write (RestAssured) test cases
        RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
        writer.write(testCases);

        logger.info(testCases.size() + " test cases generated and written to " + loader.getTargetDirJava());

    }

}
