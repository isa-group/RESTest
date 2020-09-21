package es.us.isa.restest.testcases.restassured.executors;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.testcases.restassured.filters.NominalOrFaultyTestCaseFilter;
import es.us.isa.restest.testcases.restassured.filters.ResponseValidationFilter;
import es.us.isa.restest.testcases.restassured.filters.StatusCode5XXFilter;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * This class allows to instantiate an abstract test case into REST Assured, so that
 * it is executed without the need of using a TestWriter
 */
public class RestAssuredExecutor {

    private OpenAPISpecification oasSpec;
    private String basePath;
    private boolean logging;
    private ResponseValidationFilter validationFilter;
    private StatusCode5XXFilter statusCode5XXFilter;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private Boolean lastTestPassed; // Null = test was not checked (oracles disabled)

    public RestAssuredExecutor(OpenAPISpecification oasSpec) {
        this.oasSpec = oasSpec;
        basePath = oasSpec.getSpecification().getServers().get(0).getUrl().replaceAll("/$", "");
        logging = true;
        validationFilter = new ResponseValidationFilter(oasSpec.getPath());
        statusCode5XXFilter = new StatusCode5XXFilter();
        totalTests = 0;
        passedTests = 0;
        failedTests = 0;
        lastTestPassed = null;
    }

    public TestResult executeTest(TestCase testCase) {
        // Build request
        RequestSpecification request = RestAssured.given();
        if (logging)
            request.log().all();
        testCase.getQueryParameters().forEach(request::queryParam);
        testCase.getHeaderParameters().forEach(request::header);
        testCase.getPathParameters().forEach(request::pathParam);
        if (testCase.getInputFormat().equals("application/x-www-form-urlencoded"))
            request.contentType(testCase.getInputFormat());
        if (!testCase.getFormParameters().isEmpty())
            testCase.getFormParameters().forEach(request::formParam);
        if (testCase.getBodyParameter() != null && !testCase.getBodyParameter().equals("")) {
            request.contentType("application/json");
            request.body(testCase.getBodyParameter());
        }

        // Send request and get response
        Response response = request
                .when()
                .request(testCase.getMethod().toString(), basePath + testCase.getPath());
        if (logging)
            response.then().log().all();
        else
            response.then();

        // Assert response and update counters
        if (testCase.getEnableOracles()) {
            NominalOrFaultyTestCaseFilter nominalFaultyFilter = new NominalOrFaultyTestCaseFilter(testCase.getFaulty(), testCase.getFulfillsDependencies(), testCase.getFaultyReason());
            try {
                statusCode5XXFilter.filterValidation(response);
                nominalFaultyFilter.filterValidation(response);
                validationFilter.filterValidation(response, testCase.getPath(), testCase.getMethod().toString());
                passedTests++;
                lastTestPassed = true;
            } catch (RuntimeException ex) {
                failedTests++;
                lastTestPassed = false;
            }
        } else {
            lastTestPassed = null;
        }

        totalTests++;

        return new TestResult(testCase.getId(), Integer.toString(response.getStatusCode()), response.getBody().asString(), response.getContentType(), lastTestPassed);
    }

    public int getTotalTests() {
        return totalTests;
    }

    public void setTotalTests(int totalTests) {
        this.totalTests = totalTests;
    }

    public int getPassedTests() {
        return passedTests;
    }

    public void setPassedTests(int passedTests) {
        this.passedTests = passedTests;
    }

    public int getFailedTests() {
        return failedTests;
    }

    public void setFailedTests(int failedTests) {
        this.failedTests = failedTests;
    }

    public Boolean getLastTestPassed() {
        return lastTestPassed;
    }

    public void setLastTestPassed(Boolean lastTestPassed) {
        this.lastTestPassed = lastTestPassed;
    }

    public boolean isLogging() {
        return logging;
    }

    public void setLogging(boolean logging) {
        this.logging = logging;
    }
}
