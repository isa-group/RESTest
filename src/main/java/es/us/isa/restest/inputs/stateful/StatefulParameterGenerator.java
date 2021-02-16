package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;

import java.security.SecureRandom;
import java.util.Random;


public class StatefulParameterGenerator implements ITestDataGenerator {

    private  String operationId;
    private String parameterName;

    private String dataDirPath;
    private String defaultValue;
    private OpenAPISpecification spec;

    private Random random;

    public StatefulParameterGenerator() {
        this.random = new SecureRandom();
    }

    @Override
    public JsonNode nextValue() {
        JsonNode valueNode = null;
        String jsonPath = this.dataDirPath + '/' + this.operationId + "_data.json";

        if (operationId != null && FileManager.checkIfExists(jsonPath)) {
            JsonNode jsonNode = (JsonNode) JSONManager.readJSON(jsonPath);
            JsonNode bodyNode = jsonNode.get(this.random.nextInt(jsonNode.size()));

            if(bodyNode.hasNonNull(parameterName)) {
                valueNode = bodyNode.get(parameterName);
            }
        }

        if (valueNode == null) {
            ObjectMapper mapper = new ObjectMapper();
            valueNode = mapper.getNodeFactory().textNode(defaultValue);
        }

        return valueNode;
    }

    @Override
    public String nextValueAsString() {
        String value = null;
        JsonNode node = nextValue();
        if (node != null && node.isValueNode()) {
            value = node.asText();
        }
        return value;
    }

    public String nextValueAsString(String operationPath) {
        io.swagger.v3.oas.models.Operation getOperation = spec.getSpecification().getPaths().get(operationPath).getGet();
        if (getOperation != null) {
            setOperationId(getOperation.getOperationId());
        }
        return nextValueAsString();
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public void setDataDirPath(String dataDirPath) {
        this.dataDirPath = dataDirPath;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public void setSpec(OpenAPISpecification spec) {
        this.spec = spec;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
