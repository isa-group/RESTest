package es.us.isa.restest.reporting;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.coverage.CoverageResults;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.util.TestManager;

import it.units.inginf.male.outputs.FinalSolution;
import javafx.util.Pair;
import org.apache.jena.tdb.store.Hash;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.regexGenerator.ConsoleRegexTurtle.learnRegex;
//import static es.us.isa.restest.main.TestGenerationAndExecution.getTestConfigurationObject;
import static es.us.isa.restest.main.TestGenerationAndExecution.getTestConfigurationObject;
import static es.us.isa.restest.util.CSVManager.readCSV;

/**
 *
 * This class generates the statistics related to the generation and execution of test cases. Currently, it generates test cases in CVS format and coverage data
 *
 */
public class StatsReportManager {

    private String testDataDir;
    private String coverageDataDir;
    private boolean enableCSVStats = true;
    private boolean enableInputCoverage = true;
    private boolean enableOutputCoverage = true;
    private CoverageMeter coverageMeter;
    Collection<TestCase> testCases = null;


    private static final Logger logger = LogManager.getLogger(StatsReportManager.class.getName());

    public StatsReportManager() {
        this(PropertyManager.readProperty("data.tests.dir"), PropertyManager.readProperty("data.coverage.tests.dir"));
    }

    public StatsReportManager(String testDataDir, String coverageDataDir, boolean enableCSVStats, boolean enableInputCoverage, boolean enableOutputCoverage, CoverageMeter coverageMeter) {
        this.testDataDir = testDataDir;
        this.coverageDataDir = coverageDataDir;
        this.enableCSVStats = enableCSVStats;
        this.enableInputCoverage = enableInputCoverage;
        this.enableOutputCoverage = enableOutputCoverage;
        this.coverageMeter = coverageMeter;
    }

    public StatsReportManager(String testDataDir, String coverageDataDir) {
        this.testDataDir = testDataDir;
        this.coverageDataDir = coverageDataDir;
    }

    // Generate statistics
    public void generateReport(String testId) {

        // Generate CVS stats
        if (enableCSVStats)
            generateCSVStats(testId);

        // Generate coverage stats
        if (enableInputCoverage || enableOutputCoverage)
            generateCoverageStats(testId);

    }

    public void learn(String testId) {

        // Store the values of the parameters of successful and unsuccessful operations
        Map<Pair<String, TestParameter>, Set<String>> successfulValues = new HashMap<>();
        Map<Pair<String, TestParameter>, Set<String>> failedValues = new HashMap<>();

        TestConfigurationObject testConf = getTestConfigurationObject();
        List<Operation> operations = testConf.getTestConfiguration().getOperations();

        for(Operation operation: operations){
            // Adding parameters that use a csv to the maps
            for(TestParameter testParameter: operation.getTestParameters()){
                Generator generator = testParameter.getGenerator();
                if(generator.getType().equals(RANDOM_INPUT_VALUE)){
                    for(GenParameter genParameter: generator.getGenParameters()){

                        if(genParameter.getName().equals("csv")){
                            // Adding the pair <OperationId, parameterName> to the maps
                            Pair<String, TestParameter> operationAndParameter = new Pair(operation.getOperationId(), testParameter);
                            successfulValues.put(operationAndParameter, new HashSet<>());
                            failedValues.put(operationAndParameter, new HashSet<>());
                        }
                    }
                }
            }
        }

        // TODO: 2XX or 4XX
        // TODO: 2XX list (map) may be empty
        // TODO: Add support to multiple operations (pairs as keys)
        // TODO: BY OPERATION ID or method and path?
        // TODO: Multiple CSVs?

        String csvTrPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
        List<TestResult> trs = TestManager.getTestResults(csvTrPath);


        for(TestCase testCase: testCases){
            String operationId = testCase.getOperationId();

            // TODO: Check faulty and ¿fulfills Dependencies?

            String responseCode = trs.stream().filter(tr -> tr.getId().equals(testCase.getId()))
                    .findFirst()
                    .orElseThrow(() -> new NullPointerException("Associated test result not found")).getStatusCode();


            // Iterate semantic parameters
            // Filter by operationId
            Set<TestParameter> parametersOfOperation = successfulValues.keySet().stream()
                    .filter(x->x.getKey().equals(operationId)).map(x->x.getValue())
                    .collect(Collectors.toSet());

            for(TestParameter parameter: parametersOfOperation){
                Pair<String, TestParameter> pair = new Pair<>(operationId, parameter);

                // Search parameter value in corresponding map
                String value = "";
                switch (parameter.getIn()){
                    case "header":
                        value = testCase.getHeaderParameters().get(parameter.getName());
                        break;
                    case "path":
                        value = testCase.getPathParameters().get(parameter.getName());
                        break;
                    case "form":
                        value = testCase.getFormParameters().get(parameter.getName());
                        break;
                    default:        // query
                        value = testCase.getQueryParameters().get(parameter.getName());
                        break;
                }


                // Add parameter value to a map depending on the response code
                switch (responseCode.charAt(0)){
                    case '2':
                        successfulValues.get(pair).add(value);
                        break;
                    case '4':
                        failedValues.get(pair).add(value);
                        break;
                }

            }

//            System.out.println("Id: " + testCase.getId());
//            testCase.getOperationId();
//            System.out.println("Header: " + testCase.getHeaderParameters());
//            System.out.println("Path: " + testCase.getPathParameters());
//            System.out.println("Query: " + testCase.getQueryParameters().get("market"));
//            System.out.println("Form: " + testCase.getFormParameters());
//            System.out.println("Response Code: " + responseCode);

        }

        System.out.println("---------------------------------------------------------------------------");
        System.out.println("---------------------------------------------------------------------------");
        System.out.println(successfulValues);
        System.out.println(failedValues);
        System.out.println("---------------------------------------------------------------------------");
        System.out.println("---------------------------------------------------------------------------");

        // TODO: Check size of both lists
        // TODO: Learn
        // TODO: Update csv
        // TODO: Check F1, recall and precision

        // Learn
        for(Pair<String, TestParameter> key: successfulValues.keySet()){
            String name = key.getKey() + "_" + key.getValue();          // OperationName_parameterId
            Set<String> successfulSet = successfulValues.get(key);
            Set<String> failedSet = failedValues.get(key);

            if(failedSet.size() > 5 && successfulSet.size() > 5){

                FinalSolution solution = learnRegex(name, successfulSet, failedSet,false);

                System.out.println("Regex aprendida = " + solution.getSolution());


            }




        }


    }

