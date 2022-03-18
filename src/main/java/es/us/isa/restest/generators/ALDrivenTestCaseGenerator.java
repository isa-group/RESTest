package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

import static es.us.isa.restest.util.FileManager.deleteFile;
import static es.us.isa.restest.util.TestManager.getTestCases;

public class ALDrivenTestCaseGenerator extends AbstractTestCaseGenerator {

	private String alPredictorCommand;								// TODO
	private Integer alCandidatesRatio;								// TODO
	private String resourcesFolderPath; 							// Path to the folder containing resources shared between RESTest and selector
	private static final String CSV_NAME = "pool.csv";				// CSV of temporary test cases (the ones analyzed/output by the selector)
	private String csvTmpTcPath; 									// resourcesFolderPath + "/" + CSV_NAME

	private static Logger logger = LogManager.getLogger(ALDrivenTestCaseGenerator.class.getName());

	public ALDrivenTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);

		// Predictor command
		String os = System.getProperty("os.name");
		if (os.contains("Windows"))
			alPredictorCommand = PropertyManager.readProperty("al.predictor.command.windows");
		else
			alPredictorCommand = PropertyManager.readProperty("al.predictor.command.unix");
	}

	/**
	 * This generator works by iterations. In every iteration, the desired number of test cases
	 * are generated. Then, the predictor is executed on such test cases, which are labeled
	 * accordingly in "nominal" and "faulty". New iterations are run until the desired number of
	 * both expected nominal and faulty test cases are generated
	 * @param testOperation
	 * @return
	 * @throws RESTestException
	 */
	@Override
	protected Collection<TestCase> generateOperationTestCases(Operation testOperation) throws RESTestException {

		List<TestCase> testCases = new ArrayList<>();
		List<TestCase> queriedTestCases = new ArrayList<>();

		// Reset counters for the current operation
		resetOperation();

		// Repeat iterations until the desired number of test cases have been generated
		while (hasNext()) {
			queriedTestCases.clear();
			while (queriedTestCases.size() < (numberOfTests-nTests)*alCandidatesRatio) {
				TestCase test = generateNextTestCase(testOperation);
				queriedTestCases.add(test);
			}

			// Export test cases to temporary CSV
			deleteFile(csvTmpTcPath); // Delete file first, so as to consider only test cases from this iteration
			queriedTestCases.forEach(tc -> tc.exportToCSV(csvTmpTcPath));

			// Feed test cases to predictor, which queries and labels the best ones
			boolean commandOk = false;
			try {
				ProcessBuilder pb = new ProcessBuilder(alPredictorCommand, resourcesFolderPath, csvTmpTcPath, ((Integer) numberOfTests).toString());
				pb.inheritIO(); // Print output of program to stdout
				Process proc = pb.start();
				proc.waitFor();
				commandOk = true;
			} catch (IOException e) {
				logger.error("Error running AL selector");
				logger.error("Exception: ", e);
			} catch (InterruptedException e) {
				logger.error("Error running AL selector");
				logger.error("Exception: ", e);
				Thread.currentThread().interrupt();
			}
			if (commandOk) {
				// Read back test cases from CSV and update objects
				queriedTestCases = getTestCases(csvTmpTcPath);

				queriedTestCases.forEach(tc ->{
					if (hasNext()) {
						// Set authentication data (if any)
						authenticateTestCase(tc);

						// Add test case to the collection
						testCases.add(tc);

						// Update indexes
						updateIndexes(tc);
					}
				});
			}
		}
		deleteFile(csvTmpTcPath); // Delete pool file
		return testCases;
	}


	// Generate the next test case
	public TestCase generateNextTestCase(Operation testOperation) throws RESTestException {

		// We only try to generate valid test cases, and the predictor classifies (and possibly discards) them
		TestCase test = generateRandomValidTestCase(testOperation);

		checkTestCaseValidity(test);

		return test;
	}

	protected boolean hasNext() { return nTests < numberOfTests; }

	public void setResourcesFolderPath(String resourcesFolderPath) { this.resourcesFolderPath = resourcesFolderPath; this.csvTmpTcPath = resourcesFolderPath + "/" + CSV_NAME; }

	public void setAlCandidatesRatio(Integer numberOfCandidates) { this.alCandidatesRatio = numberOfCandidates; }

}

