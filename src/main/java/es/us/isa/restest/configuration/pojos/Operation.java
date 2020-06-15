
package es.us.isa.restest.configuration.pojos;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class Operation {

    private String testPath;
    private String operationId;
    private String method;
    private List<TestParameter> testParameters = null;
    private String expectedResponse;

    @JsonIgnore
    private io.swagger.v3.oas.models.Operation openApiOperation;

    public String getTestPath() {
        return testPath;
    }

    public void setTestPath(String testPath) {
        this.testPath = testPath;
    }

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

    public io.swagger.v3.oas.models.Operation getOpenApiOperation() {
        return openApiOperation;
    }

    public void setOpenApiOperation(io.swagger.v3.oas.models.Operation openApiOperation) {
        this.openApiOperation = openApiOperation;
    }

}
