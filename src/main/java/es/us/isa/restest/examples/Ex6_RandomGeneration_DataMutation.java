package es.us.isa.restest.examples;

import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * TODO
 *
 * The resources for this example are located at src/main/resources/Examples/Ex1_RandomGeneration.
 *
 */
public class Ex6_RandomGeneration_DataMutation {

    public static String propertyFilePath="src/main/resources/Examples/Ex6_RandomGeneration_DataMutation/events.properties"; 		// Path to user properties file with configuration options

    public static void main(String[] args) throws RESTestException {
        // Load properties
        RESTestLoader loader = new RESTestLoader(propertyFilePath);

        // Create test case generator
        RandomTestCaseGenerator generator = (RandomTestCaseGenerator) loader.createGenerator();
        Collection<TestCase> testCases = generator.generate();

        // Create target directory for test cases if it does not exist
        createDir(loader.getTargetDirJava());

        // Write (RestAssured) test cases
        RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
        writer.write(testCases);

        System.out.println(testCases.size() + " test cases generated and written to " + loader.getTargetDirJava());
    }
}
