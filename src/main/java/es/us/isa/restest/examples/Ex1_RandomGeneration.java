package es.us.isa.restest.examples;

import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public static final String PROPERTY_FILE_PATH="src/main/resources/Examples/Ex1_RandomGeneration/user_config.properties"; 		// Path to user properties file with configuration options

    public static final Logger logger = Logger.getLogger(Ex1_RandomGeneration.class.getName());

    public static void main(String[] args) throws RESTestException {
        //TODO
    }
}
