package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;
import es.us.isa.restest.util.RESTestException;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
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
            ObjectNode dictNode = (ObjectNode) JSONManager.readJSON(jsonPath);
            MediaType requestBody = openApiOperation.getRequestBody().getContent().get("application/json");

            if (requestBody != null && !dictNode.isEmpty()) {
                ObjectNode rootNode = objectMapper.createObjectNode();
                try {
                    generateStatefulObjectNode(dictNode, requestBody.getSchema(), rootNode, "");
                } catch (RESTestException e) {
                    logger.warn("There isn't enough data to generate a valid request body for {} operation.", this.operationId);
                    logger.warn("RESTest will use the default request body specified in the testConf.");
                }
                if (rootNode != null && !rootNode.isEmpty()) {
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

    private void generateStatefulObjectNode(ObjectNode dictNode, Schema<?> schema, JsonNode rootNode, String prefix) throws RESTestException {
        if (schema.get$ref() != null) {
            schema = spec.getSpecification().getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }

        JsonNode childNode = null;
        if (schema.getType().equals("object")) {
            childNode = "".equals(prefix)? rootNode : objectMapper.createObjectNode();

            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                String newPrefix = "".equals(prefix)? prefix + entry.getKey() : prefix + '.' + entry.getKey();
                generateStatefulObjectNode(dictNode, entry.getValue(), childNode, newPrefix);
            }

        } else if (schema.getType().equals("array")) {
            childNode = objectMapper.createArrayNode();
            generateStatefulObjectNode(dictNode, ((ArraySchema)schema).getItems(), childNode, prefix);
        } else {
            ArrayNode valueArray = (ArrayNode) dictNode.get(prefix);

            if(valueArray != null) {
                childNode = valueArray.get(random.nextInt(valueArray.size()));
            } else if (schema.getExample() != null) {
                childNode = createNodeFromExample(schema);
            }
        }

        if (!"".equals(prefix)) {
            if (childNode == null || childNode.isNull() || childNode.isMissingNode()) {
                throw new RESTestException();
            } else if (rootNode.isObject()) {
                ((ObjectNode) rootNode).set(prefix.substring(prefix.lastIndexOf('.') + 1), childNode);
            } else {
                ((ArrayNode) rootNode).add(childNode);
            }
        }

    }

    private JsonNode createNodeFromExample(Schema<?> schema) {
        return objectMapper.getNodeFactory().nullNode();
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
