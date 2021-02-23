package es.us.isa.restest.reporting;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.coverage.CoverageResults;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.TestManager;


import it.units.inginf.male.outputs.FinalSolution;
import javafx.util.Pair;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.xtext.serializer.sequencer.ISyntacticSequencer;

import java.util.*;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.pojos.ParameterValues.getValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.Predicates.*;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.getNewValues;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConfWithNewPredicates;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.main.TestGenerationAndExecution.*;
import static es.us.isa.restest.util.FileManager.*;
import java.util.Collection;
import java.util.List;


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
    private final int maxNumberOfPredicates = 3;
    private final String metricToUse = "match recall";


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
    public void generateReport(String testId, boolean executeTestCases) {

        // Generate CVS stats
        if (enableCSVStats)
            generateCSVStats(testId);

        // Generate coverage stats
        if (enableInputCoverage || enableOutputCoverage)
            generateCoverageStats(testId, executeTestCases);

    }


    public void learn(String testId, Boolean secondPredicateSearch, OpenAPISpecification spec, String confPath) {
        List<Operation> operations = getTestConfigurationObject().getTestConfiguration().getOperations();

        // Store the values of the parameters of successful and unsuccessful operations (current iteration)
        Map<Pair<Operation, TestParameter>, Set<String>> validValues = getMapOfSemanticParameters(operations);
        Map<Pair<Operation, TestParameter>, Set<String>> invalidValues = getMapOfSemanticParameters(operations);

        // Read the valid and invalid values of previous iterations
        Set<ParameterValues> valuesFromPreviousIterations = getValuesFromPreviousIterations(getExperimentName(), validValues.keySet());

        // Get TestResults
        String csvTrPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
        List<TestResult> trs = TestManager.getTestResults(csvTrPath);

        // Iterate the test cases of an operation
        for(TestCase testCase: testCases) {
            // The results are only considered if the testCase is not faulty
            if (!testCase.getFaulty()) {
                // Obtain response code of the given testCase
                String responseCode = trs.stream().filter(tr -> tr.getId().equals(testCase.getId()))
                        .findFirst()
                        .orElseThrow(() -> new NullPointerException("Associated test result not found")).getStatusCode();

                // Add parameter value to a map depending on the response code
                updateValidAndInvalidValues(testCase, validValues, invalidValues, valuesFromPreviousIterations, responseCode, operations);
            }
        }

        // Write csv of valid (directory)
        for(ParameterValues parameterValues: valuesFromPreviousIterations){
            Pair<Operation, TestParameter> key = new Pair<>(parameterValues.getOperation(), parameterValues.getTestParameter());
            parameterValues.updateValidAndInvalidValuesCSV(validValues.get(key), invalidValues.get(key));
        }

        // Learn regular expression
        // valuesFromPreviousIterations has been updated, containing PREVIOUS AND CURRENT values
        for(ParameterValues parameterValues: valuesFromPreviousIterations){

            Set<String> validSet = parameterValues.getValidValues();
            Set<String> invalidSet = parameterValues.getInvalidValues();

            List<String> predicatesToIgnore = getPredicatesToIgnore(parameterValues.getTestParameter());

            // If the obtained data is enough, a regular expression is generated and the associated csv file is filtered
            if(invalidSet.size() >= 5 && validSet.size() >= 5){

                // OperationName_parameterId
                String name = parameterValues.getOperation().getOperationId() + "_" + parameterValues.getTestParameter().getName();

                // Generate regex
                logger.info("Generating regex...");
                FinalSolution solution = learnRegex(name, validSet, invalidSet,false);
                String regex = solution.getSolution();
                logger.info("Regex learned for parameter " + parameterValues.getTestParameter().getName() + ": " + regex);
                logger.info("Accuracy: " + solution.getValidationPerformances().get("character accuracy"));
                logger.info("Precision: " + solution.getValidationPerformances().get("match precision"));
                logger.info("Recall: " + solution.getValidationPerformances().get("match recall"));
                logger.info("F1-Score: " + solution.getValidationPerformances().get("match f-measure"));
//                "match precision"
//                        "character accuracy": 1.0,
//                        "character precision": 1.0,
//                        "match recall": 1.0,
//                        "character recall": 1.0,
//                        "match f-measure": 1.0

                // If the performance of the generated regex surpasses a given value of F1-Score, filter csv file
                if(solution.getValidationPerformances().get(metricToUse)  >= 0.9){
                    // Filter all the CSVs of the associated testParameter
                    updateCsvWithRegex(parameterValues, regex);

                    // Update CSVs of valid and invalid values according to the generated regex
                    updateCsvWithRegex(parameterValues.getValidCSVPath(), regex);
                    updateCsvWithRegex(parameterValues.getInvalidCSVPath(), regex);

                    // Second predicate search using the generated regex
                    if(secondPredicateSearch && predicatesToIgnore.size() < maxNumberOfPredicates){

                        // Get new predicates for parameter
                        Set<String> predicates = new HashSet<>();
                        predicates = getPredicates(parameterValues, regex, predicatesToIgnore, spec);


                        if(predicates.size() > 0) {
                            // Get new values
                            Set<String> results = getNewValues(parameterValues, predicates, regex);

                            // Add results to the corresponding CSV Path
                            addResultsToCSV(parameterValues, results);

                            // Add predicate to TestParameter and update testConf file
                            TestConfigurationObject conf = getTestConfigurationObject();
                            updateTestConfWithNewPredicates(conf, confPath, parameterValues, predicates);


                        }
                    }


                }

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
    private void generateCoverageStats(String testId, boolean executeTestCases) {

        // Add test cases
        getCoverageMeter().addTestSuite(testCases);

        if (enableOutputCoverage && executeTestCases) {
            // Update CoverageMeter with the test results
            String csvTrPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
            List<TestResult> trs = TestManager.getTestResults(csvTrPath);
            coverageMeter.addTestResults(trs);
        }

        if (enableInputCoverage || enableOutputCoverage) {
            // Generate coverage report (input coverage a priori)
            exportCoverageReport(coverageMeter, coverageDataDir + "/" + PropertyManager.readProperty("data.coverage.computation.priori.file") + "_" + testId + ".csv");
            logger.info("Coverage report a priori generated.");

            if(executeTestCases) {
                // Generate coverage report (input coverage a posteriori)
                exportCoverageReport(coverageMeter.getAPosteriorCoverageMeter(), coverageDataDir + "/" + PropertyManager.readProperty("data.coverage.computation.posteriori.file") + "_" + testId + ".csv");
                logger.info("Coverage report a posteriori generated.");
            }
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
