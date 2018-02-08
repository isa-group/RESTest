
package es.us.isa.rester.configuration.pojos;

import java.util.List;

public class Operation {

    private String operationId;
    private String method;
    private List<TestParameter> testParameters = null;
    private String expectedResponse;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<TestParameter> getTestParameters() {
        return testParameters;
    }

    public void setTestParameters(List<TestParameter> testParameters) {
        this.testParameters = testParameters;
    }

    public String getExpectedResponse() {
        return expectedResponse;
    }

    public void setExpectedResponse(String expectedResponse) {
        this.expectedResponse = expectedResponse;
    }

}
