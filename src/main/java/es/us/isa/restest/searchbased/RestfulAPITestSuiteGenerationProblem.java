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
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.StatsReportManager;

import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.TestManager.getLastTestResult;

import es.us.isa.restest.util.PropertyManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.uma.jmetal.problem.impl.AbstractGenericProblem;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

public class RestfulAPITestSuiteGenerationProblem extends AbstractGenericProblem<RestfulAPITestSuiteSolution> {

	// TestSuiteSizeParameters :
	// We support 3 suite size configuration mechanisms:
	// 1.- A random value between maxTestSuiteSize and minTestSuiteSize if those values are set.
	// 2.- A fixed value specified by the fixedTestSuiteSize attribute if set.
	// 3.- A default fixed value computed using the information in the operation/api undertest (method computeDefaultTestSuiteSize)
	Integer maxTestSuiteSize;
	Integer minTestSuiteSize;
	Integer fixedTestSuiteSize;

    // Elements under Tests:
    String OAISpecPath;
    OpenAPISpecification apiUnderTest;
//    Operation operationUnderTest;

    // Test case creation configuration
    String testClassNamePrefix;
    String testsPackage;
    String targetPath;
//    List<TestParameter> parameters;
//    Map<String, ITestDataGenerator> generators;
    TestConfigurationObject config;
    
    // Random number generator
    PseudoRandomGenerator randomGenerator;
    
    // Transient test case creation objects;
    StatsReportManager statsReportManager;
    RESTAssuredWriter iWriter;

    Map<String, ConstraintBasedTestCaseGenerator> testCaseGenerators; // key: operationId
    Map<String, Operation> operationsUnderTest; // key: operationId

    // Optimization problem configuration
    List<RestfulAPITestingObjectiveFunction> objectiveFunctions;

    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath) {
    	this(apiUnderTest,configuration,objFuncs,targetPath,JMetalRandom.getInstance().getRandomGenerator(),null);
    }
    
    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath, PseudoRandomGenerator randomGenerator, Integer minTestSuiteSize, Integer maxTestSuiteSize) {
    	this(apiUnderTest, configuration, objFuncs, targetPath, randomGenerator,null);
    	if(maxTestSuiteSize!=null) {
    		this.setNumberOfVariables(maxTestSuiteSize);
    		this.fixedTestSuiteSize=null;    		
    	}    	    	
    	this.maxTestSuiteSize=maxTestSuiteSize;
    	this.minTestSuiteSize=minTestSuiteSize;
    }
    
    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath, PseudoRandomGenerator randomGenerator, Integer fixedTestSuiteSize) {
    	this.testsPackage="searchbased";
    	this.apiUnderTest = apiUnderTest;
        this.setName(apiUnderTest.getSpecification().getInfo().getTitle());
        this.config = configuration;
        this.targetPath = targetPath;
        this.randomGenerator = randomGenerator;
        this.iWriter = createWriter(targetPath);
        this.statsReportManager = createStatsReportManager();

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

        setNumberOfObjectives(this.objectiveFunctions.size());
        setNumberOfVariables(computeDefaultTestSuiteSize());
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
        int i = 0;
        for (RestfulAPITestingObjectiveFunction objFunc: objectiveFunctions) {
            if (objFunc.isRequiresTestExecution()) {
                invokeMissingTests(s); // Run tests only if some objective function requires it
                break;
            }
        }
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
        Map<String, TestResult> result = new HashMap<>();
        TestResult testResult;
        List<TestCase> cases=new ArrayList<>(1);
        for (TestCase testCase : missingTestCases) {
        	cases.add(testCase);
        	iWriter.setClassName(testCase.getId());
        	iWriter.write(cases);
        	testResult=execute(testCase);
            result.put(testCase.getId(), testResult);
            cases.clear();
        }

        return result;
    }

    private TestResult execute(TestCase testCase) {
        String testClassName = testCase.getId();
        String filePath = targetPath + "/" + testClassName + ".java";
        String className = "";
        if(testsPackage!=null && !"".equals(testsPackage))
        	className=testsPackage + "." + testClassName;
        else
        	className = testClassName;
        Class<?> testClass = es.us.isa.restest.util.ClassLoader.loadClass(filePath, className);
        JUnitCore junit = new JUnitCore();
        //junit.addListener(new TextListener(System.out));
        //junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
        Result result = junit.run(testClass);

        int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
        //logger.info(result.getRunCount() + " tests run in " + result.getRunTime()/1000 + " seconds. Successful: " + successfulTests +" , Failures: " + result.getFailureCount() + ", Ignored: " + result.getIgnoreCount());

        return getLastTestResult(statsReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testresults.file"));
    }

    private RESTAssuredWriter createWriter(String targetDir) {
        String basePath = apiUnderTest.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDir, testClassNamePrefix, testsPackage, basePath.toLowerCase());
        writer.setLogging(true);
        writer.setAllureReport(false);
        writer.setEnableStats(true);
        writer.setSpecPath(apiUnderTest.getPath());
        writer.setAPIName(targetPath.substring(targetPath.lastIndexOf("/")+1));
        return writer;
    }

    private StatsReportManager createStatsReportManager() {
        String testDataDir = PropertyManager.readProperty("data.tests.dir") + targetPath.substring(targetPath.lastIndexOf("/"));
        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + targetPath.substring(targetPath.lastIndexOf("/"));

        // Delete previous results (if any)
        deleteDir(testDataDir);
        deleteDir(coverageDataDir);

        // Recreate directories
        createDir(testDataDir);
        createDir(coverageDataDir);

        return new StatsReportManager(testDataDir, coverageDataDir);
    }

    private void deleteDir(String dirPath) {
        File dir = new File(dirPath);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            System.err.println("Error deleting target dir");
            e.printStackTrace();
        }
    }

    // Create target dir if it does not exist
    private void createTargetDir() {
        File dir = new File(targetPath + "/");
        dir.mkdirs();
    }

	public TestCase createRandomTestCase() {
		Operation operation = chooseRandomOperation();
		String faulty = chooseRandomFaultyReason();
		ConstraintBasedTestCaseGenerator testCaseGenerator = testCaseGenerators.get(operation.getOperationId());
		testCaseGenerator.checkIDLReasonerData(operation, faulty);
		TestCase testCase = testCaseGenerator.generateNextTestCase(operation, faulty);
		testCaseGenerator.authenticateTestCase(testCase);
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

	public String getTestsPackage() {
		return testsPackage;
	}
	
	public void setTestsPackage(String testsPackage) {
		this.testsPackage = testsPackage;
	}
	
	public TestConfigurationObject getConfig() {
		return config;
	}

	/*public ITestDataGenerator getGeneratorsFor(Operation op,Parameter specParameter) {
		ITestDataGenerator result=generators.get(specParameter.getName());
		if(result==null) {
			result = TestDataGeneratorFactory.createTestDataGenerator(specParameter.getGenerator();
			generators.put(specParameter.getName(), result);
		}
		return null;
	}*/

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
	
	
    
}