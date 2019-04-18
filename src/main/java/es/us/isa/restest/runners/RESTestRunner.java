/**
 * 
 */
package es.us.isa.restest.runners;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.util.AllureReportManager;
import es.us.isa.restest.util.ClassLoader;

/**
 * This class a basic test workflow: test generation -> test writing -> class compilation and loading -> test execution -> test report generation
 * @author Sergio Segura
 *
 */
public class RESTestRunner {

	String targetDir;							// Directory where tests will be generated	
	String testClassName;						// Name of the class to be generated
	String packageName;							// Package name
	AbstractTestCaseGenerator generator;   		// Test case generator
	IWriter writer;								// RESTAssured writer
	AllureReportManager reportManager;			// Allure report manager
	int numTestCases = 0;						// Number of test cases generated so far
	private static final Logger logger = LogManager.getLogger(RESTestRunner.class.getName());
	
	public RESTestRunner(String testClassName, String targetDir, String packageName, AbstractTestCaseGenerator generator, IWriter writer, AllureReportManager reportManager) {
		this.targetDir = targetDir;
		this.packageName = packageName;
		this.testClassName = testClassName;
		this.generator = generator;
		this.writer = writer;
		this.reportManager = reportManager;
	}
	
	public void run() {

		// Test generation and writing (RESTAssured)
		testGeneration();
		
		// Load test class
		String filePath = targetDir + "/" + testClassName + ".java";
		String className = packageName + "." + testClassName;
		logger.info("Compiling and loading test class " + className + ".java");
		Class<?> testClass = ClassLoader.loadClass(filePath, className);
		
		// Test execution
		logger.info("Running tests");
		System.setProperty("allure.results.directory", reportManager.getResultsDirPath());
		testExecution(testClass);
		
		// Generate test report
		logger.info("Generating test report");
		reportManager.generateReport();
	}
	
	
	private void testGeneration() {
	    
		// Generate test cases
		logger.info("Generating tests");
        Collection<TestCase> testCases = generator.generate();
        this.numTestCases += testCases.size();
        
        // Write test cases
        String filePath = targetDir + "/" + testClassName + ".java";
        logger.info("Writing " + testCases.size() + " test cases to test class " + filePath);
        writer.write(testCases);

	}

	private static void testExecution(Class<?> testClass)  {
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		Result result = junit.run(testClass);
	
		int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
		logger.info(result.getRunCount() + " tests run in " + result.getRunTime()/1000 + " seconds. Successful: " + successfulTests +" , Failures: " + result.getFailureCount() + ", Ignored: " + result.getIgnoreCount());
	}
	
	
	public String getTargetDir() {
		return targetDir;
	}
	
	public void setTargetDir(String targetDir) {
		this.targetDir = targetDir;
	}
	
	public String getTestClassName() {
		return testClassName;
	}
	
	public void setTestClassName(String testClassName) {
		this.testClassName = testClassName;
	}

	public int getNumTestCases() {
		return numTestCases;
	}
	
	public void resetNumTestCases() {
		this.numTestCases=0;
	}
}
