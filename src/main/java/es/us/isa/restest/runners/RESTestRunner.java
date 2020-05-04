/**
 * 
 */
package es.us.isa.restest.runners;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.coverage.CoverageResults;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.*;
import es.us.isa.restest.util.ClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.IWriter;

import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.Timer.TestStep.*;

/**
 * This class a basic test workflow: test generation -> test writing -> class compilation and loading -> test execution -> test report generation -> test coverage report generation
 * @author Sergio Segura
 *
 */
public class RESTestRunner {

	private String targetDir;							// Directory where tests will be generated
	private String testClassName;						// Name of the class to be generated
	private String packageName;							// Package name
	private AbstractTestCaseGenerator generator;   		// Test case generator
	private IWriter writer;								// RESTAssured writer
	private AllureReportManager allureReportManager;	// Allure report manager
	private CSVReportManager csvReportManager;			// CSV report manager
	private CoverageMeter covMeter;						// Coverage meter
	private int numTestCases = 0;						// Number of test cases generated so far
//	private Timer timer;
	private static final Logger logger = LogManager.getLogger(RESTestRunner.class.getName());

	public RESTestRunner(String testClassName, String targetDir, String packageName, AbstractTestCaseGenerator generator, IWriter writer, AllureReportManager reportManager, CSVReportManager csvReportManager, CoverageMeter covMeter) {
		this.targetDir = targetDir;
		this.packageName = packageName;
		this.testClassName = testClassName;
		this.generator = generator;
		this.writer = writer;
		this.allureReportManager = reportManager;
		this.csvReportManager = csvReportManager;
//		this.timer = new Timer();
		this.covMeter = covMeter;
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
		System.setProperty("allure.results.directory", allureReportManager.getResultsDirPath());
		testExecution(testClass);

		// Print number of faulty and nominal test cases
		logger.info("Nominal test cases generated: " + generator.getnNominal());
		logger.info("Faulty test cases generated: " + generator.getnFaulty());


		if (csvReportManager.getEnableStats()) {
			logger.info("Exporting number of faulty and nominal test cases to CSV");
			String csvNFPath = csvReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testcases.nominalfaulty.file");
			generator.exportNominalFaultyToCSV(csvNFPath, testClassName);
		}
		
		// Generate test report
		logger.info("Generating test report");
		allureReportManager.generateReport();

		//Generate coverage report
		if(covMeter != null) {
			readTestResults();
			generateCoverageReport();
		}

//		//Generate time report
//		generateTimeReport();
	}

	private void testGeneration() {
	    
		// Generate test cases
		logger.info("Generating tests");
		generator.setnCurrentFaulty(0);
		generator.setnCurrentNominal(0);
		Timer.startCounting(TEST_SUITE_GENERATION);
		Collection<TestCase> testCases = generator.generate();
		Timer.stopCounting(TEST_SUITE_GENERATION);
        this.numTestCases += testCases.size();

        // Export test cases and nFaulty and nNominal to CSV if enableStats is true
		if (csvReportManager.getEnableStats()) {
			logger.info("Exporting test cases coverage to CSV");
			String csvTcPath = csvReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testcases.file");
			testCases.forEach(tc -> tc.exportToCSV(csvTcPath));

			// Generate input coverage data if enableStats and enableInputCoverage is true.
			if(csvReportManager.getEnableInputCoverage()) {
				String csvTcCoveragePath = csvReportManager.getCoverageDataDir() + "/" + PropertyManager.readProperty("data.coverage.testcases.file");
				testCases.forEach(tc -> CoverageMeter.exportCoverageOfTestCaseToCSV(csvTcCoveragePath, tc));
			}
		}

      // Update CoverageMeter with recently created test suite (if coverage is enabled).
		if (covMeter != null) {
			covMeter.addTestSuite(testCases);
		}
        
        // Write test cases
        String filePath = targetDir + "/" + testClassName + ".java";
        logger.info("Writing " + testCases.size() + " test cases to test class " + filePath);
        writer.write(testCases);

	}

	private void testExecution(Class<?> testClass)  {
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		Timer.startCounting(TEST_SUITE_EXECUTION);
		Result result = junit.run(testClass);
		Timer.stopCounting(TEST_SUITE_EXECUTION);
		int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
		logger.info(result.getRunCount() + " tests run in " + result.getRunTime()/1000 + " seconds. Successful: " + successfulTests +" , Failures: " + result.getFailureCount() + ", Ignored: " + result.getIgnoreCount());

	}

	/**
	 * Update CoverageMeter with the test results (if coverage is enabled).
	 */
	private void readTestResults() {
		String csvTrPath = csvReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testresults.file");
		List<TestResult> trs = TestManager.getTestResults(csvTrPath);
		covMeter.setTestResults(trs);
	}

	private void generateCoverageReport() {
		CoverageResults results = new CoverageResults(covMeter.getTotalCoverage(), covMeter.getInputCoverage(),
				covMeter.getOutputCoverage());
		results.setCoverageOfCoverageCriteriaFromCoverageMeter(covMeter);
		results.setCoverageOfCriterionTypeFromCoverageMeter(covMeter);
		try {
			results.exportCoverageReportToJSON(csvReportManager.getCoverageDataDir() + "/" + PropertyManager.readProperty("data.coverage.computation.file"));
		} catch (IOException e) {
			logger.error("The coverage report cannot be generated. Stack trace:");
			logger.log(Level.valueOf("context"), e);
		}
		logger.info("Coverage report generated.");
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
