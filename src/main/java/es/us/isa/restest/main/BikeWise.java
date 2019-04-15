package es.us.isa.restest.main;

import java.util.Collection;

import org.junit.runner.JUnitCore;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writters.RESTAssuredWritter;
import es.us.isa.restest.util.ClassLoader;


public class BikeWise {

	static int numTestCases = 1;												// Number of test cases per operation
	static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		// Path to OAS specification file
	static String confPath = "src/test/resources/Bikewise/fullConf.yaml";		// Path to test configuration file
	static String targetDir = "src/generation/java/restassured";				// Directory where test will be generated
	static String testClassName = "BikewiseTest";								// Prefix of the test class to be generated
	static String packageName = "restassured";									// Package name
	
	public static void main(String[] args) {
		
		
		// Test generation and writing (RESTAssured)
		testGeneration();
		
		// Load test class
		String filePath = targetDir + "/" + testClassName + ".java";
		String className = packageName + "." + testClassName;
		Class<?> testClass = ClassLoader.loadClass(filePath, className);
		
		// Test execution
		System.setProperty("allure.results.directory", "target/allure-results");
		testExecution(testClass);
		
		// Generate test report		
	}
	
	private static void testGeneration() {
	    
		// Load specification
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(confPath);

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = generator.generate();

        // Write RESTAssured test cases
        RESTAssuredWritter writer = new RESTAssuredWritter();
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.setAllureReport(true);
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        writer.write(OAISpecPath, targetDir, testClassName, packageName, basePath.toLowerCase(), testCases);
	}
	
	
	private static void testExecution(Class<?> testClass)  {
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		junit.run(testClass);
	}
	
	
}