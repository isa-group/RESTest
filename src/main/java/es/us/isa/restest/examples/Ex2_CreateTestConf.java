package es.us.isa.restest.examples;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


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

    public static final Logger logger = Logger.getLogger(Ex2_CreateTestConf.class.getName());


    public static void main(String[] args) {
        //TODO
    }
}
