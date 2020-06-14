package es.us.isa.restest.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.Timer;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;
import static es.us.isa.restest.testcases.TestCase.checkFaulty;
import static es.us.isa.restest.util.Timer.TestStep.TEST_CASE_GENERATION;

public class RandomTestCaseGenerator extends AbstractTestCaseGenerator {
	
	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
	}

	@Override
	protected Collection<TestCase> generateOperationTestCases(Operation testOperation) {

		List<TestCase> testCases = new ArrayList<>();

		// Whether the next test case to generate must be faulty or not
		String faultyReason = "none";
		if (faultyRatio > 0)
			faultyReason = "individual_parameter_constraint";

		while (hasNext()) {

			// Generate faulty test cases until faultyRatio is reached
			if (!faultyReason.equals("none") && (float)index/(float)numberOfTest >= faultyRatio)
				faultyReason = "none";

			// Create test case with specific parameters and values
			Timer.startCounting(TEST_CASE_GENERATION);
			TestCase test = generateNextTestCase(testOperation, faultyReason);
			Timer.stopCounting(TEST_CASE_GENERATION);
			// Authentication
			authenticateTestCase(test);

			// Add test case to the collection
			testCases.add(test);
		}

		return testCases;
	}

	// Generate the next test case and update the generation index
	@Override
	protected TestCase generateNextTestCase(Operation testOperation, String faultyReason) {

		// This way, all test cases of an operation are not executed one after the other, but randomly:
		String testId = "test_" + IDGenerator.generateId() + "_" + removeNotAlfanumericCharacters(testOperation.getOperationId());
		TestCase test = new TestCase(testId, !faultyReason.equals("none"), testOperation.getOperationId(), testOperation.getTestPath(), HttpMethod.valueOf(testOperation.getMethod().toUpperCase()));
		test.setFaultyReason(faultyReason);

		// Set parameters
		setTestCaseParameters(test, testOperation);

		if (!faultyReason.equals("none") && !makeTestCaseFaulty(test, testOperation.getOpenApiOperation())) { // If this test case must be faulty
			test.setFaulty(false); // ... set faulty to false, in order to have the right oracle
			test.setFaultyReason("none");
		}

		if (!test.getFaulty() && checkFaulty(test, validator)) { // Before returning test case, if faulty==false, it may still be faulty (due to mutations of JSONmutator)
			test.setFaulty(true);
			test.setFaultyReason("invalid_request_body");
		}

		updateIndexes(test.getFaulty());
		return test;
	}
	
	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		Boolean res = index<numberOfTest;
		if (index == numberOfTest)
			index = 0;
		return res;
	}
}
