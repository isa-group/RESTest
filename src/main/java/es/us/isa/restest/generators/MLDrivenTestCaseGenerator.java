package es.us.isa.restest.generators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.util.CommandRunner.runCommand;
import static es.us.isa.restest.util.FileManager.deleteFile;
import static es.us.isa.restest.util.TestManager.getTestCases;

public class MLDrivenTestCaseGenerator extends AbstractTestCaseGenerator {

	private String propertiesFilePath;
	private String mlValidityPredictorCommand;						// TODO
	private Integer mlCandidatesRatio;								// TODO
	private static final String CSV_NAME = "pool.csv";				// CSV of temporary test cases (the ones analyzed/output by the selector)
	private String poolFolderPath;

	private static Logger logger = LogManager.getLogger(MLDrivenTestCaseGenerator.class.getName());

	public MLDrivenTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
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
			iterationTestCases.clear();
			while (iterationTestCases.size() < (numberOfTests-nTests)*mlCandidatesRatio) { // TO DO: ml.candidates.ratio=100
				TestCase test = generateNextTestCase(testOperation);
				iterationTestCases.add(test);
			}

			// Export test cases to temporary CSV
			deleteFile(getPoolDataPath()); // Delete file first, so as to consider only test cases from this iteration
			iterationTestCases.forEach(tc -> tc.exportToCSV(getPoolDataPath()));

			// Feed test cases to predictor, which updates them
			boolean commandOk = true;
			try {
				runCommand(mlValidityPredictorCommand, new String[]{propertiesFilePath});
			} catch(RESTestException e) {
				commandOk = false;
			}

			if (commandOk) {
				// Read back test cases from CSV and update objects
				iterationTestCases = getTestCases(getPoolDataPath());

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

		deleteFile(getPoolDataPath()); // Delete pool file
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

	public String getPropertiesFilePath() {
		return propertiesFilePath;
	}

	public void setPropertiesFilePath(String propertiesFilePath) {
		this.propertiesFilePath = propertiesFilePath;
	}

	public String getMlValidityPredictorCommand() {
		return mlValidityPredictorCommand;
	}

	public void setMlValidityPredictorCommand(String mlValidityPredictorCommand) {
		this.mlValidityPredictorCommand = mlValidityPredictorCommand;
	}

	public Integer getMlCandidatesRatio() {
		return mlCandidatesRatio;
	}

	public void setMlCandidatesRatio(Integer mlCandidatesRatio) {
		this.mlCandidatesRatio = mlCandidatesRatio;
	}

	public String getPoolFolderPath() {
		return poolFolderPath;
	}

	public void setPoolFolderPath(String poolFolderPath) {
		this.poolFolderPath = poolFolderPath;
	}

	private String getPoolDataPath() {
		return poolFolderPath + "/" + CSV_NAME;
	}
}
