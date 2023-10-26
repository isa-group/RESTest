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

    public static final Logger logger = Logger.getLogger(Ex6_RandomGeneration_DataMutation.class.getName());

    public static void main(String[] args) throws RESTestException {
        //TODO
    }
}
