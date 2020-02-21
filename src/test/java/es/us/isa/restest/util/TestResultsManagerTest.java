package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestResult;
import org.junit.Test;

import java.util.List;

import static es.us.isa.restest.util.TestManager.getTestResults;
import static org.junit.Assert.assertEquals;

public class TestResultsManagerTest {

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
}
