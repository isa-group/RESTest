package es.us.isa.restest.generators;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.util.FileManager.deleteFile;
import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;
import static es.us.isa.restest.util.TestManager.getTestCases;

public class MLDrivenTestCaseGenerator extends AbstractTestCaseGenerator {

	private String propertiesFilePath;
	private String mlValidityPredictorCommand;						// TODO
	private Integer mlCandidatesRatio;								// TODO
	private Float mlResamplingRatio;
	private static final String CSV_NAME = "pool.csv";				// CSV of temporary test cases (the ones analyzed/output by the selector)
	private String experimentFolder;

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

		boolean fulfillsDependencies = !hasDependencies(testOperation.getOpenApiOperation());

		// Repeat iterations until the desired number of faulty and nominal test cases have been generated
		while (hasNext()) {
			iterationTestCases.clear();
			while (iterationTestCases.size() < (numberOfTests-nTests)*mlCandidatesRatio) {
				TestCase test = generateNextTestCase(testOperation);
				test.setFulfillsDependencies(fulfillsDependencies);
				iterationTestCases.add(test);
			}

			// Export test cases to temporary CSV
			deleteFile(getPoolDataPath()); // Delete file first, so as to consider only test cases from this iteration
			iterationTestCases.forEach(tc -> tc.exportToCSV(getPoolDataPath()));

			// Feed test cases to predictor, which updates them
			boolean commandOk = true;

			Response response = RestAssured.given()
					.queryParam("trainingPath", experimentFolder)
					.queryParam("targetPath", getPoolDataPath())
					.queryParam("resamplingRatio", mlResamplingRatio.toString())
					.queryParam("propertiesPath", propertiesFilePath)
					.get("http://localhost:8000/validity")
					.andReturn();

			// print status code
			commandOk = response.statusCode() == 200;

			if (commandOk) {
				// Read back test cases from CSV and update objects
				iterationTestCases = getTestCases(getPoolDataPath());

				// Add test cases one by one until desired number is reached both for nominal and faulty
				for (TestCase tc : iterationTestCases) {

					if (!hasNext()) {
						break;
					}

					if ((Boolean.TRUE.equals(tc.getFaulty()) && hasNextFaulty()) ||
							(Boolean.FALSE.equals(tc.getFaulty()) && hasNextNominal())) {
						// Set authentication data (if any)
						authenticateTestCase(tc);

						// Add test case to the collection
						testCases.add(tc);

						// Update indexes
						updateIndexes(tc);
					}
				}
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

	public String getExperimentFolder() {
		return experimentFolder;
	}

	public void setExperimentFolder(String experimentFolder) {
		this.experimentFolder = experimentFolder;
	}

	private String getPoolDataPath() {
		return experimentFolder + "/" + CSV_NAME;
	}

	public void setMlResamplingRatio(Float mlResamplingRatio) {	this.mlResamplingRatio = mlResamplingRatio; }
}
