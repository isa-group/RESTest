package es.us.isa.restest.coverage;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.TestManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static es.us.isa.restest.util.FileManager.deleteFile;

/**
 * This class computes the API coverage achieved by a set of test cases and test
 * results. It takes as input: 1) path to OpenAPI Specification; and 2) paths to
 * a set of "test-cases*.csv" files. It is *REQUIRED* that the files containing the
 * test cases are named this way. It is also *REQUIRED* that there exists one
 * "test-results*.csv" file per "test-cases*.csv" file, named in the same way
 *
 */
public class CoverageComputation {

    private static Logger logger = LogManager.getLogger(CoverageComputation.class.getName());

    public static void main(String[] args) {
        String specPath = "src/test/resources/restest-test-resources/coverage-data/swagger.yaml";
        OpenAPISpecification spec;
        String testDirPath = "src/test/resources/restest-test-resources/coverage-data";
        File testDir;
        int batchSize = Integer.MAX_VALUE;
        CoverageMeter coverageMeter;
        List<Pair<String, String>> testCasesResultsFiles = new ArrayList<>(); // left = testCasesFile; right = testResultsFile

        // Validation of arguments
        if (args.length != 0 && args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException("You must provide two or three arguments: 1) path to OAS,2) path to " +
                    "folder containing test cases and test results in CSV format, and 3) batch size for computing " +
                    "coverage (optional, but improve performance for very large test suites, e.g., >100K test cases.");
        }

        if (args.length == 2) {
            specPath = args[0];
            testDirPath = args[1];
        }

        if (args.length == 3) {
            try {
                batchSize = Integer.parseInt(args[2]);
                if (batchSize <= 0)
                    throw new Exception();
            } catch (Exception e) {
                throw new IllegalArgumentException("The batch size must be an integer greater than 0.");
            }
        }

        try {
            spec = new OpenAPISpecification(specPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("The specified OAS file is not valid or does not exist.", e);
        }

        try {
            testDir = new File(testDirPath);
            if (!testDir.isDirectory())
                throw new IllegalArgumentException("The specified path is not a directory");
        } catch (Exception e) {
            throw new IllegalArgumentException("The specified path is not a directory or does not exist.", e);
        }

        // Load CoverageMeter
        try {
            coverageMeter = new CoverageMeter(new CoverageGatherer(spec));
        } catch (Exception e) {
            throw new IllegalStateException("There was a problem loading the CoverageMeter. The OAS may be too complex.", e);
        }

        // Load test cases and test results files
        logger.info("Reading CSV files (test cases and test results)");

        List<String> dirFiles = Arrays.asList(testDir.list());
        List<String> testCasesFiles = dirFiles.stream().filter(f -> f.startsWith("test-cases")).collect(Collectors.toList());

        for (String testCasesFile: testCasesFiles) {
            String testResultsFile = testCasesFile.replaceAll("test-cases", "test-results");
            if (new File(testDir.getPath() + "/" + testResultsFile).exists()) // Add test-cases file only if respective test-results file exists too
                testCasesResultsFiles.add(Pair.with(testDir.getPath() + "/" + testCasesFile, testDir.getPath() + "/" + testResultsFile));
        }

        // Add test cases and test results to coverageMeter progressively
        int i = 1;
        int total = testCasesResultsFiles.size();
        logger.info("Computing coverage");
        for (Pair<String, String> testCasesResultsFile: testCasesResultsFiles) {
            Collection<TestCase> testSuite = TestManager.getTestCases(testCasesResultsFile.getValue0());
            Collection<TestResult> testResults = TestManager.getTestResults(testCasesResultsFile.getValue1());
            coverageMeter.addTestSuite(testSuite);
            coverageMeter.addTestResults(testResults, testSuite);
            logger.info("Progress: {}/{}", i++, total);
        }

        // Export coverage to CSV (both a priori and a posteriori)
        String aPrioriCoveragePath = testDir.getPath() + "/" + PropertyManager.readProperty("data.coverage.computation.priori.file") + ".csv";
        String aPosterioriCoveragePath = testDir.getPath() + "/" + PropertyManager.readProperty("data.coverage.computation.posteriori.file") + ".csv";

        deleteFile(aPrioriCoveragePath);
        deleteFile(aPosterioriCoveragePath);

        logger.info("Generating coverage a priori");
        exportCoverageReport(coverageMeter, aPrioriCoveragePath);
        logger.info("Generating coverage a posteriori");
        exportCoverageReport(coverageMeter.getAPosteriorCoverageMeter(5005), aPosterioriCoveragePath);
        logger.info("Coverage files generated in path {}", testDirPath);
    }

    private static void exportCoverageReport(CoverageMeter coverageMeter, String path) {
        CoverageResults results = new CoverageResults(coverageMeter);
        results.setCoverageOfCoverageCriteriaFromCoverageMeter(coverageMeter);
        results.setCoverageOfCriterionTypeFromCoverageMeter(coverageMeter);
        results.exportCoverageReportToCSV(path);
    }
}
