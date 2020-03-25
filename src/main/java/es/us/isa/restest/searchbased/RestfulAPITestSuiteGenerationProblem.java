/**
 *
 */
package es.us.isa.restest.searchbased;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.AllureReportManager;
import es.us.isa.restest.util.CSVReportManager;
import static es.us.isa.restest.util.FileManager.createDir;
import es.us.isa.restest.util.PropertyManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.uma.jmetal.problem.impl.AbstractGenericProblem;

public class RestfulAPITestSuiteGenerationProblem extends AbstractGenericProblem<RestfulAPITestSuiteSolution> {

    // Elements under Tests:
    String OAISpecPath;
    OpenAPISpecification apiUnderTest;
    Operation operationUnderTest;

    // Test case creation configuration
    String testClassNamePrefix;
    String testsPackage;
    String targetPath;
    List<TestParameter> parameters;
    Map<String, ITestDataGenerator> generators;
    // Transient test case creation objects;
    CSVReportManager csvReportManager;
    IWriter iWriter;
    AllureReportManager allureReportManager;
    RESTestRunner runner;
    DummyTestCaseGenerator testCaseGenerator;

    // Optimization problem configuration
    List<RestfulAPITestingObjectiveFunction> objectiveFunctions;

    public RestfulAPITestSuiteGenerationProblem(String OAISpecPath, String operationPath, String method, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath) {

    }

    public RestfulAPITestSuiteGenerationProblem(OpenAPISpecification apiUnderTest, Operation operationUnderTest, List<RestfulAPITestingObjectiveFunction> objFuncs, String targetPath) {
        this.apiUnderTest = apiUnderTest;
        if(operationUnderTest!=null) {
        	this.operationUnderTest = operationUnderTest;
        	this.parameters = this.operationUnderTest.getTestParameters();
        }
        this.generators = createGenerators(this.parameters);
        this.targetPath = targetPath;

        this.iWriter = createWriter(targetPath);
        this.csvReportManager = createCSVReportManager();
        this.allureReportManager = createReportManager();
        this.testCaseGenerator = new DummyTestCaseGenerator();
        this.runner = new RESTestRunner(testClassNamePrefix, targetPath, testsPackage, testCaseGenerator, iWriter, allureReportManager, csvReportManager);

        assert (objFuncs != null);
        assert (objectiveFunctions.size() > 0);
        this.objectiveFunctions = objFuncs;

        setNumberOfObjectives(this.objectiveFunctions.size());
    }

    @Override
    public void evaluate(RestfulAPITestSuiteSolution s) {
        int i = 0;
        invokeMissingTests(s);
        for (RestfulAPITestingObjectiveFunction objFunc : objectiveFunctions) {
            s.setObjective(i, objFunc.evaluate(s));
            i++;
        }
    }

    @Override
    public RestfulAPITestSuiteSolution createSolution() {
        return new RestfulAPITestSuiteSolution(this);
    }

    public List<TestParameter> getParameters() {
        return parameters;
    }

    public Map<String, ITestDataGenerator> getGenerators() {
        return generators;
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
            if (s.getTestResult(testCase) == null) {
                missingTestCases.add(testCase);
            }
        }
        iWriter.write(missingTestCases);
        Map<TestCase, TestResult> results = execute(missingTestCases);
        s.addTestResults(results);
    }

    private Map<TestCase, TestResult> execute(List<TestCase> missingTestCases) {
        Map<TestCase, TestResult> result = new HashMap<>();
        TestResult testResult;
        for (TestCase testCase : missingTestCases) {
            testResult=execute(testCase);
            result.put(testCase, testResult);
        }

        return result;
    }

    private TestResult execute(TestCase testCase) {
        String testClassName = generateTestClassName(testCase);
        String filePath = targetPath + "/" + testClassName + ".java";
        String className = testsPackage + "." + testClassName;
        Class<?> testClass = es.us.isa.restest.util.ClassLoader.loadClass(filePath, className);
        JUnitCore junit = new JUnitCore();
        //junit.addListener(new TextListener(System.out));
        junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
        Result result = junit.run(testClass);

        int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
        //logger.info(result.getRunCount() + " tests run in " + result.getRunTime()/1000 + " seconds. Successful: " + successfulTests +" , Failures: " + result.getFailureCount() + ", Ignored: " + result.getIgnoreCount());
        TestResult testResult=null;
        return testResult;
    }

    private String generateTestClassName(TestCase testCase) {
        return "";
    }

    private IWriter createWriter(String targetDir) {
        String basePath = apiUnderTest.getSpecification().getSchemes().get(0).name() + "://" + apiUnderTest.getSpecification().getHost() + apiUnderTest.getSpecification().getBasePath();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDir, testClassNamePrefix, testsPackage, basePath.toLowerCase());
        writer.setLogging(true);
        writer.setAllureReport(true);
        writer.setEnableStats(true);
        writer.setAPIName(apiUnderTest.getSpecification().getInfo().getTitle());
        return writer;
    }

    private CSVReportManager createCSVReportManager() {
        String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/" + apiUnderTest.getSpecification().getInfo().getTitle();
        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/" + apiUnderTest.getSpecification().getInfo().getTitle();

        // Delete previous results (if any)
        deleteDir(testDataDir);
        deleteDir(coverageDataDir);

        // Recreate directories
        createDir(testDataDir);
        createDir(coverageDataDir);

        return new CSVReportManager(testDataDir, coverageDataDir);

//		CSVReportManager csvReportManager = new CSVReportManager();
//		csvReportManager.setEnableStats(false);
//		return csvReportManager;
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

    private AllureReportManager createReportManager() {
        String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/" + apiUnderTest.getSpecification().getInfo().getTitle();
        String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/" + apiUnderTest.getSpecification().getInfo().getTitle();

        // Delete previous results (if any)
        deleteDir(allureResultsDir);
        deleteDir(allureReportDir);

        AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);

        return arm;
    }
}
