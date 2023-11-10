package es.us.isa.restest.examples;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;


/**
 * All RESTest test case generators requires an input test configuration file specifying the API operations to be tested,
 * test data generators for each input parameter, authentication details, etc.
 * This example shows how to generate a default test configuration file for an API given its OAS specification.
 * See test cases for more examples.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex2_CreateTestConf.
 *
 */
public class Ex2_CreateTestConf {

    public static final String SPEC_PATH = "src/main/resources/Examples/Ex2_CreateTestConf/spec_bigoven.yaml"; 		// Path to OAS specification file
    public static final String CONF_PATH = "src/main/resources/Examples/Ex2_CreateTestConf/default_test_conf.yaml"; 		// Path to test configuration file

    private static final Logger logger = LogManager.getLogger(Ex2_CreateTestConf.class.getName());

    public static void main(String[] args) {

        // Load specification file
        OpenAPISpecification spec = new OpenAPISpecification(SPEC_PATH);

        // Create filters to indicate which operations (paths and http methods) to include in the test configuration file.
        List<TestConfigurationFilter> filters = new ArrayList<>();
        TestConfigurationFilter filter = new TestConfigurationFilter();
        filter.setPath("/recipes");
        filter.addGetMethod();
        filters.add(filter);

        // Generate default test configuration file
        DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
        gen.generate(CONF_PATH, filters);

        logger.info("Default test configuration file generated at " + CONF_PATH);

    }
}
