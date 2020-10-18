package es.us.isa.restest.testcases.restassured.filters;

import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import io.restassured.response.Response;

/**
 * REST-Assured filter to be extended by all RESTest filters which handle oracles.
 * This class implements a method to export a REST-Assured response to a TestResult
 * in CSV. Such method must be called before throwing the exception corresponding
 * to the oracle being violated.
 */
public class OracleFilter {

    protected String APIName;
    protected String testResultId;

    public OracleFilter() {
        super();
    }

    public OracleFilter(String APIName) {
        super();
        this.APIName = APIName;
    }

    public OracleFilter(String testResultId, String APIName) {
        super();
        this.testResultId = testResultId;
        this.APIName = APIName;
    }

    protected void exportTestResultToCSV(Response response, Boolean passed) {
        String testDataFile = PropertyManager.readProperty("data.tests.dir") + "/" + APIName + "/" + PropertyManager.readProperty("data.tests.testresults.file");
        TestResult tr = new TestResult(testResultId, Integer.toString(response.statusCode()), response.asString(), response.contentType(), passed);
        tr.exportToCSV(testDataFile);
    }

    protected void saveTestResultAndThrowException(Response response, String message) {
        if (APIName != null && testResultId != null)
            exportTestResultToCSV(response, false);
        throw new RuntimeException(message);
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
}
