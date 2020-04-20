package es.us.isa.restest.generators;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.random.RandomBooleanGenerator;
import es.us.isa.restest.inputs.random.RandomInputValueIterator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.Timer;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import java.util.*;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;
import static es.us.isa.restest.testcases.TestCase.checkFaulty;
import static es.us.isa.restest.util.IDLAdapter.idl2restestTestCase;
import static es.us.isa.restest.util.IDLAdapter.restest2idlTestCase;
import static es.us.isa.restest.util.SpecificationVisitor.*;
import static es.us.isa.restest.util.Timer.TestStep.TEST_CASE_GENERATION;
import static es.us.isa.restest.util.Timer.TestStep.TEST_SUITE_GENERATION;

public class ConstraintBasedTestCaseGenerator extends AbstractTestCaseGenerator {

	private Float faultyDependencyRatio = 0.5f;			// Ratio of faulty test cases due to inter-parameter deps. Defaults to 0.5
	private Integer reloadInputDataEvery = 100;      // Number of requests using the same randomly generated input data
	private Integer inputDataMaxValues = 1000;       // Number of values used for each parameter when reloading input data
	private Analyzer idlReasoner;							// IDLReasoner to check if requests are valid or not

	public ConstraintBasedTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
	}


	protected Collection<TestCase> generateOperationTestCases(Operation specOperation,
			es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method) {

		List<TestCase> testCases = new ArrayList<TestCase>();

		if (hasDependencies(specOperation)) // If the operation contains dependencies, create new IDLReasoner for that operation
			idlReasoner = new Analyzer("oas", spec.getPath(), path, method.toString());
		else // Otherwise, set it to null so that it's not used
			idlReasoner = null;

		// Whether the next test case to generate must be faulty or not
		String faultyReason = "none";
		if (faultyRatio > 0) {
			if (faultyDependencyRatio > 0)
				faultyReason = "inter_parameter_dependency";
			else
				faultyReason = "individual_parameter_constraint";
		}

		while (hasNext()) {
			if (index%reloadInputDataEvery == 0 && !faultyReason.equals("individual_parameter_constraint")) {
				Map <String, List<String>> inputData = generateInputData(testOperation.getTestParameters()); // Update input data
				idlReasoner.updateData(inputData);
			}

			// Generate faulty test cases until faultyRatio is reached
			if (!faultyReason.equals("none")) {
				if ((float)index/(float)numberOfTest >= faultyRatio*faultyDependencyRatio)
					faultyReason = "individual_parameter_constraint";
				if ((float)index/(float)numberOfTest >= faultyRatio)
					faultyReason = "none";
			}
			Timer.startCounting(TEST_CASE_GENERATION);
			TestCase test = generateNextTestCase(specOperation,testOperation,path,method,faultyReason);
			Timer.stopCounting(TEST_CASE_GENERATION);
			authenticateTestCase(test);
			testCases.add(test);
		}

		return testCases;
	}

	private Map <String, List<String>> generateInputData(List<TestParameter> testParameters) {
		Map <String, List<String>> inputData = new HashMap<>();
		List<String> paramValues;
		ITestDataGenerator generator;
		for (TestParameter parameter: testParameters) {
			if (parameter.getWeight() == null || parameter.getWeight() > 0) {
				paramValues = new ArrayList<>();
				generator = generators.get(parameter.getName());
				if (generator instanceof RandomInputValueIterator && ((RandomInputValueIterator) generator).getMaxValues() == 1) {
					paramValues = ((RandomInputValueIterator) generator).getValues();
				} else if (generator instanceof RandomBooleanGenerator) {
					paramValues = Arrays.asList("0", "1");
				} else {
					while (paramValues.size() < inputDataMaxValues) {
						paramValues.add(generator.nextValueAsString());
					}
				}
				inputData.put(parameter.getName(), paramValues);
			}
		}

		return inputData;
	}

	// Generate the next test case and update the generation index
	protected TestCase generateNextTestCase(Operation specOperation, es.us.isa.restest.configuration.pojos.Operation testOperation, String path, HttpMethod method, String faultyReason) {
		// This way, all test cases of an operation are not executed one after the other, but randomly:
		String testId = "test_" + IDGenerator.generateId() + "_" + removeNotAlfanumericCharacters(testOperation.getOperationId());
		TestCase test = new TestCase(testId, !faultyReason.equals("none"), testOperation.getOperationId(), path, method);
		test.setFaultyReason(faultyReason);

		switch (faultyReason) {
			case "none":
				if (idlReasoner != null)
					idl2restestTestCase(test, idlReasoner.getRandomValidRequest(), specOperation); // Generate valid test case with IDLReasoner
				else
					setTestCaseParameters(test, specOperation, testOperation); // Generate valid test case normally (no need to manage deps.)
				test.setFulfillsDependencies(true);
				break;
			case "inter_parameter_dependency":
				if (idlReasoner != null)
					idl2restestTestCase(test, idlReasoner.getRandomInvalidRequest(), specOperation); // Generate invalid test case with IDLReasoner
				else {
					setTestCaseParameters(test, specOperation, testOperation); // Impossible (no deps.), generate valid request
					test.setFaulty(false);
					test.setFaultyReason("none");
					test.setFulfillsDependencies(true);
				}
				break;
			case "individual_parameter_constraint":
				setTestCaseParameters(test, specOperation, testOperation);
				if (!makeTestCaseFaulty(test, specOperation)) {
					test.setFaulty(false);
					test.setFaultyReason("none");
				}
				break;
			default:
				throw new IllegalArgumentException("The faulty reason '" + faultyReason + "' is not supported.");
		}

		if (!test.getFaulty()) // Before returning test case, if faulty==false, it may still be faulty (due to mutations of JSONmutator)
			if (checkFaulty(test, validator)) {
				test.setFaulty(true);
				test.setFaultyReason("invalid_request_body");
			}

		// Update indexes
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

	public Float getFaultyDependencyRatio() {
		return faultyDependencyRatio;
	}

	public void setFaultyDependencyRatio(Float faultyDependencyRatio) {
		this.faultyDependencyRatio = faultyDependencyRatio;
	}

	public Integer getReloadInputDataEvery() {
		return reloadInputDataEvery;
	}

	public void setReloadInputDataEvery(Integer reloadInputDataEvery) {
		this.reloadInputDataEvery = reloadInputDataEvery;
	}

	public Integer getInputDataMaxValues() {
		return inputDataMaxValues;
	}

	public void setInputDataMaxValues(Integer inputDataMaxValues) {
		this.inputDataMaxValues = inputDataMaxValues;
	}
}
