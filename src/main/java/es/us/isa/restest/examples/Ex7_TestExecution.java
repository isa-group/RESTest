package es.us.isa.restest.examples;

import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.runners.RESTestExecutor;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.runners.RESTestWorkflow;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;

/**
 * This example demonstrates how to execute a previously generated set of tests using the RESTestExecutor class. To do this,
 * the following parameters need to be specified: targetDir (destination directory of tests generated), testClassName (test class name),
 * and packageName (package name), this information can be found in the user properties file (see src/main/resources/Examples/Ex7_TestExecution/user_conf.properties).
 *
 * The resources for this example are located at src/main/resources/Examples/Ex7_TestExecution.
 *
 */
public class Ex7_TestExecution {

    public static String targetDir = "src/main/resources/Examples/Ex7_TestExecution/test_cases";

    public static String testClassName = "RestCountriesTest";

    public static String packageName = "restcountries";



    public static void main(String[] args) throws RESTestException {

        RESTestExecutor executor = new RESTestExecutor(targetDir, testClassName, packageName);
        executor.execute();


    }
}
