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

import static es.us.isa.restest.util.CommandRunner.runCommand;
import static es.us.isa.restest.util.FileManager.deleteFile;
import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;
import static es.us.isa.restest.util.TestManager.getTestCases;

public class ALDrivenTestCaseGenerator extends AbstractTestCaseGenerator {

	private String propertiesFilePath;
	private String mlUncertaintyPredictorCommand;					// TODO
	private Integer mlCandidatesRatio;								// TODO
	private Integer mlTrainingRequestsPerIteration;
	private Integer mlTrainingMaxIterationsNotLearning;
	private Float mlTrainingPrecisionThreshold;
	private Float mlResamplingRatio;
	private static final String CSV_NAME = "pool.csv";				// CSV of temporary test cases (the ones analyzed/output by the selector)
	private String poolFolderPath;

	private static Logger logger = LogManager.getLogger(ALDrivenTestCaseGenerator.class.getName());

	public ALDrivenTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
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
		List<TestCase> testCasesPool = new ArrayList<>();

		// Reset counters for the current operation
		resetOperation();

		boolean fulfillsDependencies = !hasDependencies(testOperation.getOpenApiOperation());

		// Repeat iterations until the desired number of test cases have been generated
		while (hasNext()) {
			testCasesPool.clear();
			while (testCasesPool.size() < (numberOfTests-nTests)*mlCandidatesRatio) {
				TestCase test = generateNextTestCase(testOperation);
				test.setFulfillsDependencies(fulfillsDependencies);
				testCasesPool.add(test);
			}

			// Export test cases to temporary CSV
			deleteFile(getPoolDataPath()); // Delete file first, so as to consider only test cases from this iteration
			testCasesPool.forEach(tc -> tc.exportToCSV(getPoolDataPath()));

			// Feed test cases to predictor, which queries and labels the best ones
			boolean commandOk = true;
			try {
				runCommand(mlUncertaintyPredictorCommand, new String[]{propertiesFilePath, Float.toString(mlResamplingRatio), Integer.toString(mlTrainingRequestsPerIteration)});
			} catch(RESTestException e) {
				commandOk = false;
			}

			if (commandOk) {
				// Read back test cases from CSV and update objects
				testCasesPool = getTestCases(getPoolDataPath());

				testCasesPool.forEach(tc ->{
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

	protected boolean hasNext() { return nTests < numberOfTests; }

	public String getPropertiesFilePath() {
		return propertiesFilePath;
	}

	public void setPropertiesFilePath(String propertiesFilePath) {
		this.propertiesFilePath = propertiesFilePath;
	}

	public String getMlUncertaintyPredictorCommand() {
		return mlUncertaintyPredictorCommand;
	}

	public void setMlUncertaintyPredictorCommand(String mlUncertaintyPredictorCommand) {
		this.mlUncertaintyPredictorCommand = mlUncertaintyPredictorCommand;
	}

	public Integer getMlCandidatesRatio() {
		return mlCandidatesRatio;
	}

	public void setMlCandidatesRatio(Integer mlCandidatesRatio) {
		this.mlCandidatesRatio = mlCandidatesRatio;
	}

	public Integer getMlTrainingRequestsPerIteration() {
		return mlTrainingRequestsPerIteration;
	}

	public void setMlTrainingRequestsPerIteration(Integer mlTrainingRequestsPerIteration) {
		this.mlTrainingRequestsPerIteration = mlTrainingRequestsPerIteration;
	}

	public Integer getMlTrainingMaxIterationsNotLearning() {
		return mlTrainingMaxIterationsNotLearning;
	}

	public void setMlTrainingMaxIterationsNotLearning(Integer mlTrainingMaxIterationsNotLearning) {
		this.mlTrainingMaxIterationsNotLearning = mlTrainingMaxIterationsNotLearning;
	}

	public Float getMlTrainingPrecisionThreshold() {
		return mlTrainingPrecisionThreshold;
	}

	public void setMlTrainingPrecisionThreshold(Float mlTrainingPrecisionThreshold) {
		this.mlTrainingPrecisionThreshold = mlTrainingPrecisionThreshold;
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

	public void setMlResamplingRatio(Float mlResamplingRatio) { this.mlResamplingRatio = mlResamplingRatio; }
}

