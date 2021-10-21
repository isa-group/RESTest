package es.us.isa.restest.coverage;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.TestManager;
import org.javatuples.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    public static void main(String[] args) {
        String specPath = "src/test/resources/DHL/swagger.yaml";
        OpenAPISpecification spec;
        String testDirPath = "target/test-data/DHL_semantic_address";
        File testDir;
        CoverageMeter coverageMeter;
        List<Pair<String, String>> testCasesResultsFiles = new ArrayList<>(); // left = testCasesFile; right = testResultsFile

        // Validation of arguments
        if (args.length != 0 && args.length != 2) {
            throw new IllegalArgumentException("You must provide two arguments: 1) path to OAS, and 2) path to " +
                    "folder containing test cases and test results in CSV format.");
        }

        if (args.length == 2) {
            specPath = args[0];
            testDirPath = args[1];
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
        List<String> dirFiles = Arrays.asList(testDir.list());
        List<String> testCasesFiles = dirFiles.stream().filter(f -> f.startsWith("test-cases")).collect(Collectors.toList());

        for (String testCasesFile: testCasesFiles) {
            String testResultsFile = testCasesFile.replaceAll("test-cases", "test-results");
            if (new File(testDir.getPath() + "/" + testResultsFile).exists()) // Add test-cases file only if respective test-results file exists too
                testCasesResultsFiles.add(Pair.with(testDir.getPath() + "/" + testCasesFile, testDir.getPath() + "/" + testResultsFile));
        }

        // Add test cases and test results to coverageMeter progressively
        for (Pair<String, String> testCasesResultsFile: testCasesResultsFiles) {
            coverageMeter.addTestSuite(TestManager.getTestCases(testCasesResultsFile.getValue0()));
            coverageMeter.addTestResults(TestManager.getTestResults(testCasesResultsFile.getValue1()));
        }

        // Export coverage to CSV (both a priori and a posteriori)
        String aPrioriCoveragePath = testDir.getPath() + "/" + PropertyManager.readProperty("data.coverage.computation.priori.file") + ".csv";
        String aPosterioriCoveragePath = testDir.getPath() + "/" + PropertyManager.readProperty("data.coverage.computation.posteriori.file") + ".csv";

        deleteFile(aPrioriCoveragePath);
        deleteFile(aPosterioriCoveragePath);

        exportCoverageReport(coverageMeter, aPrioriCoveragePath);
        exportCoverageReport(coverageMeter.getAPosteriorCoverageMeter(), aPosterioriCoveragePath);
    }

    private static void exportCoverageReport(CoverageMeter coverageMeter, String path) {
        CoverageResults results = new CoverageResults(coverageMeter);
        results.setCoverageOfCoverageCriteriaFromCoverageMeter(coverageMeter);
        results.setCoverageOfCriterionTypeFromCoverageMeter(coverageMeter);
        results.exportCoverageReportToCSV(path);
    }
}
