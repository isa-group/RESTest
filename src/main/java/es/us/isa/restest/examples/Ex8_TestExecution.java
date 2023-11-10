package es.us.isa.restest.examples;

import es.us.isa.restest.runners.RESTestExecutor;
import es.us.isa.restest.util.RESTestException;

/**
 * This example demonstrates how to execute a previously generated set of tests using the RESTestExecutor class. To do this,
 * the following parameters need to be specified: targetDir (destination directory of tests generated), testClassName (test class name),
 * and packageName (package name), this information can be found in the user properties file (see src/main/resources/Examples/Ex8_TestExecution/user_conf.properties).
 *
 * The resources for this example are located at src/main/resources/Examples/Ex8_TestExecution.
 *
 */

public class Ex8_TestExecution {


    public static final String PROPERTY_FILE_PATH = "src/main/resources/Examples/Ex8_TestExecution/user_config.properties"; 		// Path to user properties file with configuration options

    public static void main(String[] args) throws RESTestException {

        // Execute tests

        RESTestExecutor executor = new RESTestExecutor(PROPERTY_FILE_PATH);
        executor.execute();


    }
}
