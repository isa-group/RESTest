package es.us.isa.restest.examples;

import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * TODO: This example shows how to generate a set of test cases using the Adaptive Random generator and write them to a file using the RESTAssured writer.
 * This test case generation strategy aims to ...
 * The values of each parameter are generated using different types of generators (e.g., fully random, random from a list, random from a range, etc.), specified in the test configuration file (and implemented in package es.us.isa.restest.inputs
 * See test cases for more examples.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex4_ARTGeneration.
 *
 */
public class Ex4_ARTGeneration {

    public static String propertyFilePath="<TODO>"; 		// Path to user properties file with configuration options

    public static void main(String[] args) throws RESTestException {

    }
}
