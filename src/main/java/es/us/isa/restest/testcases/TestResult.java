package es.us.isa.restest.testcases;


import static es.us.isa.restest.util.CSVManager.createFileWithHeader;
import static es.us.isa.restest.util.CSVManager.writeRow;
import static es.us.isa.restest.util.FileManager.checkIfExists;

/**
 * Domain-independent test result
 * 
 * @author Alberto Martin-Lopez
 */
public class TestResult {

    private String id;              // Test result unique identifier. Coincides with the test case one
    private String statusCode;      // Status code returned in the response
    private String responseBody;    // Body (if any) returned in the response
    private String outputFormat;    // Format of the response (JSON, XML, etc.)
    private Boolean passed;         // null = test was not checked (oracles disabled)
//    private TestCase testCase;      // Test case that corresponds to this test result

    public TestResult(String id, String statusCode, String responseBody, String outputFormat, Boolean passed) {
        this(id, statusCode, responseBody, outputFormat);
        this.passed = passed;
    }

    public TestResult(String id, String statusCode, String responseBody, String outputFormat) {
        this.id = id;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.outputFormat = outputFormat;
        this.passed = null;
//        this.testCase = testCase;
    }
    
    public TestResult(TestResult testResult) {
    	this.id = testResult.id;
        this.statusCode = testResult.statusCode;
        this.responseBody = testResult.responseBody;
        this.outputFormat = testResult.outputFormat;
        this.passed = testResult.passed;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public void exportToCSV(String filePath) {
        if (!checkIfExists(filePath)) // If the file doesn't exist, create it (only once)
            createFileWithHeader(filePath, "testResultId,statusCode,responseBody,outputContentType");

        // Generate row
        String csvResponseBody = "\"" + responseBody.replaceAll("\n", "\\\n").replaceAll("\"", "\"\"") + "\"";
        String row = id + "," + statusCode + "," + csvResponseBody + "," + outputFormat;
        writeRow(filePath, row);
    }
}