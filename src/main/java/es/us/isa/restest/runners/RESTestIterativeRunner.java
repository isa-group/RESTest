package es.us.isa.restest.runners;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.util.Timer;
import es.us.isa.restest.writers.IWriter;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.Timer.TestStep.ALL;

/**
 * This class implement both generation and execution of test cases according to the configuration properties.
 * @author Sergio Segura
 *
 */
public class RESTestIterativeRunner extends RESTestLoader {

	private AbstractTestCaseGenerator generator;   		// Test case generator
	protected IWriter writer;							// RESTAssured writer
	protected AllureReportManager allureReportManager;	// Allure report manager
	protected StatsReportManager statsReportManager;	// Stats report manager
	protected RESTestWorkflow workflow;					// RESTest workflow (generation and execution)

	private static final Logger logger = LogManager.getLogger(RESTestIterativeRunner.class.getName());

	public RESTestIterativeRunner(String userPropertiesFilePath) throws RESTestException {
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
	 * Generates and executes test cases iteratively until the total number of test cases is reached.
	 * @throws RESTestException
	 */
	public void run() throws RESTestException {

		logger.info("Running workflow (generation -> execution -> reporting)...");

		// Reset number of test cases
		workflow.resetNumTestCases();

		Timer.startCounting(ALL);

		// Main loop
		int iteration = 1;
		while (totalNumTestCases == -1 || workflow.getNumTestCases() < totalNumTestCases) {

			// Introduce optional delay
			if (iteration != 1 && timeDelay != -1)
				delay(timeDelay);

			// Generate unique test class name to avoid the same class being loaded everytime
			String id = IDGenerator.generateTimeId();
			String className = testClassName + "_" + id;
			((RESTAssuredWriter) writer).setClassName(className);
			((RESTAssuredWriter) writer).setTestId(id);
			workflow.setTestClassName(className);
			workflow.setTestId(id);

			// Test case generation + execution + test report generation
			workflow.run();

			logger.info("Iteration {}. {} test cases generated.", iteration, workflow.getNumTestCases());
			iteration++;
		}

		Timer.stopCounting(ALL);

		generateTimeReport(iteration-1);

	}

	// Introduce delay
	private void delay(Integer time) {
		try {
			logger.info("Introducing delay of {} seconds", time);
			TimeUnit.SECONDS.sleep(time);
		} catch (InterruptedException e) {
			logger.error("Error introducing delay", e);
			logger.error(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	// Generate time report
	private void generateTimeReport(Integer iterations) {
		String timePath = PropertyManager.readProperty("data.tests.dir") + "/" + experimentName + "/" + PropertyManager.readProperty("data.tests.time");
		try {
			Timer.exportToCSV(timePath, iterations);
		} catch (RuntimeException e) {
			logger.error("The time report cannot be generated. Stack trace:");
			logger.error(e.getMessage());
		}
		logger.info("Time report generated.");
	}

	/**
	 * Number of generated test cases
	 */
	public int getNumberOfTestCases() {
		return workflow.getNumTestCases();
	}

}
