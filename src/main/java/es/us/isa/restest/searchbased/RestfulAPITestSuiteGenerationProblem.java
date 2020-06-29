/**
 *
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfiguration;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction.ObjectiveFunctionType;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.StatsReportManager;

import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;
import static es.us.isa.restest.util.TestManager.getLastTestResult;

import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.SpecificationVisitor;

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
    Operation operationUnderTest;

    // Test case creation configuration
    String testClassNamePrefix;
    String testsPackage;
    String targetPath;
//    List<TestParameter> parameters;
//    Map<String, ITestDataGenerator> generators;
    TestConfiguration config;
    
    // Random number generator
    PseudoRandomGenerator randomGenerator;
    
    // Transient test case creation objects;
    StatsReportManager statsReportManager;
    RESTAssuredWriter iWriter;
    RandomTestCaseGenerator randomTestCaseGenerator;
    
    // Optimization problem configuration
    List<RestfulAPITestingObjectiveFunction> objectiveFunctions;

    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, Operation operationUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath) {
    	this(apiUnderTest,operationUnderTest,configuration,objFuncs,targetPath,JMetalRandom.getInstance().getRandomGenerator(),null);
    }
    
    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, Operation operationUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath, PseudoRandomGenerator randomGenerator, Integer minTestSuiteSize, Integer maxTestSuiteSize) {
    	this(apiUnderTest, operationUnderTest, configuration, objFuncs, targetPath, randomGenerator,null);
    	if(maxTestSuiteSize!=null) {
    		this.setNumberOfVariables(maxTestSuiteSize);
    		this.fixedTestSuiteSize=null;    		
    	}    	    	
    	this.maxTestSuiteSize=maxTestSuiteSize;
    	this.minTestSuiteSize=minTestSuiteSize;
    }
    
    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, Operation operationUnderTest, TestConfigurationObject configuration, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath, PseudoRandomGenerator randomGenerator, Integer fixedTestSuiteSize) {
    	this.testsPackage="searchbased";
    	this.apiUnderTest = apiUnderTest;
        this.setName(apiUnderTest.getSpecification().getInfo().getTitle());
        this.config=configuration.getTestConfiguration();
        this.targetPath = targetPath;
        this.randomGenerator = randomGenerator;
        this.iWriter = createWriter(targetPath);
        this.statsReportManager = createStatsReportManager();
        this.randomTestCaseGenerator = new RandomTestCaseGenerator(apiUnderTest, configuration, Integer.MAX_VALUE);
        if(operationUnderTest!=null) {
            this.operationUnderTest = operationUnderTest;
            this.randomTestCaseGenerator.createGenerators(operationUnderTest.getTestParameters());
        }

        assert (objFuncs != null);
        assert (objFuncs.size() > 0);
        this.objectiveFunctions = objFuncs;

        setNumberOfObjectives(this.objectiveFunctions.size());
        setNumberOfVariables(computeDefaultTestSuiteSize());
    }

    private int computeDefaultTestSuiteSize() {
    	int result=1;
    	if(fixedTestSuiteSize!=null)
    		return fixedTestSuiteSize;
    	else if(maxTestSuiteSize!=null) {    		
    		return maxTestSuiteSize;
    	}else if(operationUnderTest==null) {
	    	// If we are testing the whole API we use the number of paths:			
    		result=apiUnderTest.getSpecification().getPaths().size();
    		// we use this value for the default fixed test suite size
    		fixedTestSuiteSize=result;
		}else {
			// If we are testing a specific operation we use the number of parameters plus one 
			// (just in case no parameters are present):
			result=operationUnderTest.getTestParameters().size()+1;
			// we use this value for the default fixed test suite size
			fixedTestSuiteSize=result;
		}
		return result;
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

    public Operation getOperationUnderTest() {
        return operationUnderTest;
    }

    public OpenAPISpecification getApiUnderTest() {
        return apiUnderTest;
    }

    private Map<String, ITestDataGenerator> createGenerators(List<TestParameter> testParameters) {
        HashMap<String, ITestDataGenerator> result = new HashMap<>();

        for (TestParameter param : testParameters) {
            result.put(param.getName(), TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator()));
        }

        return result;
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
		es.us.isa.restest.configuration.pojos.Operation operation=operationUnderTest;
		String faulty="none";
		if(operationUnderTest==null){
			operation=chooseRandomOperation();
            randomTestCaseGenerator.createGenerators(operation.getTestParameters());
		}
		TestCase testCase = randomTestCaseGenerator.generateNextTestCase(operation,faulty);
		if (!hasDependencies(operation.getOpenApiOperation()))
		    testCase.setFulfillsDependencies(true);
		return testCase;
	}	

	private es.us.isa.restest.configuration.pojos.Operation chooseRandomOperation() {
		List<es.us.isa.restest.configuration.pojos.Operation> operations=config.getOperations();
		es.us.isa.restest.configuration.pojos.Operation result=null;
		int index=randomGenerator.nextInt(0,operations.size()-1);
		result=operations.get(index);
		return result;
	}

	public String getTestsPackage() {
		return testsPackage;
	}
	
	public void setTestsPackage(String testsPackage) {
		this.testsPackage = testsPackage;
	}
	
	public TestConfiguration getConfig() {
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

    public RandomTestCaseGenerator getRandomTestCaseGenerator() {
        return randomTestCaseGenerator;
    }

    public void setRandomTestCaseGenerator(RandomTestCaseGenerator randomTestCaseGenerator) {
        this.randomTestCaseGenerator = randomTestCaseGenerator;
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