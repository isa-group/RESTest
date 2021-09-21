package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import org.junit.Test;

import java.util.List;

import static es.us.isa.restest.util.TestManager.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestManagerTest {

    @Test
    public void testGetTestResults() {
        String path = "src/test/resources/csvData/testResultsManagerSample.csv";
        List<TestResult> testResults = getTestResults(path);
        for (TestResult tr: testResults) {
            assertEquals("The status code should be '200'", "200", tr.getStatusCode());
            assertEquals("The content type should be 'application/json'", "application/json", tr.getOutputFormat());
        }
        assertEquals("The response body does not match", "{\"type\":\"FeatureCollection\",\"features\":[]}", testResults.get(4).getResponseBody());
    }

    @Test
    public void testGetTestCases() {
        String path = "src/test/resources/csvData/testCasesManagerSample.csv";
        List<TestCase> testCases = getTestCases(path);
        for (TestCase tc: testCases) {
            if (testCases.indexOf(tc) != 9 && testCases.indexOf(tc) != 10)
                assertNull("The body should be null", tc.getBodyParameter());
            else if (testCases.indexOf(tc) == 9)
                assertEquals("The request body does not match", "{\"businesses\": [], \"total\": 0, \"region\": {\"center\": {\"longitude\": -104.36144, \"latitude\": 41.22686}}}", tc.getBodyParameter());
            else if (testCases.indexOf(tc) == 10)
                assertEquals("The request body does not match", "{\n" +
                        " \"error\": {\n" +
                        "   \"message\": \"Received both percent_off and amount_off parameters. Please pass in only one.\",\n" +
                        "   \"type\": \"invalid_request_error\"\n" +
                        " }\n" +
                        "}\n" +
                        "", tc.getBodyParameter());
            assertEquals("The content type should be 'application/json'", "application/json", tc.getInputFormat());
            assertEquals("The faulty should be false", false, tc.getFaulty());
        }
        assertEquals("The path does not match", "/v2/locations", testCases.get(4).getPath());
        assertEquals("The query parameter does not match", "outwear", testCases.get(6).getQueryParameters().get("proximity"));
    }

    @Test
    public void testGetLastTestResult() {
        String path = "src/test/resources/csvData/testResultsManagerSample.csv";
        TestResult tr = getLastTestResult(path);
        assertEquals("The id does not match", "GETversionlocationsmarkersformatTest_t8f5p4k4yijn", tr.getId());
        assertEquals("The status code does not match", "200", tr.getStatusCode());
        assertEquals("The body does not match", "{\"type\":\"FeatureCollection\",\"features\":[]}", tr.getResponseBody());
        assertEquals("The id does not match", "application/json", tr.getOutputFormat());
    }

    @Test
    public void testGetRangeTestResults1() {
        String path = "src/test/resources/csvData/testResultsManagerSample.csv";
        List<TestResult> testResults = getTestResults(path, 4, null);
        assertEquals("The size of the TRs does not match", 12, testResults.size());
        for (TestResult tr: testResults) {
            assertEquals("The status code does not match", "200", tr.getStatusCode());
            assertEquals("The id does not match", "application/json", tr.getOutputFormat());
        }
        assertEquals("The ID of the first TR does not match", "GETversionlocationsformatTest_1ies6plrqnv50", testResults.get(0).getId());
    }

    @Test
    public void testGetRangeTestResults2() {
        String path = "src/test/resources/csvData/testResultsManagerSample.csv";
        List<TestResult> testResults = getTestResults(path, null, 5);
        assertEquals("The size of the TRs does not match", 5, testResults.size());
        for (TestResult tr: testResults) {
            assertEquals("The status code does not match", "200", tr.getStatusCode());
            assertEquals("The id does not match", "application/json", tr.getOutputFormat());
        }
        assertEquals("The ID of the first TR does not match", "GETversionlocationsformatTest_1ies6plrqnv50", testResults.get(testResults.size()-1).getId());
    }
}
