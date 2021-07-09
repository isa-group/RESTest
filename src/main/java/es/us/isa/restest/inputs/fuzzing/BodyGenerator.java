package es.us.isa.restest.inputs.fuzzing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.mutation.SchemaMutation;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.util.SchemaManager;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;


public class BodyGenerator implements ITestDataGenerator {

    String operationId;
    Operation openApiOperation;
    String defaultValue;
    boolean mutate;

    String dataDirPath;
    OpenAPISpecification spec;

    Random random;
    ObjectMapper objectMapper;

    private static final Logger logger = LogManager.getLogger(BodyGenerator.class);
    private static final String DOT_CONVERSION = "(dot)";

    public BodyGenerator() {
        this.random = new SecureRandom();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ObjectNode nextValue() {
        ObjectNode body = null;
        String jsonPath = this.dataDirPath + '/' + this.operationId + "_data.json";

        ObjectNode dictNode = operationId != null && FileManager.checkIfExists(jsonPath)? (ObjectNode) JSONManager.readJSON(jsonPath) : objectMapper.createObjectNode();
        MediaType requestBody = openApiOperation.getRequestBody().getContent().get("application/json");

        if (requestBody != null) {
            Schema mutatedSchema = mutate? new SchemaMutation(requestBody.getSchema()).mutate() : requestBody.getSchema();
            ObjectNode rootNode = objectMapper.createObjectNode();
            try {
                generateStatefulObjectNode(dictNode, mutatedSchema, rootNode, "");
            } catch (RESTestException e) {
                logger.warn("There isn't enough data to generate a valid request body for {} operation.", this.operationId);
                logger.warn("RESTest will use the default request body specified in the testConf.");
            }
            if (rootNode != null) {
                body = rootNode;
            }
        }

        return body;
    }

    private void generateStatefulObjectNode(ObjectNode dictNode, Schema<?> schema, JsonNode rootNode, String prefix) throws RESTestException {
        if (schema.get$ref() != null) {
            schema = spec.getSpecification().getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }

        JsonNode childNode;
        if (schema.getType().equals("object")) {
            childNode = "".equals(prefix)? rootNode : objectMapper.createObjectNode();

            if (schema.getProperties() != null) {
                for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                    String paramName = entry.getKey().replace(".", DOT_CONVERSION);
                    String newPrefix = "".equals(prefix)? prefix + paramName : prefix + '.' + paramName;
                    generateStatefulObjectNode(dictNode, entry.getValue(), childNode, newPrefix);
                }
            }

        } else if (schema.getType().equals("array")) {
            childNode = objectMapper.createArrayNode();
            if (schema instanceof ArraySchema && ((ArraySchema)schema).getItems() != null) {
                generateStatefulObjectNode(dictNode, ((ArraySchema)schema).getItems(), childNode, prefix);
            }
        } else {
            String resolvedPrefix = prefix.replace("-duplicated", "").replace(DOT_CONVERSION, ".");
            ArrayNode valueArray = (ArrayNode) dictNode.get(resolvedPrefix);

            if(valueArray != null) {
                childNode = valueArray.get(random.nextInt(valueArray.size()));
            } else {
                childNode = createNodeFromExample(schema, resolvedPrefix);
            }
        }

        if (!"".equals(prefix)) {
            if (childNode == null || childNode.isNull() || childNode.isMissingNode()) {
                throw new RESTestException();
            } else if (rootNode.isObject()) {
                ((ObjectNode) rootNode).set(prefix.substring(prefix.lastIndexOf('.') + 1).replace(DOT_CONVERSION, "."), childNode);
            } else {
                ((ArrayNode) rootNode).add(childNode);
            }
        }

    }

    private JsonNode createNodeFromExample(Schema<?> schema, String prefix) {
        JsonNode node = objectMapper.getNodeFactory().nullNode();
        MediaType requestBody = openApiOperation.getRequestBody().getContent().get("application/json");

        //Looking for parameter example
        if (schema.getExample() != null) {
            node = SchemaManager.createValueNode(schema.getExample(), objectMapper);
        // If there's no parameter example, then it'll look for a request body example
        } else {
            Object example = null;

            if (requestBody.getExamples() != null) {
                example = ((Example) requestBody.getExamples().values().toArray()[random.nextInt(requestBody.getExamples().values().size())]).getValue();
            } else if (requestBody.getExample() != null) {
                example = requestBody.getExample();
            } else if (requestBody.getSchema().getExample() != null) {
                example = requestBody.getSchema().getExample();
            }

            if (example != null) {
                JsonNode exampleNode = objectMapper.valueToTree(example);
                String[] prefixSplit = prefix.split("\\.");
                JsonNode candidate = null;
                int i = 0;

                while (i < prefixSplit.length) {
                    if (exampleNode.get(prefixSplit[i]) != null) {
                        candidate = exampleNode.get(prefixSplit[i]).isArray()? exampleNode.get(prefixSplit[i]).get(0) : exampleNode.get(prefixSplit[i]);
                        exampleNode = exampleNode.get(prefixSplit[i]);
                        i++;
                    } else if (exampleNode.size() == 1 && exampleNode.get(exampleNode.fieldNames().next()).isArray()) {
                        candidate = exampleNode.get(exampleNode.fieldNames().next());
                    } else {
                        candidate = null;
                        break;
                    }
                }

                if (candidate != null) {
                    node = candidate;
                }
            }
        }

        //If there's no examples, it'll set a default value
        if (node == null || node.isNull()) {
            node = getDefaultValue(schema);
        }

        return node;
    }

    private JsonNode getDefaultValue(Schema<?> schema) {
        JsonNode node;

        switch (schema.getType()) {
            case "integer":
                node = objectMapper.getNodeFactory().numberNode(0);
                break;
            case "number":
                node = objectMapper.getNodeFactory().numberNode(0.0);
                break;
            case "boolean":
                node = objectMapper.getNodeFactory().booleanNode(true);
                break;
            default:
                if ("date".equals(schema.getFormat())) {
                    node = objectMapper.getNodeFactory().textNode("1970-01-01");
                } else if("date-time".equals(schema.getFormat())) {
                    node = objectMapper.getNodeFactory().textNode("1970-01-01T00:00:00Z");
                } else if(schema.getEnum() != null) {
                    String enumValue = (String) schema.getEnum().get(random.nextInt(schema.getEnum().size()));
                    node = objectMapper.getNodeFactory().textNode(enumValue);
                } else {
                    node = objectMapper.getNodeFactory().textNode("");
                }
                break;
        }

        return node;
    }

    @Override
    public String nextValueAsString() {
        String value = null;
        ObjectNode node = nextValue();
        if (node != null) {
            value = node.toPrettyString().replace("-duplicated", "");
        }
        return value;
    }

    public String nextValueAsString(Operation openApiOperation, String operationPath, boolean mutate) {
        setOpenApiOperation(openApiOperation);
        setMutate(mutate);
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

    public void setMutate(boolean mutate) {
        this.mutate = mutate;
    }
}
