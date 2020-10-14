package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;

import java.util.*;

public class SemanticOperation {
    private String operationName = null;
    private String operationPath = null;
    private String operationMethod = null;
    private Map<TestParameter, Set<String>> semanticParameters = null;


    public SemanticOperation(Operation operation, List<TestParameter> semanticParameters){
        Map<TestParameter, Set<String>> map = new HashMap<>();
        semanticParameters.stream().forEach(x -> map.put(x, new HashSet<>()));

        this.operationName = operation.getOperationId();
        this.operationPath = operation.getTestPath();
        this.operationMethod = operation.getMethod();
        this.semanticParameters = map;

    }

    public String getOperationName() {
        return operationName;
    }
    public String getOperationPath() {
        return operationPath;
    }

    public String getOperationMethod() {
        return operationMethod;
    }

    public Map<TestParameter, Set<String>> getSemanticParameters() {
        return semanticParameters;
    }

    public void setSemanticParameters(Map<TestParameter, Set<String>> semanticParameters) {
        this.semanticParameters = semanticParameters;
    }

}
