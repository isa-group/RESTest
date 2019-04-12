package es.us.isa.restest.main;

import java.util.Collection;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writters.RESTAssuredWritter;


public class BikeWise {

	static int numTestCases = 5;
	static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
	static String confPath = "src/test/resources/Bikewise/fullConf.yaml";
	static String targetDir = "src/generation/java/restassured";
	static String testClassName = "Bikewise";
	static String packageName = "restassured";
	
	
	public static void main(String[] args) throws ClassNotFoundException {

		// Test generation
		//testGeneration();
		
		// Test execution
		testExecution();
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
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        writer.write(OAISpecPath, targetDir, testClassName, packageName, basePath.toLowerCase(), testCases);
	}
	
	
	private static void testExecution() throws ClassNotFoundException {
		
		Class<?> testClass = Class.forName(packageName + "." + testClassName + "Test");
		System.out.println(testClass.toString());
		
		System.setProperty("allure.results.directory", "target/allure-results");
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		junit.run(testClass);
	}
	
	
}