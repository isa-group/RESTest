package es.us.isa.restest.runners;

import java.util.Collection;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.*;
import es.us.isa.restest.util.ClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.IWriter;

import static es.us.isa.restest.util.Timer.TestStep.*;

/**
 * This class implements a basic test workflow: test generation -&gt; test writing -&gt; class compilation and loading -&gt; test execution -&gt; test report generation -&gt; test coverage report generation
 * @author Sergio Segura
 *
 */
public class RESTestRunner {

	protected String targetDir;							// Directory where tests will be generated
	protected String testClassName;						// Name of the class to be generated
	private String testId;
	private String packageName;							// Package name
	private AbstractTestCaseGenerator generator;   		// Test case generator
	protected IWriter writer;							// RESTAssured writer
	protected AllureReportManager allureReportManager;	// Allure report manager
	protected StatsReportManager statsReportManager;	// Stats report manager
	private boolean executeTestCases = true;			// Whether to execute test cases
	private boolean allureReports = true;				// Whether to actually generate reports or not (folder "allure-reports")
	private int numTestCases = 0;						// Number of test cases generated so far

	private boolean learnRegex;
	private boolean secondPredicateSearch;
	private OpenAPISpecification spec;
	private String confPath;

	private static final Logger logger = LogManager.getLogger(RESTestRunner.class.getName());

	public RESTestRunner(String testClassName, String targetDir, String packageName, Boolean learnRegex, Boolean secondPredicateSearch, OpenAPISpecification spec, String confPath, AbstractTestCaseGenerator generator, IWriter writer, AllureReportManager reportManager, StatsReportManager statsReportManager) {
		this.targetDir = targetDir;
		this.packageName = packageName;
		this.testClassName = testClassName;
		this.generator = generator;
		this.writer = writer;
		this.allureReportManager = reportManager;
		this.statsReportManager = statsReportManager;

		this.learnRegex = learnRegex;
		this.secondPredicateSearch = secondPredicateSearch;
		this.spec = spec;
		this.confPath = confPath;

	}
	  
	public void run() throws RESTestException {

		// Test generation and writing (RESTAssured)
		testGeneration();

		if(executeTestCases) {
			// Test execution
			logger.info("Running tests");
			System.setProperty("allure.results.directory", allureReportManager.getResultsDirPath());
			testExecution(getTestClass());
		}

		generateReports();

		if(learnRegex){
			statsReportManager.learn(testId, spec, confPath);
		}
	}

	protected void generateReports() {
		if(executeTestCases && allureReports) {
			// Generate test report
			logger.info("Generating test report");
			allureReportManager.generateReport();
		}

		// Generate coverage report
		logger.info("Generating CSV data");
		statsReportManager.generateReport(testId, executeTestCases);
	}

	protected Class<?> getTestClass() {
		// Load test class
		String filePath = targetDir + "/" + testClassName + ".java";
		String className = packageName + "." + testClassName;
		logger.info("Compiling and loading test class {}.java", className);
		return ClassLoader.loadClass(filePath, className);
	}

	private void testGeneration() throws RESTestException {
	    
		// Generate test cases
		logger.info("Generating tests");
		Timer.startCounting(TEST_SUITE_GENERATION);
		Collection<TestCase> testCases = generator.generate();
		Timer.stopCounting(TEST_SUITE_GENERATION);
        this.numTestCases += testCases.size();

        // Pass test cases to the statistic report manager (CSV writing, coverage)
        statsReportManager.setTestCases(testCases);
        
        // Write test cases
        String filePath = targetDir + "/" + testClassName + ".java";
        logger.info("Writing {} test cases to test class {}", testCases.size(), filePath);
        writer.write(testCases);

	}

	protected void testExecution(Class<?> testClass)  {
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		Timer.startCounting(TEST_SUITE_EXECUTION);
		Result result = junit.run(testClass);
		Timer.stopCounting(TEST_SUITE_EXECUTION);
		int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
		logger.info("{} tests run in {} seconds. Successful: {}, Failures: {}, Ignored: {}", result.getRunCount(), result.getRunTime()/1000, successfulTests, result.getFailureCount(), result.getIgnoreCount());

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

	public void setTestId(String testId) {
		this.testId = testId;
	}

	public void setExecuteTestCases(Boolean executeTestCases) {
		this.executeTestCases = executeTestCases;
	}

	public void setAllureReport(boolean allureReports) {
		this.allureReports = allureReports;
	}
}
