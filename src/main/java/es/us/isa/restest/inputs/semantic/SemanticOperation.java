package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;
import org.junit.Test;

import java.util.*;

public class SemanticOperation {
    private String operationName = null;
    private Map<TestParameter, Set<String>> semanticParameters = null;


    public SemanticOperation(Operation operation, List<TestParameter> semanticParameters){
        Map<TestParameter, Set<String>> map = new HashMap<>();
        semanticParameters.stream().forEach(x -> map.put(x, new HashSet<>()));

        this.operationName = operation.getOperationId();
        this.semanticParameters = map;

    }

    public String getOperationName() {
        return operationName;
    }

    public Map<TestParameter, Set<String>> getSemanticParameters() {
        return semanticParameters;
    }

    public void setSemanticParameters(Map<TestParameter, Set<String>> semanticParameters) {
        this.semanticParameters = semanticParameters;
    }
}
