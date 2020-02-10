package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static es.us.isa.restest.util.CSVManager.readCSV;

/**
 * This class allows to populate a List of TestResult objects given
 * the path of a CSV containing the test results
 */
public class TestResultsManager {

    /**
     *
     * @param csvPath Path to the CSV file. It must contain the header
     *                "testResultId,statusCode,responseBody,outputContentType"
     * @return Collection of TestResult objects
     */
    public static List<TestResult> getTestResults(String csvPath) {
        List<List<String>> csvRows = readCSV(csvPath, false);
        List<TestResult> testResults = new ArrayList<>();
        for (List<String> csvRow: csvRows) {
            TestResult tr = new TestResult(csvRow.get(0), csvRow.get(1), csvRow.get(2), csvRow.get(3));
            testResults.add(tr);
        }
        return testResults;
    }
}
