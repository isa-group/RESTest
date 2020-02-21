package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import io.swagger.models.HttpMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static es.us.isa.restest.util.CSVManager.readCSV;

/**
 * This class allows to populate a List of TestResult objects given
 * the path of a CSV containing the test results
 */
public class TestManager {

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

    /**
     *
     * @param csvPath Path to the CSV file. It must contain the header
     *                "testCaseId,faulty,operationId,path,httpMethod,inputContentType,outputContentType,
     *                headerParameters,pathParameters,queryParameters,formParameters,bodyParameter,
     *                authentication,expectedOutputs,expectedSuccessfulOutput"
     * @return Collection of TestCase objects
     */
    public static List<TestCase> getTestCases(String csvPath) {
        List<List<String>> csvRows = readCSV(csvPath, false);
        List<TestCase> testCases = new ArrayList<>();
        for(List<String> csvRow: csvRows) {
            TestCase tc = new TestCase(csvRow.get(0), Boolean.parseBoolean(csvRow.get(1)), csvRow.get(2),
                    csvRow.get(3), HttpMethod.valueOf(csvRow.get(4)));
            testCases.add(tc);
        }
        return testCases;
    }
}