    // Generate CVS statistics (test cases to CSV)
    private void generateCSVStats(String testId) {
        logger.info("Exporting test cases coverage to CSV");
        String csvTcPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testcases.file") + "_" + testId + ".csv";
        testCases.forEach(tc -> tc.exportToCSV(csvTcPath));
    }

    // Generate coverage statistics
    private void generateCoverageStats(String testId) {

        // Add test cases
        getCoverageMeter().addTestSuite(testCases);

        if (enableOutputCoverage) {
            // Update CoverageMeter with the test results
            String csvTrPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
            List<TestResult> trs = TestManager.getTestResults(csvTrPath);
            coverageMeter.addTestResults(trs);
        }

        if (enableInputCoverage || enableOutputCoverage) {
            // Generate coverage report (input coverage a priori)
            exportCoverageReport(coverageMeter, coverageDataDir + "/" + PropertyManager.readProperty("data.coverage.computation.priori.file") + "_" + testId + ".csv");
            logger.info("Coverage report a priori generated.");

            // Generate coverage report (input coverage a posteriori)
            exportCoverageReport(coverageMeter.getAPosteriorCoverageMeter(), coverageDataDir + "/" + PropertyManager.readProperty("data.coverage.computation.posteriori.file") + "_" + testId + ".csv");
            logger.info("Coverage report a posteriori generated.");

        }
    }

    private void exportCoverageReport(CoverageMeter coverageMeter, String path) {
        CoverageResults results = new CoverageResults(coverageMeter);
        results.setCoverageOfCoverageCriteriaFromCoverageMeter(coverageMeter);
        results.setCoverageOfCriterionTypeFromCoverageMeter(coverageMeter);
        results.exportCoverageReportToCSV(path);
    }

    public String getTestDataDir() {
        return testDataDir;
    }

    public void setTestDataDir(String testDataDir) {
        this.testDataDir = testDataDir;
    }

    public String getCoverageDataDir() {
        return coverageDataDir;
    }

    public void setCoverageDataDir(String coverageDataDir) {
        this.coverageDataDir = coverageDataDir;
    }

    public boolean getEnableCSVStats() {
        return enableCSVStats;
    }

    public void setEnableCSVStats(boolean enableCSVStats) {
        this.enableCSVStats = enableCSVStats;
    }

    public boolean getEnableInputCoverage() {
        return enableInputCoverage;
    }

    public void setEnableInputCoverage(boolean enableInputCoverage) {
        this.enableInputCoverage = enableInputCoverage;
    }

    public boolean getEnableOutputCoverage() {
        return enableOutputCoverage;
    }

    public void setEnableOutputCoverage(boolean enableOutputCoverage) {
        this.enableOutputCoverage = enableOutputCoverage;
    }

    public CoverageMeter getCoverageMeter() {
        return coverageMeter;
    }

    public void setCoverageMeter(CoverageMeter coverageMeter) {
        this.coverageMeter = coverageMeter;
    }

    public void setTestCases(Collection<TestCase> testCases) {
        this.testCases = testCases;
    }

}
