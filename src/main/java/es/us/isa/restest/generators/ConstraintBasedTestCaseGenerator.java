package es.us.isa.restest.generators;

import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.random.RandomBooleanGenerator;
import es.us.isa.restest.inputs.random.RandomInputValueIterator;
import es.us.isa.restest.mutation.TestCaseMutation;
import es.us.isa.restest.specification.OpenAPISpecification;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.Timer;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.javatuples.Pair;

import java.util.*;

import static es.us.isa.restest.testcases.TestCase.checkFaulty;
import static es.us.isa.restest.util.IDLAdapter.idl2restestTestCase;
import static es.us.isa.restest.util.SpecificationVisitor.*;
import static es.us.isa.restest.util.Timer.TestStep.TEST_CASE_GENERATION;

/**
 * This class implements a constraint-based test case generator using IDLReasoner, a CSP-based tool for the automated analysis of inter-parameter dependencies
 * @author Alberto Martin-Lopez
 *
 */
public class ConstraintBasedTestCaseGenerator extends AbstractTestCaseGenerator {

	public static final String INDIVIDUAL_PARAMETER_CONSTRAINT = "individual_parameter_constraint";
	public static final String INTER_PARAMETER_DEPENDENCY = "inter_parameter_dependency";
	public static final String INVALID_REQUEST_BODY = "inter_parameter_dependency";
	private Float faultyDependencyRatio = 0.5f;												// Ratio of faulty test cases due to inter-parameter deps. Defaults to 0.5
	private Integer reloadInputDataEvery = 100;      										// Number of requests using the same randomly generated input data
	private Integer inputDataMaxValues = 1000;       										// Number of values used for each parameter when reloading input data
	private Analyzer idlReasoner;															// IDLReasoner to check if requests are valid or not
	
	// Indexes
	private int maxFaultyTestDueToDependencyViolations;											// Maximum number of faulty test cases due to dependency violations to be generated
	private int maxFaultyTestsDueToIndividualConstraints;										// Maximum number of faulty test cases due to individual constraints to be generated
	private int nFaultyTestDueToDependencyViolations;											// Current number of faulty test cases due to dependency violations to be generated
	private int nFaultyTestsDueToIndividualConstraint;											// Current number of faulty test cases due to individual constraints to be generated
	private int nFaultyTestsDuetoInvalidRequestBody;											// Current number of faulty test cases due to invalid request body
	
