package es.us.isa.restest.configuration.pojos;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;

import java.util.*;

import static es.us.isa.restest.configuration.pojos.SemanticParameter.generateSemanticParameters;

public class SemanticOperation {
    private String operationName = null;
    private String operationPath = null;
    private String operationMethod = null;
    private String operationId = null;
    private Set<SemanticParameter> semanticParameters = null;



    public SemanticOperation(Operation operation, Set<TestParameter> testParameters){

        this.operationName = operation.getOperationId();
        this.operationPath = operation.getTestPath();
        this.operationMethod = operation.getMethod();
        this.operationId = operation.getOperationId();
        this.semanticParameters = generateSemanticParameters(testParameters);

    }

    public String getOperationName() {
        return operationName;
    }
    public String getOperationPath() {
        return operationPath;
    }
    public String getOperationId() {
        return operationId;
    }
    public String getOperationMethod() {
        return operationMethod;
    }

    public Set<SemanticParameter> getSemanticParameters() {
        return semanticParameters;
    }

    public void setSemanticParameters(Set<SemanticParameter> semanticParameters) {
        this.semanticParameters = semanticParameters;
    }

    public void updateSemanticParametersValues(Map<String, Set<String>> result){
        for(SemanticParameter semanticParameter: this.semanticParameters){
            Set<String> values = result.get(semanticParameter.getTestParameter().getName());
            if(values!=null) {
                semanticParameter.addValues(values);
            }
        }
    }

}
