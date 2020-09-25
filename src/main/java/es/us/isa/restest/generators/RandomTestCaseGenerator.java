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

		// If faultyRatio > 0 generate faulty test cases (by default due to violations in individual parameter constraints)
		String faultyReason = "none";
		if (faultyRatio > 0)
			faultyReason = "individual_parameter_constraint";

		while (hasNext()) {

			// If the ratio of faulty test cases has been reached, stop generating faulty test cases
			if (!faultyReason.equals("none") && (float)index/(float)numberOfTests >= faultyRatio)
				faultyReason = "none";

			// Create test case with specific parameters and values
			Timer.startCounting(TEST_CASE_GENERATION);
			TestCase test = generateNextTestCase(testOperation, faultyReason);
			Timer.stopCounting(TEST_CASE_GENERATION);
			
			// Set authentication data (if any)
			authenticateTestCase(test);

			// Add test case to the collection
			testCases.add(test);
		}

		return testCases;
	}

	// Generate the next test case and update the generation index
	@Override
	public TestCase generateNextTestCase(Operation testOperation, String faultyReason) {
		
		// Create an empty test case with a random id
		TestCase test = createTestCaseTemplate(testOperation, faultyReason);

		// Set parameters and values using the selected test data generators. This is where the actual test case is created.
		setTestCaseParameters(test, testOperation);

		// If more faulty test case need to be generated, try mutating the current test case to make it invalid
		if (!faultyReason.equals("none"))
			mutateTestCase(test, testOperation);
		
		updateIndexes(test);
		
		return test;
	}
	
	
	// Mutate the current test case to make it invalid (if possible)
	private void mutateTestCase(TestCase test, Operation testOperation) {
		
		// Try to mutate the test case to make it faulty (if it returns false it means that not mutation could be applied)
		if (!makeTestCaseFaulty(test, testOperation.getOpenApiOperation())) { 
			test.setFaulty(false); 
			test.setFaultyReason("none");
		}

		// The test case could still be invalid if the body has been mutated. Check with a validator
		if (!test.getFaulty() && checkFaulty(test, validator)) { 
			test.setFaulty(true);
			test.setFaultyReason("invalid_request_body");
		}
		
	}

	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		Boolean res = index<numberOfTests;
		if (index == numberOfTests)
			index = 0;
		return res;
	}
	
}
