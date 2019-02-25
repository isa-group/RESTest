package es.us.isa.rester.testcases;


/**
 * Domain-independent test result
 * 
 * @author Alberto Martin-Lopez
 */
public class TestResult {

    private String statusCode;      // Status code returned in the response
    private String responseBody;    // Body (if any) returned in the response
    private String outputFormat;    // Format of the response (JSON, XML, etc.)
    private TestCase testCase;      // Test case that corresponds to this test result

    public TestResult(String statusCode, String responseBody, String outputFormat, TestCase testCase) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.outputFormat = outputFormat;
        this.testCase = testCase;
    }

    public String getStatusCode() {
        return this.statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseBody() {
        return this.responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getOutputFormat() {
        return this.outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public TestCase getTestCase() {
        return this.testCase;
    }

    public void setTestCase(TestCase testCase) {
        this.testCase = testCase;
    }
}