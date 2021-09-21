package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.*;

import static es.us.isa.restest.inputs.fuzzing.FuzzingDictionary.getNodeFuzzingValue;
import static es.us.isa.restest.inputs.stateful.DataMatching.getParameterValue;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.JSONManager.readJSON;


public class ParameterGenerator implements ITestDataGenerator {

    private String operationMethod;
    private String operationPath;
    private String altOperationPath;
    private String parameterName;
    private String altParameterName;
    private String parameterType;

    private String dataDirPath;
    private String defaultValue;
    private OpenAPISpecification spec;

    private Random random;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LogManager.getLogger(ParameterGenerator.class);

    public ParameterGenerator() {
        this.random = new SecureRandom();
    }

    @Override
    public JsonNode nextValue() {
        JsonNode valueNode = null;
        String jsonPath = dataDirPath + "/stateful_data.json";

        if (operationPath != null && checkIfExists(jsonPath)) {
            ObjectNode dict = (ObjectNode) readJSON(jsonPath);
            valueNode = getParameterValue(dict, operationMethod,
                    altOperationPath != null ? altOperationPath : operationPath,
                    altParameterName != null ? altParameterName : parameterName
            );
        }

        if (valueNode == null)
            valueNode = objectMapper.getNodeFactory().textNode(defaultValue);

        if (valueNode == null)
            valueNode = getNodeFuzzingValue(parameterType);

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

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public void setParameterType(String parameterType) {
        this.parameterType = parameterType;
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

    public void setAltParameterName(String altParameterName) {
        this.altParameterName = altParameterName;
    }

    public void setAltOperationPath(String altOperationPath) {
        this.altOperationPath = altOperationPath;
    }
}
