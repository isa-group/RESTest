package es.us.isa.restest.examples;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.FileManager.deleteFile;
import static org.junit.Assert.assertTrue;

/**
 * All RESTest test case generators requires an input test configuration file specifying the operations to be tested, test data generators for each input parameter, authentication details, etc.
 * This example shows how to generate a default test configuration file for an API given its OAS specification.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex2_CreateTestConf.
 *
 */
public class Ex2_CreateTestConf {

    public static String specPath="src/main/resources/Examples/Ex2_CreateTestConf/spec_bigoven.yaml"; 		// Path to OAS specification file
    public static String confPath="src/main/resources/Examples/Ex2_CreateTestConf/default_test_conf.yaml"; 		// Path to test configuration file


    public static void main(String[] args) throws RESTestException {

        // Load specification file
        OpenAPISpecification spec = new OpenAPISpecification(specPath);

        // Create filters to indicate which operations (paths and http methods) to include in the test configuration file.
        List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
        TestConfigurationFilter filter = new TestConfigurationFilter();
        filter.setPath("/recipes");
        filter.addGetMethod();
        filters.add(filter);

        // Generate default test configuration file
        DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
        gen.generate(confPath, filters);

        System.out.println("Default test configuration file generated at " + confPath);
    }
}