	public ConstraintBasedTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		super(spec, conf, nTests);
		
	}

	/**
	 * Set IDLReasoner for the generation of dependency-aware valid/invalid test cases
	 * @param testOperation API operation under test
	 */
	public void setUpIDLReasoner(Operation testOperation) {
		if (hasDependencies(testOperation.getOpenApiOperation())) // If the operation contains dependencies, create new IDLReasoner for that operation
			idlReasoner = new Analyzer("oas", spec.getPath(), testOperation.getTestPath(), testOperation.getMethod());
		else // Otherwise, set it to null so that it's not used
			idlReasoner = null;
	}

	/**
	 * Refresh the test data used for the generation of test cases. Test data must be fed to IDLReasoner, which in turn uses it for the generation of test cases using a CSP solver.
	 * @param testOperation API operation under test
	 */
	public void checkIDLReasonerData(Operation testOperation) {
		if (idlReasoner != null && nTests%reloadInputDataEvery == 0) {
			Map <String, List<String>> inputData = generateInputData(testOperation.getTestParameters()); // Update input data
			idlReasoner.updateData(inputData);
		}
	}

	/*
	 * Generate the collection of test cases 
	 */
	protected Collection<TestCase> generateOperationTestCases(Operation testOperation) {

		setUpIDLReasoner(testOperation);

		List<TestCase> testCases = new ArrayList<>();
		
		// Calculate number and type of faulty tests to be generated
		if (idlReasoner==null) // The operation has no inter-parameter dependencies
			maxFaultyTestDueToDependencyViolations = 0;
		else
			maxFaultyTestDueToDependencyViolations = (int) ((numberOfTests * faultyRatio * faultyDependencyRatio));
		
		maxFaultyTestsDueToIndividualConstraints = (int) (((numberOfTests * faultyRatio) - nFaultyTestDueToDependencyViolations));
		
		nFaultyTestDueToDependencyViolations = 0;
		nFaultyTestsDueToIndividualConstraint = 0;
		nFaultyTestsDuetoInvalidRequestBody = 0;
		

		while (hasNext()) {
			checkIDLReasonerData(testOperation);

			Timer.startCounting(TEST_CASE_GENERATION);
			TestCase test = generateNextTestCase(testOperation);
			Timer.stopCounting(TEST_CASE_GENERATION);
			
			// Set authentication data
			authenticateTestCase(test);
			
			testCases.add(test);
			
			// Update indexes
			updateIndexes(test);
			
		}

		return testCases;
	}



	// Generate the next test case and update the generation index
	public TestCase generateNextTestCase(Operation testOperation) {
		
		TestCase test = null;
		
		if (nFaultyTestsDueToIndividualConstraint < maxFaultyTestsDueToIndividualConstraints)		// Try generating a faulty test case violating an individual constraint
			test = generateNextTestCase(testOperation, INDIVIDUAL_PARAMETER_CONSTRAINT);
		
		else if (nFaultyTestDueToDependencyViolations < maxFaultyTestDueToDependencyViolations)		// Try generating a faulty test case violating one or more inter-parameter dependency
			test = generateNextTestCase(testOperation, INTER_PARAMETER_DEPENDENCY);
			
		// If a faulty test case has not been created. Generate a valid test case.
		if (test==null)
			test = generateNextTestCase(testOperation, "none");
		
		
		return test;
	}
	

	

	/**
	 * Returns a valid or invalid test cases based on the faultyReason provided. 
	 * @param testOperation API operation under test
	 * @param faultyReason Faulty reason. Possible values: "none", "individual_parameter_constraint", and "inter_parameter_dependency"
	 * @return
	 */
	public TestCase generateNextTestCase(Operation testOperation, String faultyReason) {
		
		TestCase test;

		switch (faultyReason) {
			case "none":
				test = generateValidTestCase(testOperation);
				break;
				
			case INTER_PARAMETER_DEPENDENCY:
				test = generateFaultyTestCaseDueToViolatedDependencies(testOperation);
				break;
				
			case INDIVIDUAL_PARAMETER_CONSTRAINT:
				test = generateFaultyTestCaseDueToIndividualConstraints(testOperation);
				break;
			default:
				throw new IllegalArgumentException("The faulty reason '" + faultyReason + "' is not supported.");
		}

		
		// Watch out! The test case could still be faulty if the body has been perturbated (for creating new test data from existing inputs)
		if (!test.getFaulty() && checkFaulty(test, validator)) { 
			test.setFaulty(true);
			test.setFaultyReason(INVALID_REQUEST_BODY);
			nFaultyTestsDuetoInvalidRequestBody++;
		}
		
		return test;
	}
	
	

	/* Returns a valid test case satisfying all the individual constraints and inter-parameter dependencies described in the API specification */ 
	private TestCase generateValidTestCase(Operation testOperation) {
		TestCase test = createTestCaseTemplate(testOperation);
		
		if (idlReasoner != null)		// The operation has inter-parameter dependencies
			idl2restestTestCase(test, idlReasoner.getRandomValidRequest(), testOperation); // Generate valid test case with IDLReasoner
		else 							// The operation has no inter-parameter dependences: generate a random test case
			setTestCaseParameters(test, testOperation); // Generate valid test case normally (no need to manage deps.)

		test.setFaulty(false);
		test.setFaultyReason("none");
		test.setFulfillsDependencies(true);
		
		return test;
	}
	
	
	/* Returns a faulty test case violating an individual constraint (ex. excluding a required parameter) */
	private TestCase generateFaultyTestCaseDueToIndividualConstraints(Operation testOperation) {
		
		TestCase test = createTestCaseTemplate(testOperation);
		
		setTestCaseParameters(test, testOperation);
		
		String mutationDescription = TestCaseMutation.mutate(test, testOperation.getOpenApiOperation());
		if (mutationDescription!="") {		// A mutation has been applied
			test.setFaulty(true);
			test.setFaultyReason(INDIVIDUAL_PARAMETER_CONSTRAINT + ":" + mutationDescription);
			nFaultyTestsDueToIndividualConstraint++;
		}	
	
		return test;
	}
	
	
	/* Returns a faulty test case violating one ore more inter-parameter dependency constraints */
	private TestCase generateFaultyTestCaseDueToViolatedDependencies(Operation testOperation) {
		
		TestCase test = createTestCaseTemplate(testOperation);
		
		if (idlReasoner != null) {		// The operation has inter-parameter dependencies
			idl2restestTestCase(test, idlReasoner.getRandomInvalidRequest(), testOperation); // Generate invalid test case with IDLReasoner
			test.setFaulty(true);
			test.setFaultyReason(INTER_PARAMETER_DEPENDENCY);
			nFaultyTestDueToDependencyViolations++;
		} else {						// The operation has no inter-parameter dependencies
			setTestCaseParameters(test, testOperation); // Impossible (no deps.), generate valid request
			test.setFaulty(false);
			test.setFaultyReason("none");
			test.setFulfillsDependencies(true);
		}
		
		return test;
	}
	
	
	
	private Map <String, List<String>> generateInputData(List<TestParameter> testParameters) {
		Map <String, List<String>> inputData = new HashMap<>();
		List<String> paramValues;
		ITestDataGenerator generator;
		for (TestParameter parameter: testParameters) {
			if (parameter.getWeight() == null || parameter.getWeight() > 0) {
				paramValues = new ArrayList<>();
				generator = generators.get(Pair.with(parameter.getName(), parameter.getIn()));
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
	
	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		return nTests < numberOfTests;
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

	public Analyzer getIdlReasoner() {
		return idlReasoner;
	}

	public void setIdlReasoner(Analyzer idlReasoner) {
		this.idlReasoner = idlReasoner;
	}
}
