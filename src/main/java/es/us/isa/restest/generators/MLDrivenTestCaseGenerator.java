package es.us.isa.restest.generators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.util.FileManager.deleteFile;
import static es.us.isa.restest.util.TestManager.getTestCases;

public class MLDrivenTestCaseGenerator extends AbstractTestCaseGenerator {

	private String mlPredictorCommand;
	private String resourcesPath; // Path to the experiment folder
	private String csvTmpTcPath;  // Path where to import/export temporary test cases (the ones analyzed/output by the predictor)

	private static Logger logger = LogManager.getLogger(MLDrivenTestCaseGenerator.class.getName());

	public MLDrivenTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);

		// Predictor command
		String os = System.getProperty("os.name");
		if (os.contains("Windows"))
			mlPredictorCommand = PropertyManager.readProperty("mlPredictor.command.windows");
		else
			mlPredictorCommand = PropertyManager.readProperty("mlPredictor.command.unix");
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
		List<TestCase> iterationTestCases = new ArrayList<>();

		// Reset counters for the current operation
		resetOperation();

		// Repeat iterations until the desired number of faulty and nominal test cases have been generated
		while (hasNext()) {
			while (iterationTestCases.size() < numberOfTests) {
				TestCase test = generateNextTestCase(testOperation);
				iterationTestCases.add(test);
			}

			// Export test cases to temporary CSV
			deleteFile(csvTmpTcPath); // Delete file first, so as to consider only test cases from this iteration
			iterationTestCases.forEach(tc -> tc.exportToCSV(csvTmpTcPath));

			// Feed test cases to predictor, which updates them
			Runtime rt = Runtime.getRuntime();

			boolean commandOk = false;
			try {
				Process proc = rt.exec(mlPredictorCommand + " " + resourcesPath); // TODO: program arguments (e.g., CSV path)
				proc.waitFor();
				commandOk = true;
			} catch (IOException e) {
				logger.error("Error running ML predictor");
				logger.error("Exception: ", e);
			} catch (InterruptedException e) {
				logger.error("Error running ML predictor");
				logger.error("Exception: ", e);
				Thread.currentThread().interrupt();
			}

			if (commandOk) {
				// Read back test cases from CSV and update objects
				iterationTestCases = getTestCases(csvTmpTcPath);

				// Add test cases one by one until desired number is reached both for nominal and faulty
				iterationTestCases.forEach(tc -> {
					if ((Boolean.TRUE.equals(tc.getFaulty()) && hasNextFaulty()) ||
							(Boolean.FALSE.equals(tc.getFaulty()) && hasNextNominal())) {
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
		
		return testCases;
	}
	

	// Generate the next test case
	public TestCase generateNextTestCase(Operation testOperation) throws RESTestException {

		// We only try to generate valid test cases, and the predictor classifies (and possibly discards) them
		TestCase test = generateRandomValidTestCase(testOperation);

		checkTestCaseValidity(test);

		return test;
	}

	protected boolean hasNext() {
		return hasNextFaulty() || hasNextNominal();
	}

	private boolean hasNextFaulty() {
		return nFaulty < (int) (faultyRatio * numberOfTests);
	}

	private boolean hasNextNominal() {
		return nNominal < (int) ((1 - faultyRatio) * numberOfTests);
	}

	public void setResourcesPath(String resourcesPath) { this.resourcesPath = resourcesPath; }

	public String getResourcesPath() { return resourcesPath; }

	public String getCsvTmpTcPath() {
		return csvTmpTcPath;
	}

	public void setCsvTmpTcPath(String csvTmpTcPath) {
		this.csvTmpTcPath = csvTmpTcPath;
	}
}

