package es.us.isa.restest.inputs.stateful;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.*;

import static es.us.isa.restest.inputs.fuzzing.FuzzingDictionary.getNodeFuzzingValue;
import static es.us.isa.restest.inputs.stateful.DataMatching.getParameterValue;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.SchemaManager.resolveSchema;


public class BodyGenerator implements ITestDataGenerator {

    String operationMethod;
    String operationPath;
    Operation openApiOperation;
    String defaultValue;
    boolean mutate;

    String dataDirPath;
    OpenAPISpecification spec;

    Random random;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LogManager.getLogger(BodyGenerator.class);
    private static final String DOT_CONVERSION = "(dot)";

    public BodyGenerator() {
        this.random = new SecureRandom();
    }

    @Override
    public JsonNode nextValue() {
        JsonNode body = null;
        String jsonPath = this.dataDirPath + "/stateful_data.json";

        if (!checkIfExists(jsonPath) && defaultValue != null) {
            try {
                return objectMapper.readTree(defaultValue);
            } catch (Exception e) {
                logger.warn("The defaultValue used for the body of {}{} is not a valid JSON", operationMethod, operationPath);
            }
        }

        ObjectNode dictNode = operationPath != null && FileManager.checkIfExists(jsonPath)? (ObjectNode) JSONManager.readJSON(jsonPath) : objectMapper.createObjectNode();
        MediaType requestBody = openApiOperation.getRequestBody().getContent().get("application/json");

        if (requestBody != null) {
            Schema mutatedSchema = mutate? new SchemaMutation(requestBody.getSchema(), spec.getSpecification()).mutate() : resolveSchema(requestBody.getSchema(), spec.getSpecification());
            JsonNode rootNode = null;
            if ("array".equals(mutatedSchema.getType()))
                rootNode = objectMapper.createArrayNode();
            else
                rootNode = objectMapper.createObjectNode();
            try {
                generateStatefulObjectNode(dictNode, mutatedSchema, rootNode, "", new ArrayList<>(), true);
            } catch (RESTestException e) {
                logger.warn("There isn't enough data to generate a valid request body for {} operation.", operationMethod+operationPath);
                logger.warn("RESTest will use the default request body specified in the testConf.");
            }
            if (rootNode != null) {
                body = rootNode;
            }
        }

        return body;
    }

    private void generateStatefulObjectNode(ObjectNode dictNode, Schema<?> schema, JsonNode rootNode, String prefix, List<String> requiredProperties, boolean firstLevel) throws RESTestException {
        if (schema.get$ref() != null) {
            schema = spec.getSpecification().getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }

        String resolvedProperty = prefix.substring(prefix.lastIndexOf('.') + 1).replace("-duplicated", "").replace(DOT_CONVERSION, ".");

        if ("".equals(prefix) // First level object
                || requiredProperties == null && rootNode.isArray() // Array has no req. properties, but generate at least one
                || (requiredProperties != null && requiredProperties.contains(resolvedProperty)) // Req. property
                || ((requiredProperties == null || !requiredProperties.contains(resolvedProperty)) && random.nextBoolean())) { // Optional property (50% prob.)
            JsonNode childNode = null;
            if (schema.getType().equals("object")) {
                childNode = "".equals(prefix) && firstLevel ? rootNode : objectMapper.createObjectNode();

                if (schema.getProperties() != null) {
                    for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                        String paramName = entry.getKey().replace(".", DOT_CONVERSION);
                        String newPrefix = "".equals(prefix) ? prefix + paramName : prefix + '.' + paramName;
                        generateStatefulObjectNode(dictNode, entry.getValue(), childNode, newPrefix, schema.getRequired(), false);
                    }
                }

            } else if (schema.getType().equals("array")) {
                childNode = "".equals(prefix) && firstLevel ? rootNode : objectMapper.createArrayNode();
                if (schema instanceof ArraySchema && ((ArraySchema) schema).getItems() != null) {
                    generateStatefulObjectNode(dictNode, ((ArraySchema) schema).getItems(), childNode, prefix, schema.getRequired(), false);
                }
            } else {
                String resolvedPrefix = prefix.replace("-duplicated", "").replace(DOT_CONVERSION, ".");
                childNode = getParameterValue(dictNode, operationMethod, operationPath, resolvedPrefix);
                if (childNode == null) {
                    childNode = createNodeFromExample(schema, resolvedPrefix);
                }
            }

            if (!"".equals(prefix) || !firstLevel) {
                if (childNode == null || childNode.isMissingNode()) {
                    throw new RESTestException();
                } else if (rootNode.isObject()) {
                    ((ObjectNode) rootNode).set(prefix.substring(prefix.lastIndexOf('.') + 1).replace(DOT_CONVERSION, "."), childNode);
                } else {
                    ((ArrayNode) rootNode).add(childNode);
                }
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
        JsonNode node = getNodeFuzzingValue(schema.getType());

        // For dates and enums in particular, we may generate valid default values
        if ("date".equals(schema.getFormat()) && random.nextBoolean()) {
            node = objectMapper.getNodeFactory().textNode("2020-01-01");
        } else if("date-time".equals(schema.getFormat()) && random.nextBoolean()) {
            node = objectMapper.getNodeFactory().textNode("2020-01-01T12:00:00Z");
        } else if(schema.getEnum() != null && random.nextBoolean()) {
            String enumValue = (String) schema.getEnum().get(random.nextInt(schema.getEnum().size()));
            node = objectMapper.getNodeFactory().textNode(enumValue);
        }

        return node;
    }

    @Override
    public String nextValueAsString() {
        String value = null;
        JsonNode node = nextValue();
        if (node != null) {
            value = node.toString().replace("-duplicated", "");
        }
        return value;
    }

    public String nextValueAsString(boolean mutate) {
        setMutate(mutate);
        return nextValueAsString();
    }

    public void setDataDirPath(String dataDirPath) {
        this.dataDirPath = dataDirPath;
    }

    public void setOperation(String operationMethod, String operationPath) {
        this.operationMethod = operationMethod;
        this.operationPath = operationPath;
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
