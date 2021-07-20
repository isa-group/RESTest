package es.us.isa.restest.inputs.fuzzing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;

import java.io.File;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Random;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.JSONManager.readJSON;


public class ParameterGenerator implements ITestDataGenerator {

    private String operationMethod;
    private String operationPath;
    private String parameterName;

    private String dataDirPath;
    private String defaultValue;
    private OpenAPISpecification spec;

    private Random random;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParameterGenerator() {
        this.random = new SecureRandom();
    }

    @Override
    public JsonNode nextValue() {
        JsonNode valueNode = null;
        String jsonPath = dataDirPath + "/stateful_data.json";

        if (operationPath != null && checkIfExists(jsonPath)) {
            ObjectNode dict = (ObjectNode) readJSON(jsonPath);

            // Data from the same operation:
            ObjectNode operationDict = (ObjectNode) dict.get(operationMethod + operationPath);
            if (operationDict != null) {
                ArrayNode paramDict = ((ArrayNode) dict.get(parameterName));
                if (paramDict != null) {
                    valueNode = paramDict.get(this.random.nextInt(paramDict.size()));
                }
            }

            // Data from other operations:
            if (valueNode == null) {
                for (JsonNode otherOperationDict : dict) {
                    ArrayNode paramDict = ((ArrayNode) otherOperationDict.get(parameterName));
                    if (paramDict != null) {
                        valueNode = paramDict.get(this.random.nextInt(paramDict.size()));
                        break;
                    }
                }
            }
        }

        if (valueNode == null) {
            valueNode = objectMapper.getNodeFactory().textNode(defaultValue);
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
            setOperation("GET", operationPath);
        }
        return nextValueAsString();
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public void setDataDirPath(String dataDirPath) {
        this.dataDirPath = dataDirPath;
    }

    public void setOperation(String operationMethod, String operationPath) {
        this.operationMethod = operationMethod;
        this.operationPath = operationPath;
    }

    public void setSpec(OpenAPISpecification spec) {
        this.spec = spec;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
