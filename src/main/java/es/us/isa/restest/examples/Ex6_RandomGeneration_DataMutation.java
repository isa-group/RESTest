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
 * This example demonstrates how to generate a set of random test cases and write them to a file using the RESTAssured writer,
 * while also applying data mutation to the generated test cases. To apply data mutation, we utilize the RandomTestCaseGenerator
 * class and ObjectPerturbator, which is a versatile tool designed to transform JSON objects, commonly used as inputs for API
 * operations, into new JSON objects. These transformed objects may be invalid (although not guaranteed), allowing developers
 * to test various data scenarios.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex6_RandomGeneration_DataMutation.
 *
 */
public class Ex6_RandomGeneration_DataMutation {

    public static final String PROPERTY_FILE_PATH = "src/main/resources/Examples/Ex6_RandomGeneration_DataMutation/events.properties"; 		// Path to user properties file with configuration options


    private static final Logger logger = LogManager.getLogger(Ex6_RandomGeneration_DataMutation.class.getName());


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
