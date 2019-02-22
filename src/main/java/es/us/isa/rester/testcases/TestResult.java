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

    public TestResult() {
    }

    public TestResult(String statusCode, String responseBody, String outputFormat) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.outputFormat = outputFormat;
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
}