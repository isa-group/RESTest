package es.us.isa.restest.reporting;

import es.us.isa.restest.configuration.pojos.*;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.coverage.CoverageResults;
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

import static es.us.isa.restest.configuration.pojos.ParameterValues.getValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.main.TestGenerationAndExecution.*;
import static es.us.isa.restest.util.FileManager.*;

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
        // TODO: Add x-example from OAS to valid values
        List<Operation> operations = getTestConfigurationObject().getTestConfiguration().getOperations();

        // Store the values of the parameters of successful and unsuccessful operations (current iteration)
        Map<Pair<String, TestParameter>, Set<String>> validValues = getMapOfSemanticParameters(operations);
        Map<Pair<String, TestParameter>, Set<String>> invalidValues = getMapOfSemanticParameters(operations);

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
                updateValidAndInvalidValues(testCase, validValues, invalidValues, valuesFromPreviousIterations, responseCode);
            }
        }

        // Write csv of valid (directory)
        for(ParameterValues parameterValues: valuesFromPreviousIterations){
            Pair<String, TestParameter> key = new Pair<>(parameterValues.getOperationId(), parameterValues.getTestParameter());
            parameterValues.updateValidAndInvalidValuesCSV(validValues.get(key), invalidValues.get(key));
        }

        // Learn regular expression
        // valuesFromPreviousIterations has been updated, containing PREVIOUS AND CURRENT values
        for(ParameterValues parameterValues: valuesFromPreviousIterations){

            Set<String> validSet = parameterValues.getValidValues();
            Set<String> invalidSet = parameterValues.getInvalidValues();

            // If the obtained data is enough, a regular expression is generated and the associated csv file is filtered
            if(invalidSet.size() >= 5 && validSet.size() >= 5){

                // OperationName_parameterId
                String name = parameterValues.getOperationId() + "_" + parameterValues.getTestParameter().getName();

                // Generate regex
                logger.info("Generating regex...");
                FinalSolution solution = learnRegex(name, validSet, invalidSet,false);
                String regex = solution.getSolution();
                logger.info("Regex learned for parameter " + parameterValues.getTestParameter().getName() + ": " + regex);
                logger.info("F1-Score: " + solution.getValidationPerformances().get("match f-measure"));

                // If the performance of the generated regex surpasses a given value of F1-Score, filter csv file
                if(solution.getValidationPerformances().get("match f-measure")  >= 0.9){
                    // Filter all the CSVs of the associated testParameter
                    updateCsvWithRegex(parameterValues, regex);

                    // Delete CSV of successful and failed values of previous iterations after the update with regex
                    deleteFile(parameterValues.getValidCSVPath());
                    deleteFile(parameterValues.getInvalidCSVPath());

                    // Second predicate search using the generated regex
                    if(secondPredicateSearch){
                        System.out.println(regex);
                        System.out.println(parameterValues.getTestParameter());
                        System.out.println(parameterValues.getOperationId());
                    }

                    // TODO: REMOVE THIS FOR LOOP
                    // Delete the invalid files from the other parameters
                    for(ParameterValues otherParameterValues: valuesFromPreviousIterations){
                        deleteFile(otherParameterValues.getInvalidCSVPath());
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
