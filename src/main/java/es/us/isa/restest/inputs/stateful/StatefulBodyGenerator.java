package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;


public class StatefulBodyGenerator implements ITestDataGenerator {

    String operationId;
    Operation openApiOperation;
    String defaultValue;

    String dataDirPath;
    OpenAPISpecification spec;

    Random random;
    ObjectMapper objectMapper;

    private static Logger logger = LogManager.getLogger(StatefulBodyGenerator.class);

    public StatefulBodyGenerator() {
        this.random = new SecureRandom();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ObjectNode nextValue() {
        ObjectNode body = null;
        String jsonPath = this.dataDirPath + '/' + this.operationId + "_data.json";

        if (operationId != null && FileManager.checkIfExists(jsonPath)) {
            JsonNode jsonNode = (JsonNode) JSONManager.readJSON(jsonPath);
            MediaType requestBody = openApiOperation.getRequestBody().getContent().get("application/json");

            if (requestBody != null && !jsonNode.isEmpty()) {
                ObjectNode rootNode = objectMapper.createObjectNode();
                JsonNode bodyNode = jsonNode.get(this.random.nextInt(jsonNode.size()));
                generateStatefulObjectNode(bodyNode, requestBody.getSchema(), objectMapper, rootNode);
                if (!rootNode.isEmpty()) {
                    body = rootNode;
                }
            }
        }

        if (body == null) {
            try {
                body = (ObjectNode) objectMapper.readTree(defaultValue);
            } catch (JsonProcessingException e) {
                logger.error("An error occurred when deserializing JSON: \n {}", defaultValue);
                logger.error(e.getMessage(), e);
            }
        }

        return body;
    }

    private void generateStatefulObjectNode(JsonNode jsonNode, Schema schema, ObjectMapper mapper, ObjectNode rootNode) {
        if (schema.get$ref() != null) {
            schema = spec.getSpecification().getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }
        for (Object o : schema.getProperties().entrySet()) {
            Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) o;
            JsonNode childNode;
            if (entry.getValue().getType().equals("object")) {
                childNode = mapper.createObjectNode();
                generateStatefulObjectNode(jsonNode, entry.getValue(), mapper, (ObjectNode) childNode);
            } else {
                childNode = jsonNode.findParent(entry.getKey());
                if (childNode != null && childNode.isObject()) {
                    childNode = childNode.get(entry.getKey());
                } else {
                    childNode = null;
                }
            }

            if (childNode == null || childNode.isNull() || childNode.isMissingNode()) {
                rootNode.removeAll();
                break;
            } else {
                rootNode.set(entry.getKey(), childNode);
            }
        }
    }

    @Override
    public String nextValueAsString() {
        String value = null;
        ObjectNode node = nextValue();
        if (node != null) {
            value = node.toPrettyString();
        }
        return value;
    }

    public String nextValueAsString(Operation openApiOperation, String operationPath) {
        setOpenApiOperation(openApiOperation);
        io.swagger.v3.oas.models.Operation getOperation = spec.getSpecification().getPaths().get(operationPath).getGet();
        if (getOperation != null) {
            setOperationId(getOperation.getOperationId());
        }
        return nextValueAsString();
    }

    public void setDataDirPath(String dataDirPath) {
        this.dataDirPath = dataDirPath;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public void setOpenApiOperation(Operation operation) {
        this.openApiOperation = operation;
    }

    public void setSpec(OpenAPISpecification spec) {
        this.spec = spec;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
