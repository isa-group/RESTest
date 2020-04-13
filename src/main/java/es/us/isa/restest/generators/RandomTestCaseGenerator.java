package es.us.isa.restest.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.Timer;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;
import static es.us.isa.restest.testcases.TestCase.checkFaulty;
import static es.us.isa.restest.util.IDLAdapter.restest2idlTestCase;
import static es.us.isa.restest.util.SpecificationVisitor.*;
import static es.us.isa.restest.util.Timer.TestStep.TEST_CASE_GENERATION;

public class RandomTestCaseGenerator extends AbstractTestCaseGenerator {
	
	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
	}

	protected Collection<TestCase> generateOperationTestCases(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method) {

		List<TestCase> testCases = new ArrayList<TestCase>();

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
			TestCase test = generateNextTestCase(specOperation,testOperation,path,method,faultyReason);
			Timer.stopCounting(TEST_CASE_GENERATION);
			// Authentication
			authenticateTestCase(test);

//			// Set responses
//			test.setExpectedOutputs(specOperation.getResponses());
//
//			// Set expected output in case the request is successful
//			test.setExpectedSuccessfulOutput(specOperation.getResponses().get(testOperation.getExpectedResponse()));

			// Add test case to the collection
			testCases.add(test);
		}

		return testCases;
	}

	// Generate the next test case and update the generation index
	protected TestCase generateNextTestCase(Operation specOperation, es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method, String faultyReason) {

		// This way, all test cases of an operation are not executed one after the other, but randomly:
		String testId = "test_" + IDGenerator.generateId() + "_" + removeNotAlfanumericCharacters(testOperation.getOperationId());
		TestCase test = new TestCase(testId, !faultyReason.equals("none"), testOperation.getOperationId(), path, method);
		test.setFaultyReason(faultyReason);

		// Set parameters
		setTestCaseParameters(test, specOperation, testOperation);

		if (!faultyReason.equals("none") && !makeTestCaseFaulty(test, specOperation)) { // If this test case must be faulty
			test.setFaulty(false); // ... set faulty to false, in order to have the right oracle
			test.setFaultyReason("none");
		}

		if (!test.getFaulty()) // Before returning test case, if faulty==false, it may still be faulty (due to mutations of JSONmutator)
			if (checkFaulty(test, validator)) {
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
