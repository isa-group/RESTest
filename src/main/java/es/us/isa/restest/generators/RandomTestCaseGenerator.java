package es.us.isa.restest.generators;

import static es.us.isa.restest.testcases.TestCase.checkFaulty;
import static es.us.isa.restest.util.Timer.TestStep.TEST_CASE_GENERATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.mutation.TestCaseMutation;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.Timer;

/**
 *  This class implements a simple random test case generator
 * @author Sergio Segura
 *
 */
public class RandomTestCaseGenerator extends AbstractTestCaseGenerator {
	
	public static final String INDIVIDUAL_PARAMETER_CONSTRAINT = "individual_parameter_constraint";
	public static final String INVALID_REQUEST_BODY = "inter_parameter_dependency";
	
	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
	}

	@Override
	protected Collection<TestCase> generateOperationTestCases(Operation testOperation) {

		List<TestCase> testCases = new ArrayList<>();

		while (hasNext()) {

			// Create test case with specific parameters and values
			Timer.startCounting(TEST_CASE_GENERATION);
			TestCase test = generateNextTestCase(testOperation);
			Timer.stopCounting(TEST_CASE_GENERATION);
			
			// Set authentication data (if any)
			authenticateTestCase(test);

			// Add test case to the collection
			testCases.add(test);
			
			// Update indexes
			updateIndexes(test);
		}

		return testCases;
	}

	// Generate the next test case and update the generation index
	public TestCase generateNextTestCase(Operation testOperation) {
		
		// Create an empty test case with a random id
		TestCase test = createTestCaseTemplate(testOperation);

		// Set parameters and values using the selected test data generators. This is where the actual test case is created.
		setTestCaseParameters(test, testOperation);
		
		// If more faulty test cases need to be generated, try mutating the current test case to make it invalid
		if (nFaulty < (faultyRatio * numberOfTests))
			mutateTestCase(test, testOperation);

		return test;
	}
	

	/* Mutate the test case trying to make it invalid */
	private void mutateTestCase(TestCase test, Operation testOperation) {
		
		String mutationDescription = TestCaseMutation.mutate(test, testOperation.getOpenApiOperation());
		if (mutationDescription!="") {
			test.setFaulty(true);
			test.setFaultyReason(INDIVIDUAL_PARAMETER_CONSTRAINT + ":" + mutationDescription);
		}
		
		// Watch out! The test case could still be faulty if the body has been perturbated (for creating new test data from existing inputs)
		if (!test.getFaulty() && checkFaulty(test, validator)) { 
			test.setFaulty(true);
			test.setFaultyReason(INVALID_REQUEST_BODY);
		}
		
	}

	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		return nTests < numberOfTests;
	}
	
}
