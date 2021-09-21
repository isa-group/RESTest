package es.us.isa.restest.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.perturbation.ObjectPerturbator;
import es.us.isa.restest.inputs.random.RandomInputValueIterator;
import es.us.isa.restest.inputs.random.RandomStringGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;

import java.util.*;

import static es.us.isa.restest.inputs.fuzzing.FuzzingDictionary.getFuzzingValues;
import static es.us.isa.restest.inputs.fuzzing.FuzzingDictionary.getNodeFromValue;
import static es.us.isa.restest.util.SchemaManager.resolveSchema;

/**
 * This class implements a generator of fuzzing test cases. It uses a customizable dictionary to obtain
 * fuzzing parameters. Those parameters are classified by type (string, integer, number, boolean).
 *
 * @author José Ramón Fernández
 */

public class FuzzingTestCaseGenerator extends AbstractTestCaseGenerator {

    private final ITestDataGenerator commonFuzzingGenerator; // Random strings to be used for all parameters
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static Logger logger = LogManager.getLogger(FuzzingTestCaseGenerator.class.getName());

    public FuzzingTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
        super(spec, conf, nTests);
        commonFuzzingGenerator = new RandomStringGenerator(10, 20, true, true, true);
    }

    @Override
    protected Collection<TestCase> generateOperationTestCases(Operation testOperation) {

        List<TestCase> testCases = new ArrayList<>();

        resetOperation();

        // Set up generators for each parameter
        if (testOperation.getTestParameters() != null) {
            for (TestParameter testParam : testOperation.getTestParameters()) {
                if (!testParam.getIn().equals("body")) {
                    ParameterFeatures param = SpecificationVisitor.findParameterFeatures(testOperation.getOpenApiOperation(), testParam.getName(), testParam.getIn());
                    List<String> fuzzingList = getFuzzingValues(param.getType());
                    if (param.getEnumValues() != null)
                        fuzzingList.addAll(param.getEnumValues());
                    ITestDataGenerator generator = new RandomInputValueIterator<>(fuzzingList);
                    nominalGenerators.replace(Pair.with(testParam.getName(), testParam.getIn()), Arrays.asList(generator, commonFuzzingGenerator));
                }
            }
        }

        while (hasNext()) {
            TestCase test = generateNextTestCase(testOperation);
            test.setFulfillsDependencies(false);
            test.setFaulty(false);

            authenticateTestCase(test);
            testCases.add(test);
            updateIndexes(test);
        }

        return testCases;
    }

    @Override
    public TestCase generateNextTestCase(Operation testOperation) {
        TestCase tc = createTestCaseTemplate(testOperation);

        if (testOperation.getTestParameters() != null) {
            for (TestParameter testParam : testOperation.getTestParameters()) {
                if (testParam.getWeight() == null || rand.nextFloat() <= testParam.getWeight()) {
                    if (!testParam.getIn().equals("body")) {
                        tc.addParameter(testParam, nominalGenerators.get(Pair.with(testParam.getName(), testParam.getIn())).get(rand.nextInt(2)).nextValueAsString());
                    } else {
                        generateFuzzingBody(tc, testParam, testOperation);
                    }
                }
            }
        }
        return tc;
    }

    private void generateFuzzingBody(TestCase tc, TestParameter testParam, Operation testOperation) {
        MediaType requestBody = testOperation.getOpenApiOperation().getRequestBody().getContent().get("application/json");

        if (requestBody != null) {
            JsonNode node = null;
            Schema schema = resolveSchema(requestBody.getSchema(), spec.getSpecification());
            if ("array".equals(schema.getType()))
                node = objectMapper.createArrayNode();
            else
                node = objectMapper.createObjectNode();
            generateFuzzingBody(schema, node, schema.getRequired());
            tc.addParameter(testParam, node.toString());
        } else {
            ITestDataGenerator generator = getRandomGenerator(nominalGenerators.get(Pair.with(testParam.getName(), testParam.getIn())));
            if (generator instanceof ObjectPerturbator) {
                tc.addParameter(testParam, ((ObjectPerturbator) generator).getRandomOriginalStringObject());
            } else {
                tc.addParameter(testParam, generator.nextValueAsString());
            }
        }
    }

    private void generateFuzzingBody(Schema schema, JsonNode rootNode, List<String> requiredProperties) {
        if (schema.get$ref() != null) {
            schema = spec.getSpecification().getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }

        Set<Map.Entry> entries = new HashSet<>();
        if (schema instanceof ObjectSchema)
            entries.addAll(schema.getProperties().entrySet());
        else if (schema instanceof ArraySchema)
            entries.add(new AbstractMap.SimpleEntry<>(null, ((ArraySchema) schema).getItems()));

        for (Object o : entries) {
            Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) o;
            if (requiredProperties == null && rootNode.isArray() // Array has no req. properties, but generate at least one
                    || (requiredProperties != null && requiredProperties.contains(entry.getKey())) // Req. property
                    || ((requiredProperties == null || !requiredProperties.contains(entry.getKey())) && rand.nextBoolean())) { // Optional property (50% prob.)
                JsonNode childNode = null;
                if ("object".equals(entry.getValue().getType())) {
                    childNode = objectMapper.createObjectNode();
                    generateFuzzingBody(entry.getValue(), childNode, entry.getValue().getRequired());
                } else if ("array".equals(entry.getValue().getType())) {
                    childNode = objectMapper.createArrayNode();
                    generateFuzzingBody(entry.getValue(), childNode, entry.getValue().getRequired());
                } else {
                    childNode = createValueNode(entry.getValue());
                }

                if (childNode != null && !childNode.isMissingNode()) {
                    if (rootNode.isObject()) {
                        ((ObjectNode) rootNode).set(entry.getKey(), childNode);
                    } else {
                        ((ArrayNode) rootNode).add(childNode);
                    }
                }
            }
        }
    }

    private JsonNode createValueNode(Schema schema) {
        List<String> fuzzingList = getFuzzingValues(schema.getType());
        if (schema.getEnum() != null)
            fuzzingList.addAll(schema.getEnum());
        fuzzingList.add(commonFuzzingGenerator.nextValueAsString());

        // For dates in particular, we may generate valid default values
        if ("date".equals(schema.getFormat())) {
            fuzzingList.add("2020-01-01");
        } else if("date-time".equals(schema.getFormat())) {
            fuzzingList.add("2020-01-01T12:00:00Z");
        }

        String value = fuzzingList.get(rand.nextInt(fuzzingList.size()));
        return getNodeFromValue(value);
    }

    @Override
    protected boolean hasNext() {
        return nTests < numberOfTests;
    }
}
