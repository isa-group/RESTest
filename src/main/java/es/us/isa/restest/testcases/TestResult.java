package es.us.isa.restest.testcases;


import static es.us.isa.restest.util.CSVManager.createCSVwithHeader;
import static es.us.isa.restest.util.CSVManager.writeCSVRow;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.apache.commons.text.StringEscapeUtils.escapeCsv;

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
    private String failReason;      // null = test was not checked (oracles disabled)
//    private TestCase testCase;      // Test case that corresponds to this test result

    public TestResult(String id, String statusCode, String responseBody, String outputFormat, Boolean passed, String failReason) {
        this(id, statusCode, responseBody, outputFormat);
        this.passed = passed;
        this.failReason = failReason;
    }

    public TestResult(String id, String statusCode, String responseBody, String outputFormat) {
        this.id = id;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.outputFormat = outputFormat;
        this.passed = null;
        this.failReason = null;
//        this.testCase = testCase;
    }
    
    public TestResult(TestResult testResult) {
    	this.id = testResult.id;
        this.statusCode = testResult.statusCode;
        this.responseBody = testResult.responseBody;
        this.outputFormat = testResult.outputFormat;
        this.passed = testResult.passed;
        this.failReason = testResult.failReason;
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

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getFlatRepresentation() {
        return this.getStatusCode() +    // Status code
                this.getOutputFormat() + // Content type
                this.getResponseBody();  // Body
    }

    public void exportToCSV(String filePath) {
        if (!checkIfExists(filePath)) // If the file doesn't exist, create it (only once)
            createCSVwithHeader(filePath, "testResultId,statusCode,responseBody,outputContentType,passed,failReason");

        // Generate row, we need to escape all fields susceptible to contain characters such as ',', '\n', '"', etc.
        String row = id + "," + statusCode + "," + escapeCsv(responseBody) + "," + outputFormat + "," + passed + "," + escapeCsv(failReason);
        writeCSVRow(filePath, row);
    }
}