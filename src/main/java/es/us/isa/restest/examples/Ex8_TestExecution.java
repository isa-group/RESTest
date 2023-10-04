package es.us.isa.restest.examples;

import es.us.isa.restest.runners.RESTestExecutor;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.util.RESTestException;

/**
 * Ex8_TestExecution class serves as an entry point for executing a series of REST API test cases using RESTestRunner.
 *
 * - Specify the configuration options in the user properties file located at "src/main/resources/Examples/Ex8_TestExecution/user_config.properties".
 * - The test cases are executed using RESTestRunner, which orchestrates the test workflow.
 * - Upon completion, the number of executed test cases is printed to the console.
 *
 * The resources for this example are located at src/main/resources/Examples/Ex8_TestExecution.
 *
 */
public class Ex8_TestExecution {

    public static String targetDir = "src/main/resources/Examples/Ex8_TestExecution/test_cases";

    public static String testClassName = "RestCountriesTest";

    public static String packageName = "restcountries";



    public static void main(String[] args) throws RESTestException {

        RESTestExecutor executor = new RESTestExecutor(targetDir, testClassName, packageName);
        executor.execute();


    }
}
