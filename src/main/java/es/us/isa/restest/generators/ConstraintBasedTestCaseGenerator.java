package es.us.isa.restest.generators;

import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.random.RandomBooleanGenerator;
import es.us.isa.restest.inputs.random.RandomInputValueIterator;
import es.us.isa.restest.specification.OpenAPISpecification;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.Timer;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import java.util.*;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;
import static es.us.isa.restest.testcases.TestCase.checkFaulty;
import static es.us.isa.restest.util.IDLAdapter.idl2restestTestCase;
import static es.us.isa.restest.util.SpecificationVisitor.*;
import static es.us.isa.restest.util.Timer.TestStep.TEST_CASE_GENERATION;

public class ConstraintBasedTestCaseGenerator extends AbstractTestCaseGenerator {

	public static final String INDIVIDUAL_PARAMETER_CONSTRAINT = "individual_parameter_constraint";
	public static final String INTER_PARAMETER_DEPENDENCY = "inter_parameter_dependency";
	private Float faultyDependencyRatio = 0.5f;			// Ratio of faulty test cases due to inter-parameter deps. Defaults to 0.5
	private Integer reloadInputDataEvery = 100;      // Number of requests using the same randomly generated input data
	private Integer inputDataMaxValues = 1000;       // Number of values used for each parameter when reloading input data
	private Analyzer idlReasoner;							// IDLReasoner to check if requests are valid or not

	public ConstraintBasedTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
	}

	@Override
	protected Collection<TestCase> generateOperationTestCases(Operation testOperation) {

		List<TestCase> testCases = new ArrayList<>();

		if (hasDependencies(testOperation.getOpenApiOperation())) // If the operation contains dependencies, create new IDLReasoner for that operation
			idlReasoner = new Analyzer("oas", spec.getPath(), testOperation.getTestPath(), testOperation.getMethod());
		else // Otherwise, set it to null so that it's not used
			idlReasoner = null;

		// Whether the next test case to generate must be faulty or not
		String faultyReason = "none";
		if (faultyRatio > 0) {
			if (faultyDependencyRatio > 0)
				faultyReason = INTER_PARAMETER_DEPENDENCY;
			else
				faultyReason = INDIVIDUAL_PARAMETER_CONSTRAINT;
		}

		while (hasNext()) {
			if (idlReasoner != null && index%reloadInputDataEvery == 0 && !faultyReason.equals(INDIVIDUAL_PARAMETER_CONSTRAINT)) {
				Map <String, List<String>> inputData = generateInputData(testOperation.getTestParameters()); // Update input data
				idlReasoner.updateData(inputData);
			}

			// Generate faulty test cases until faultyRatio is reached
			if (!faultyReason.equals("none")) {
				if ((float)index/(float)numberOfTest >= faultyRatio*faultyDependencyRatio)
					faultyReason = INDIVIDUAL_PARAMETER_CONSTRAINT;
				if ((float)index/(float)numberOfTest >= faultyRatio)
					faultyReason = "none";
			}
			Timer.startCounting(TEST_CASE_GENERATION);
			TestCase test = generateNextTestCase(testOperation, faultyReason);
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
	@Override
	protected TestCase generateNextTestCase(Operation testOperation, String faultyReason) {
		// This way, all test cases of an operation are not executed one after the other, but randomly:
		String testId = "test_" + IDGenerator.generateId() + "_" + removeNotAlfanumericCharacters(testOperation.getOperationId());
		TestCase test = new TestCase(testId, !faultyReason.equals("none"), testOperation.getOperationId(), testOperation.getTestPath(), HttpMethod.valueOf(testOperation.getMethod().toUpperCase()));
		test.setFaultyReason(faultyReason);

		switch (faultyReason) {
			case "none":
				if (idlReasoner != null)
					idl2restestTestCase(test, idlReasoner.getRandomValidRequest(), testOperation); // Generate valid test case with IDLReasoner
				else
					setTestCaseParameters(test, testOperation); // Generate valid test case normally (no need to manage deps.)
				test.setFulfillsDependencies(true);
				break;
			case INTER_PARAMETER_DEPENDENCY:
				if (idlReasoner != null)
					idl2restestTestCase(test, idlReasoner.getRandomInvalidRequest(), testOperation); // Generate invalid test case with IDLReasoner
				else {
					setTestCaseParameters(test, testOperation); // Impossible (no deps.), generate valid request
					test.setFaulty(false);
					test.setFaultyReason("none");
					test.setFulfillsDependencies(true);
				}
				break;
			case INDIVIDUAL_PARAMETER_CONSTRAINT:
				setTestCaseParameters(test, testOperation);
				if (!makeTestCaseFaulty(test, testOperation.getOpenApiOperation())) {
					test.setFaulty(false);
					test.setFaultyReason("none");
				}
				break;
			default:
				throw new IllegalArgumentException("The faulty reason '" + faultyReason + "' is not supported.");
		}

		if (!test.getFaulty() && checkFaulty(test, validator)) { // Before returning test case, if faulty==false, it may still be faulty (due to mutations of JSONmutator)
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
