/**
 *
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction.ObjectiveFunctionType;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.testcases.restassured.executors.RestAssuredExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.uma.jmetal.problem.impl.AbstractGenericProblem;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

public class RestfulAPITestSuiteGenerationProblem extends AbstractGenericProblem<RestfulAPITestSuiteSolution> {

    private static final Logger logger = LogManager.getLogger(RestfulAPITestSuiteGenerationProblem.class.getName());

	// TestSuiteSizeParameters :
	// We support 3 suite size configuration mechanisms:
	// 1.- A random value between maxTestSuiteSize and minTestSuiteSize if those values are set.
	// 2.- A fixed value specified by the fixedTestSuiteSize attribute if set.
	// 3.- A default fixed value computed using the information in the operation/api undertest (method computeDefaultTestSuiteSize)
	Integer maxTestSuiteSize;
	Integer minTestSuiteSize;
	Integer fixedTestSuiteSize;

    OpenAPISpecification apiUnderTest;
    TestConfigurationObject config; // Test case creation configuration
    PseudoRandomGenerator randomGenerator; // Random number generator
    RestAssuredExecutor testCaseExecutor; // Used to execute test cases when needed

    Map<String, ConstraintBasedTestCaseGenerator> testCaseGenerators; // key: operationId
    Map<String, Operation> operationsUnderTest; // key: operationId

    // Optimization problem configuration
    List<RestfulAPITestingObjectiveFunction> objectiveFunctions;
    boolean requiresTestExecution;
    boolean requiresTestOracles;
    long testCasesExecuted;

    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs) {
    	this(apiUnderTest,configuration,objFuncs,JMetalRandom.getInstance().getRandomGenerator(),null);    	
    }
    
    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, PseudoRandomGenerator randomGenerator, Integer minTestSuiteSize, Integer maxTestSuiteSize) {
    	this(apiUnderTest, configuration, objFuncs, randomGenerator,null);
    	if(maxTestSuiteSize!=null) {
    		this.setNumberOfVariables(maxTestSuiteSize);
    		this.fixedTestSuiteSize=null;    		
    	}    	    	
    	this.maxTestSuiteSize=maxTestSuiteSize;
    	this.minTestSuiteSize=minTestSuiteSize;
    	
    }
    
    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, PseudoRandomGenerator randomGenerator, Integer fixedTestSuiteSize) {
    	this.apiUnderTest = apiUnderTest;
    	testCaseExecutor = new RestAssuredExecutor(apiUnderTest);
    	testCaseExecutor.setLogging(false);
        this.setName(apiUnderTest.getSpecification().getInfo().getTitle());
        this.config = configuration;
        this.randomGenerator = randomGenerator;

        // Initialize operations and test case generators
        this.operationsUnderTest = new HashMap<>();
        this.testCaseGenerators = new HashMap<>();
        for (Operation op: this.config.getTestConfiguration().getOperations()) {
            operationsUnderTest.put(op.getOperationId(), op);
            ConstraintBasedTestCaseGenerator testCaseGenerator = new ConstraintBasedTestCaseGenerator(apiUnderTest, configuration, Integer.MAX_VALUE);
            testCaseGenerator.setUpIDLReasoner(op);
            testCaseGenerator.createGenerators(op.getTestParameters());
            testCaseGenerators.put(op.getOperationId(), testCaseGenerator);
        }

        assert (objFuncs != null);
        assert (objFuncs.size() > 0);
        this.objectiveFunctions = objFuncs;
        requiresTestExecution = false;
        requiresTestOracles = false;
        this.testCasesExecuted=0;

        for (RestfulAPITestingObjectiveFunction objFunc: objectiveFunctions) {
            if (objFunc.isRequiresTestExecution())
                requiresTestExecution = true;
            if (objFunc.isRequiresTestOracles())
                requiresTestOracles = true;
        }

        setNumberOfObjectives(this.objectiveFunctions.size());
        setNumberOfVariables(computeDefaultTestSuiteSize());
    }
    
    public RestfulAPITestSuiteGenerationProblem clone() {
    	return new RestfulAPITestSuiteGenerationProblem(apiUnderTest, config, objectiveFunctions, randomGenerator, minTestSuiteSize, maxTestSuiteSize);
    }
    
    

    private int computeDefaultTestSuiteSize() {
    	if(fixedTestSuiteSize!=null)
    		return fixedTestSuiteSize;
    	else if(maxTestSuiteSize!=null) {    		
    		return maxTestSuiteSize;
		}
    	// Default suite size is noOfOperations * 4
		return config.getTestConfiguration().getOperations().size() * 4;
	}

	@Override
    public void evaluate(RestfulAPITestSuiteSolution s) {
        logger.info("Evaluating solution...");
        if (requiresTestExecution) // Run tests only if some objective function requires it
            invokeMissingTests(s);

        int i = 0;
        for (RestfulAPITestingObjectiveFunction objFunc : objectiveFunctions) {
            s.setObjective(i, objFunc.getType().equals(
            		ObjectiveFunctionType.MINIMIZATION)
            					?
            				objFunc.evaluate(s) 	// If minimizing return as is
            					:
            				-objFunc.evaluate(s)); 	// Otherwise change sign
            i++;
        }
    }

    @Override
    public RestfulAPITestSuiteSolution createSolution() {
        return new RestfulAPITestSuiteSolution(this);
    }

    public OpenAPISpecification getApiUnderTest() {
        return apiUnderTest;
    }

    private void invokeMissingTests(RestfulAPITestSuiteSolution s) {
        List<TestCase> missingTestCases = new ArrayList<TestCase>();
        for (TestCase testCase : s.getVariables()) {
            if (s.getTestResult(testCase.getId()) == null) {
                missingTestCases.add(testCase);
            }
        }        
        Map<String, TestResult> results = execute(missingTestCases);
        s.addTestResults(results);
    }

    private Map<String, TestResult> execute(List<TestCase> missingTestCases) {
        Map<String, TestResult> results = new HashMap<>();
        for (TestCase testCase: missingTestCases) {
            results.put(testCase.getId(), testCaseExecutor.executeTest(testCase));
            testCasesExecuted++;
        }

        return results;
    }

	public TestCase createRandomTestCase() {
		Operation operation = chooseRandomOperation();
		String faulty = chooseRandomFaultyReason();
		ConstraintBasedTestCaseGenerator testCaseGenerator = testCaseGenerators.get(operation.getOperationId());
		testCaseGenerator.checkIDLReasonerData(operation, faulty);
		TestCase testCase = testCaseGenerator.generateNextTestCase(operation, faulty);
		testCaseGenerator.authenticateTestCase(testCase);
        if (!requiresTestOracles) // If no objective function requires oracles, disable them
            testCase.setEnableOracles(false);
		return testCase;
	}

	private Operation chooseRandomOperation() {
		List<Operation> operations=config.getTestConfiguration().getOperations();
		return operations.get(randomGenerator.nextInt(0, operations.size()-1));
	}

	private String chooseRandomFaultyReason() {
        double prob = randomGenerator.nextDouble();
        if (prob < 0.5)
            return "none";
        else if (prob < 0.75)
            return "individual_parameter_constraint";
        else
            return "inter_parameter_dependency";
    }

	public TestConfigurationObject getConfig() {
		return config;
	}

	public void setMaxTestSuiteSize(Integer maxTestSuiteSize) {
		this.maxTestSuiteSize = maxTestSuiteSize;
	}

	public void setMinTestSuiteSize(Integer minTestSuiteSize) {
		this.minTestSuiteSize = minTestSuiteSize;
	}

	public void setFixedTestSuiteSize(Integer fixedTestSuiteSize) {
		this.fixedTestSuiteSize = fixedTestSuiteSize;
	}

	public List<RestfulAPITestingObjectiveFunction> getObjectiveFunctions() {
		return objectiveFunctions;
	}

    public Map<String, ConstraintBasedTestCaseGenerator> getTestCaseGenerators() {
        return testCaseGenerators;
    }

    public Map<String, Operation> getOperationsUnderTest() {
        return operationsUnderTest;
    }

	public Integer getMaxTestSuiteSize() {
		return maxTestSuiteSize;
	}

	public Integer getMinTestSuiteSize() {
		return minTestSuiteSize;
	}

	public Integer getFixedTestSuiteSize() {
		return fixedTestSuiteSize;
	}

	public long getTestCasesExecuted() {
		return testCasesExecuted;		
	}
	
	
    
}