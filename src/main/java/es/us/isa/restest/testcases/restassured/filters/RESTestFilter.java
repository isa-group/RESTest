package es.us.isa.restest.testcases.restassured.filters;

import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import io.restassured.response.Response;

/**
 * REST-Assured filter to be extended by all RESTest filters.
 * This class implements a method to export a REST-Assured response to a TestResult
 * in CSV. Such method must be called before throwing the exception corresponding
 * to the oracle being violated.
 */
public class RESTestFilter {

    protected String APIName;
    protected String testId;
    protected String testResultId;

    protected Boolean testCaseIsFaulty; // Whether this test case is faulty or not
    protected Boolean dependenciesFulfilled; // Whether this test case fulfills all inter-parameter dependencies or not
    protected String faultyReason; // Why the test case is faulty


    public RESTestFilter() {
        super();
    }

    public RESTestFilter(Boolean testCaseIsFaulty, Boolean dependenciesFulfilled, String faultyReason) {
        this.testCaseIsFaulty = testCaseIsFaulty;
        this.dependenciesFulfilled = dependenciesFulfilled;
        this.faultyReason = faultyReason;
    }

    public RESTestFilter(String APIName) {
        super();
        this.APIName = APIName;
    }

    public RESTestFilter(String APIName, String testId) {
        super();
        this.testId = testId;
        this.APIName = APIName;
    }

    protected void exportTestResultToCSV(Response response, Boolean passed, String failReason) {
        String testDataFile = PropertyManager.readProperty("data.tests.dir") + "/" + APIName + "/" + PropertyManager.readProperty("data.tests.testresults.file") + "_" + testId + ".csv";
        TestResult tr = new TestResult(testResultId, Integer.toString(response.statusCode()), response.asString(), response.contentType(), passed, failReason);
        tr.exportToCSV(testDataFile);
    }

    protected void saveTestResultAndThrowException(Response response, String message) {
        if (APIName != null && testResultId != null)
            exportTestResultToCSV(response, false, message);
        throw new RuntimeException(message);
    }

    public void updateFaultyData(Boolean testCaseIsFaulty, Boolean dependenciesFulfilled, String faultyReason) {
        this.testCaseIsFaulty = testCaseIsFaulty;
        this.dependenciesFulfilled = dependenciesFulfilled;
        this.faultyReason = faultyReason;
    }

    public String getTestResultId() {
        return testResultId;
    }

    public void setTestResultId(String testResultId) {
        this.testResultId = testResultId;
    }

    public String getAPIName() {
        return APIName;
    }

    public void setAPIName(String APIName) {
        this.APIName = APIName;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }
}
