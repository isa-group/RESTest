package es.us.isa.restest.main;

import java.util.Collection;

import org.junit.runner.JUnitCore;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.AllureReportManager;
import es.us.isa.restest.util.ClassLoader;
import es.us.isa.restest.util.PropertyManager;


public class BikeWise {

	static int numTestCases = 5;												// Number of test cases per operation
	static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		// Path to OAS specification file
	static String confPath = "src/test/resources/Bikewise/fullConf.yaml";		// Path to test configuration file
	static String targetDir = "src/generation/java/restassured";				// Directory where tests will be generated	
	static String APIName = "Bikewise";											// API name
	static String testClassName = "BikewiseTest";								// Name of the class to be generated
	static String packageName = "restassured";									// Package name
	
	public static void main(String[] args) {
		
		String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/" + APIName;
		String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/" + APIName;
				
		// Test generation and writing (RESTAssured)
		System.out.println("Generating test...");
		testGeneration();
		
		// Load test class
		System.out.println("Compiling and loading test class...");
		String filePath = targetDir + "/" + testClassName + ".java";
		String className = packageName + "." + testClassName;
		Class<?> testClass = ClassLoader.loadClass(filePath, className);
		
		// Test execution
		System.out.println("Running tests...");
		System.setProperty("allure.results.directory", allureResultsDir);
		testExecution(testClass);
		
		// Generate test report
		System.out.println("Generating test report...");
		AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);
		arm.generateReport();
		
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
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDir, testClassName, packageName, basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.setAllureReport(true);
        writer.write(testCases);
	}
	
	
	private static void testExecution(Class<?> testClass)  {
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		junit.run(testClass);
	}
	
	
}