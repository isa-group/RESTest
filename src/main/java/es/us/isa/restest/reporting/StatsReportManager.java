package es.us.isa.restest.reporting;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.coverage.CoverageResults;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.TestManager;

import it.units.inginf.male.outputs.FinalSolution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static es.us.isa.restest.inputs.semantic.objects.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.Predicates.*;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.getNewValues;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConfWithIncreasedNumberOfTries;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConfWithNewPredicates;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.main.TestGenerationAndExecution.*;
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
    private boolean secondPredicateSearch;
    private Integer maxNumberOfPredicates;                // MaxNumberOfPredicates = AdditionalPredicates + 1
    private Integer minimumValidAndInvalidValues;
    private String metricToUse;
    private Double minimumValueOfMetric;
    private Integer maxNumberOfTriesToGenerateRegularExpression;


    private static final Logger logger = LogManager.getLogger(StatsReportManager.class.getName());

    public StatsReportManager() {
        this(PropertyManager.readProperty("data.tests.dir"), PropertyManager.readProperty("data.coverage.tests.dir"));
    }

    public StatsReportManager(String testDataDir, String coverageDataDir, boolean enableCSVStats, boolean enableInputCoverage, boolean enableOutputCoverage, CoverageMeter coverageMeter,
                              Boolean secondPredicateSearch, Integer maxNumberOfPredicates, Integer minimumValidAndInvalidValues,
                              String metricToUse, Double minimumValueOfMetric, Integer maxNumberOfTriesToGenerateRegularExpression) {
        this.testDataDir = testDataDir;
        this.coverageDataDir = coverageDataDir;
        this.enableCSVStats = enableCSVStats;
        this.enableInputCoverage = enableInputCoverage;
        this.enableOutputCoverage = enableOutputCoverage;
        this.coverageMeter = coverageMeter;

        this.secondPredicateSearch = secondPredicateSearch;
        this.maxNumberOfPredicates = maxNumberOfPredicates;
        this.minimumValidAndInvalidValues = minimumValidAndInvalidValues;
        this.metricToUse = metricToUse;
        this.minimumValueOfMetric = minimumValueOfMetric;
        this.maxNumberOfTriesToGenerateRegularExpression = maxNumberOfTriesToGenerateRegularExpression;

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


    public void learn(String testId, OpenAPISpecification spec, String confPath) {

        // 1. Get Semantic Operations (Set<SemanticOperation>)
        // 2. Set valid and invalid values with values from previous iterations
        List<Operation> operations = getTestConfigurationObject().getTestConfiguration().getOperations();
        // An operation is a SemanticOperation if it contains the genParameter "predicates"
        Set<SemanticOperation> semanticOperations = getSemanticOperationsWithValuesFromPreviousIterations(operations, getExperimentName());


        // Get TestResults
        String csvTrPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
        List<TestResult> trs = TestManager.getTestResults(csvTrPath);

        // Iterate the test cases of an operation
        for(TestCase testCase: testCases) {
            // The results are only considered if the testCase is not faulty
            if (Boolean.TRUE.equals(!testCase.getFaulty())) {
                // Obtain response code of the given testCase
                String responseCode = trs.stream().filter(tr -> tr.getId().equals(testCase.getId()))
                        .findFirst()
                        .orElseThrow(() -> new NullPointerException("Associated test result not found")).getStatusCode();

                // Add parameter value to valid or invalid values depending on the response code, ONLY if this is a SemanticOperation
                if (semanticOperations.stream().anyMatch(so -> so.getOperationId().equals(testCase.getOperationId())))
                    updateValidAndInvalidValues(semanticOperations, testCase, responseCode);
            }
        }

        // Write csv of valid and invalid values (directory)
        for(SemanticOperation semanticOperation: semanticOperations){
            semanticOperation.updateCSVWithValidAndInvalidValues(getExperimentName());
        }

        // Learn regular expression
        for(SemanticOperation semanticOperation: semanticOperations){
            for(SemanticParameter semanticParameter: semanticOperation.getSemanticParameters()){

                Set<String> validSet = semanticParameter.getValidValues();
                Set<String> invalidSet = semanticParameter.getInvalidValues();

                // If the obtained data is enough, a regular expression is generated and the associated csv file is filtered
                if(invalidSet.size() >= minimumValidAndInvalidValues && validSet.size() >= minimumValidAndInvalidValues &&
                   semanticParameter.getNumberOfTriesToGenerateRegex() < maxNumberOfTriesToGenerateRegularExpression){

                    // Increase the number of tries
                    // and update testConf with new value of number of tries
                    updateTestConfWithIncreasedNumberOfTries(getTestConfigurationObject(), confPath, semanticOperation, semanticParameter);

                    // OperationName_parameterId
                    String name = semanticOperation.getOperationId() + "_" + semanticParameter.getTestParameter().getName();

                    // Generate regex
                    logger.info("Generating regex...");
                    FinalSolution solution = learnRegex(name, validSet, invalidSet,false);
                    String regex = solution.getSolution();
                    logger.info("Regex learned for parameter {}: {} ", semanticParameter.getTestParameter().getName(), regex);
                    logger.info("Accuracy: {}", solution.getValidationPerformances().get("character accuracy"));
                    logger.info("Precision: {}", solution.getValidationPerformances().get("match precision"));
                    logger.info("Recall: {}", solution.getValidationPerformances().get("match recall"));
                    logger.info("F1-Score: {}", solution.getValidationPerformances().get("match f-measure"));
                    logger.info("\n Number of tries for generating regex for this parameter: {}/{}", semanticParameter.getNumberOfTriesToGenerateRegex(), maxNumberOfTriesToGenerateRegularExpression);
//                "match precision"
//                        "character accuracy": 1.0,
//                        "character precision": 1.0,
//                        "match recall": 1.0,
//                        "character recall": 1.0,
//                        "match f-measure": 1.0

                    // If the performance of the generated regex surpasses a given value of the selected metric (acc, precision, recall and F1-Score), filter csv file
                    if(solution.getValidationPerformances().get(metricToUse)  >= minimumValueOfMetric){
                        // Filter all the CSVs of the associated testParameter (testConfSemantic)
                        updateCsvWithRegex(semanticParameter, regex);

                        // Update CSVs of valid and invalid values according to the generated regular expression (test-data folder)
                        updateCsvWithRegex(semanticParameter.getValidCSVPath(getExperimentName(),semanticOperation.getOperationId()), regex);
                        updateCsvWithRegex(semanticParameter.getInvalidCSVPath(getExperimentName(), semanticOperation.getOperationId()), regex);

                        // Second predicate search using the generated regex
                        if(secondPredicateSearch && semanticParameter.getPredicates().size() <= maxNumberOfPredicates){

                            // Get new predicates for parameter
                            Set<String> newPredicates = getPredicates(semanticOperation, semanticParameter, regex, spec);

                            if(!newPredicates.isEmpty()) {
                                // Get new values
                                Set<String> results = getNewValues(semanticParameter, newPredicates, regex);

                                // Add results to the corresponding CSV Path
                                // Without exceding the limit
                                addResultsToCSV(semanticParameter, results);

                                // Add predicate to TestParameter
                                // Set the value of numberOfTriesToGenerateRegex to 0
                                // Update testConf file
                                // Set the value to 0 again (and update testConf accordingly)
                                TestConfigurationObject conf = getTestConfigurationObject();
                                updateTestConfWithNewPredicates(conf, confPath, semanticOperation, semanticParameter, newPredicates);
                            }

                        }

                    }

                }

            }

        }


    }

    // Generate CVS statistics (test cases to CSV)
    private void generateCSVStats(String testId) {
        logger.info("Exporting test cases to CSV");
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
            coverageMeter.addTestResults(trs, testCases);
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
