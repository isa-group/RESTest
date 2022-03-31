package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.apache.logging.log4j.LogManager;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import static java.net.URLDecoder.decode;

import static es.us.isa.restest.util.CSVManager.readCSV;

/**
 * This class allows to populate a List of TestResult objects given
 * the path of a CSV containing the test results
 */
public class TestManager {

    private TestManager() {
        //Utility class
    }

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
            TestResult tr = new TestResult(csvRow.get(0), csvRow.get(1), csvRow.get(2), csvRow.get(3), Boolean.parseBoolean(csvRow.get(4)), csvRow.get(5));
            testResults.add(tr);
        }
        return testResults;
    }

    /**
     *
     * @param csvPath Path to the CSV file. It must contain the header
     *                "testResultId,statusCode,responseBody,outputContentType"
     * @param startRow First row to retrieve
     * @param stopRow Last row to retrieve
     * @return Collection of TestResult objects
     */
    public static List<TestResult> getTestResults(String csvPath, Integer startRow, Integer stopRow) {
        List<List<String>> csvRows = readCSV(csvPath, false);
        int start = startRow != null ? startRow : 0;
        int stop = stopRow != null ? stopRow : csvRows.size();

        List<TestResult> testResults = new ArrayList<>();
        for (int i=start; i<stop; i++) {
            TestResult tr = new TestResult(csvRows.get(i).get(0), csvRows.get(i).get(1), csvRows.get(i).get(2), csvRows.get(i).get(3), Boolean.parseBoolean(csvRows.get(i).get(4)), csvRows.get(i).get(5));
            testResults.add(tr);
        }

        return testResults;
    }

    /**
     *
     * @param csvPath Path to the CSV file. It must contain the header
     *                "testResultId,statusCode,responseBody,outputContentType"
     * @return Last TestResult object added to the CSV file
     */
    public static TestResult getLastTestResult(String csvPath) {
        List<List<String>> csvRows = readCSV(csvPath, false);
        return new TestResult(csvRows.get(csvRows.size()-1).get(0), csvRows.get(csvRows.size()-1).get(1), csvRows.get(csvRows.size()-1).get(2), csvRows.get(csvRows.size()-1).get(3));
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
            TestCase tc = new TestCase(csvRow.get(0), Boolean.parseBoolean(csvRow.get(1)), csvRow.get(4),
                    csvRow.get(5), HttpMethod.valueOf(csvRow.get(6)));
            tc.setFaultyReason(csvRow.get(2));
            tc.setFulfillsDependencies(Boolean.parseBoolean(csvRow.get(3)));
            tc.setInputFormat(csvRow.get(7));
            tc.setBodyParameter(csvRow.get(13).equals("") ? null : csvRow.get(13));
            tc.setPathParameters(stringParamsToMap(csvRow.get(10)));
            tc.setQueryParameters(stringParamsToMap(csvRow.get(11)));
            tc.setHeaderParameters(stringParamsToMap(csvRow.get(9)));
            tc.setFormParameters(stringParamsToMap(csvRow.get(12)));
            testCases.add(tc);
        }
        return testCases;
    }

    private static Map<String, String> stringParamsToMap(String stringParameters) {
        Map<String, String> parameters = new HashMap<>();
        if (stringParameters.equals(""))
            return parameters;

        try {
            String[] pairs = stringParameters.split(";");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                parameters.put(decode(keyValue[0], StandardCharsets.UTF_8.toString()), keyValue.length==2 ? decode(keyValue[1], StandardCharsets.UTF_8.toString()) : "");
            }
        } catch (UnsupportedEncodingException e) {
            parameters.clear();
            LogManager.getLogger(TestManager.class.getName()).warn("Parameters of test case could not be decoded.", e);
            LogManager.getLogger(TestManager.class.getName()).warn(e.getMessage());
        }

        return parameters;
    }
}
