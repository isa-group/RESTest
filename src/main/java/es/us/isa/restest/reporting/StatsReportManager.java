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

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static es.us.isa.restest.inputs.semantic.regexGenerator.RegexGeneratorUtils.*;
import static es.us.isa.restest.main.TestGenerationAndExecution.getExperimentName;
import static es.us.isa.restest.main.TestGenerationAndExecution.getTestConfigurationObject;
import static es.us.isa.restest.util.CSVManager.*;
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

        TestConfigurationObject testConf = getTestConfigurationObject();
        List<Operation> operations = testConf.getTestConfiguration().getOperations();

        // Store the values of the parameters of successful and unsuccessful operations
        Map<Pair<String, TestParameter>, Set<String>> validValues = getMapOfSemanticParameters(operations);
        Map<Pair<String, TestParameter>, Set<String>> invalidValues = getMapOfSemanticParameters(operations);

        // Read the valid and invalid values of previous iterations
        // Key format: <operationId, parameterName>
        Map<Pair<String, String>, Set<String>> previousValidValues = new HashMap<>();
        Map<Pair<String, String>, Set<String>> previousInvalidValues = new HashMap<>();

        for(Pair<String, TestParameter> key: validValues.keySet()){
            String csvPath = PropertyManager.readProperty("data.tests.dir") + "/" + getExperimentName() + "/validAndInvalidValues/" + key.getKey() + "/" + key.getValue().getName() + "/";
            createDir(csvPath); // This dir is created if it does not exist

            String validPath = csvPath + "valid.csv";
            String invalidPath = csvPath + "invalid.csv";
            createFileIfNotExists(validPath);
            createFileIfNotExists(invalidPath);

            // Read valid and invalid values from previous iterations
            // TODO: Convert into a get from map
            Set<String> readValidValues = new HashSet<>(readValues(validPath));
            Set<String> readInvalidValues = new HashSet<>(readValues(invalidPath));

            Pair<String, String> operationParameterName = new Pair<>(key.getKey(), key.getValue().getName());

            previousValidValues.put(operationParameterName, readValidValues);
            previousInvalidValues.put(operationParameterName, readInvalidValues);

        }


        // --------------------------------------------------------------------------------------

        String csvTrPath = testDataDir + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
        List<TestResult> trs = TestManager.getTestResults(csvTrPath);


        // Iterate the test cases of an operation
        for(TestCase testCase: testCases) {
            String operationId = testCase.getOperationId();

            // The results are only considered if the testCase is not faulty
            if (!testCase.getFaulty()) {

                // Obtain response code of the given testCase
                String responseCode = trs.stream().filter(tr -> tr.getId().equals(testCase.getId()))
                        .findFirst()
                        .orElseThrow(() -> new NullPointerException("Associated test result not found")).getStatusCode();

                // TODO: Advanced classification
                // Iterate semantic parameters
                // Filter by operationId
                Set<TestParameter> parametersOfOperation = validValues.keySet().stream()
                        .filter(x -> x.getKey().equals(operationId)).map(x -> x.getValue())
                        .collect(Collectors.toSet());

                for (TestParameter parameter : parametersOfOperation) {
                    Pair<String, TestParameter> pair = new Pair<>(operationId, parameter);

                    // Search parameter value in corresponding map
                    String value = "";
                    switch (parameter.getIn()) {
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
                    // 5XX codes are not taken into consideration
                    switch (responseCode.charAt(0)) {
                        case '2':
                            validValues.get(pair).add(value);
                            break;
                        case '4':
//                            parametersOfOperation (no hace falta, solo el operationId)
                            // validValues.get(pair)
                            // previousValidValues
                            // Falta conocer el resto de parámetros semánticos del testCase en cuestión
                            if(isTestValueInvalid()){
                                // TODO: Añadir solamente si está en máximo grado de aislamiento o el resto de parámetros (semánticos) son válidos
                                invalidValues.get(pair).add(value);
                            }
                            break;
                    }

                }

            }
        }

        // TODO: Convert this for loop into a function
        // Write csv of valid (directory)
        for(Pair<String, TestParameter> key: validValues.keySet()){
            // TODO: Avoid repeated values
            // operationId/parameterName/valid.csv
            // TODO: Convert to function
            String csvPath = PropertyManager.readProperty("data.tests.dir") + "/" + getExperimentName() + "/validAndInvalidValues/" + key.getKey() + "/" + key.getValue().getName() + "/";
            createDir(csvPath);

            String validPath = csvPath + "valid.csv";
            String invalidPath = csvPath + "invalid.csv";
            createFileIfNotExists(validPath);
            createFileIfNotExists(invalidPath);

            // Read valid and invalid values from previous iterations
            // TODO: Convert into a get from map
            Set<String> allValidValues = new HashSet<>(readValues(validPath));
            Set<String> allInvalidValues = new HashSet<>(readValues(invalidPath));

            // Merge both sets (previous iterations and current iteration)
            allValidValues.addAll(validValues.get(key));
            allInvalidValues.addAll(invalidValues.get(key));

            // Check for duplicates (if a value was considered invalid but appeared in a valid operation, it is deleted from the "invalid" set)
            Set<String> intersection = new HashSet<>(allValidValues);
            intersection.retainAll(allInvalidValues);
            allInvalidValues.removeAll(intersection);


            // Write the Set of values as CSV files
            try {
                collectionToCSV(validPath, allValidValues);
                collectionToCSV(invalidPath, allInvalidValues);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // PROVISIONAL: DELETE IN THE FUTURE
            System.out.println("---------------------------------------------------------------------------");
            System.out.println("---------------------------------------------------------------------------");
            System.out.println(allValidValues);
            System.out.println(allInvalidValues);
            System.out.println("---------------------------------------------------------------------------");
            System.out.println("---------------------------------------------------------------------------");

        }






        // Learn regular expression
        for(Pair<String, TestParameter> key: validValues.keySet()){
            String name = key.getKey() + "_" + key.getValue().getName();          // OperationName_parameterId
            // Read valid and invalid values from previous and current iteration
            // TODO: Convert to function
            String csvPath = PropertyManager.readProperty("data.tests.dir") + "/" + getExperimentName() + "/validAndInvalidValues/" + key.getKey() + "/" + key.getValue().getName() + "/";
            String validPath = csvPath + "valid.csv";
            String invalidPath = csvPath + "invalid.csv";

            Set<String> validSet = new HashSet<>(readValues(validPath));
            Set<String> invalidSet = new HashSet<>(readValues(invalidPath));

            // If the obtained data is enough, a regular expression is generated and the associated csv file is filtered
            if(invalidSet.size() >= 5 && validSet.size() >= 5){

                // Generate regex
                logger.info("Generating regex...");
                FinalSolution solution = learnRegex(name, validSet, invalidSet,false);
                String regex = solution.getSolution();
                Pattern pattern = Pattern.compile(regex);
                logger.info("Regex learned: " + regex);

                // If the performance of the generated regex surpasses a given value of F1-Score, filter csv file
                if(solution.getValidationPerformances().get("match f-measure")  > 0.9){
                    updateCsvWithRegex(key, pattern);

                    // Delete CSV of successful and failed values of previous iterations after the update with regex
                    deleteFile(validPath);
                    deleteFile(invalidPath);
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
