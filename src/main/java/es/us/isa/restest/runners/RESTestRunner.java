package es.us.isa.restest.runners;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.ClassLoader;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.util.Timer;
import es.us.isa.restest.writers.IWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.Timer.TestStep.TEST_SUITE_EXECUTION;
import static es.us.isa.restest.util.Timer.TestStep.TEST_SUITE_GENERATION;

/**
 * This class implement both generation and execution of test cases according to the configuration properties.
 * @author Sergio Segura
 *
 */
public class RESTestRunner extends RESTestLoader {

	private AbstractTestCaseGenerator generator;   		// Test case generator
	protected IWriter writer;							// RESTAssured writer
	protected AllureReportManager allureReportManager;	// Allure report manager
	protected StatsReportManager statsReportManager;	// Stats report manager
	protected RESTestWorkflow workflow;					// RESTest workflow (generation and execution)

	private static final Logger logger = LogManager.getLogger(RESTestRunner.class.getName());

	public RESTestRunner(String userPropertiesFilePath) throws RESTestException {
		super(userPropertiesFilePath);

		// Create target directory if it does not exist
		createDir(targetDirJava);

		// Create RESTest workflo (generation -> execution -> reporting)
		logger.info("Generating RESTest workflow...");
		generator = createGenerator(); 						// Test case generator
		writer = createWriter(); 							// Test case writer
		allureReportManager = createAllureReportManager(); 	// Allure test case reporter
		statsReportManager = createStatsReportManager(); 	// Stats reporter
		workflow = new RESTestWorkflow(testClassName,
				targetDirJava,
				packageName,
				spec,
				confPath,
				generator,
				writer,
				allureReportManager,
				statsReportManager);						// RESTest workflow (generation and execution)

		workflow.setAllureReport(allureReports);			// Enable/disable allure generation
		workflow.setExecuteTestCases(executeTestCases);		// Enable/disable test execution
		workflow.setTestId(experimentName);

	}


	/**
	 * Generates and executes test cases
	 * @throws RESTestException
	 */
	public void run() throws RESTestException {

		logger.info("Running workflow (generation -> execution -> reporting)...");
		workflow.run();										// Run RESTest workflow

	}

	/**
	 * Number of generated test cases
	 */
	public int getNumberOfTestCases() {
		return workflow.getNumTestCases();
	}

}
