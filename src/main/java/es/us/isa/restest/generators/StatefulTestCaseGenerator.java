package es.us.isa.restest.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.perturbation.ObjectPerturbator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.*;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;

public class StatefulTestCaseGenerator extends AbstractTestCaseGenerator {

    private String specDirPath;

    public StatefulTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
        super(spec, conf, nTests);
        this.specDirPath = spec.getPath().substring(0, spec.getPath().lastIndexOf('/'));
    }

    @Override
    protected Collection<TestCase> generateOperationTestCases(Operation testOperation) throws RESTestException {
        List<TestCase> testCases = new ArrayList<>();
        resetOperation();

        boolean fulfillsDependencies = !hasDependencies(testOperation.getOpenApiOperation());

        while (hasNext()) {
            // Create test case with specific parameters and values
            TestCase test = generateNextTestCase(testOperation);

            if(test != null) {
                test.setFulfillsDependencies(fulfillsDependencies);

                // Set authentication data (if any)
                authenticateTestCase(test);

                // Add test case to the collection
                testCases.add(test);

                // Update indexes
                updateIndexes(test);
            }

        }

        return testCases;
    }

    @Override
    public TestCase generateNextTestCase(Operation testOperation) throws RESTestException {
        TestCase tc = null;
        if(nFaulty < (int) (faultyRatio * numberOfTests)) {
            tc = generateFaultyTestCaseDueToIndividualConstraints(testOperation);
        }

        if(tc == null) {
            io.swagger.v3.oas.models.Operation getOperation = spec.getSpecification().getPaths().get(testOperation.getTestPath()).getGet();
            if(!testOperation.getMethod().equalsIgnoreCase("get") && getOperation != null &&
                    FileManager.checkIfExists(this.specDirPath + '/' + getOperation.getOperationId() + "_data.json")) {
                tc = generateStatefulValidTestCase(testOperation, this.specDirPath + '/' + getOperation.getOperationId() + "_data.json");
            } else {
                tc = generateRandomValidTestCase(testOperation);
            }
        }

        return tc;
    }

    private TestCase generateStatefulValidTestCase(Operation testOperation, String jsonPath) throws RESTestException {
        TestCase tc = createTestCaseTemplate(testOperation);
        JsonNode jsonNode = (JsonNode) JSONManager.readJSON(jsonPath);
        boolean perturbation = false;

        if(testOperation.getTestParameters() != null) {
            for (TestParameter testParam : testOperation.getTestParameters()) {
                float randomFloat = rand.nextFloat();
                if (!testParam.getIn().equals("body") && (testParam.getWeight() == null || randomFloat <= testParam.getWeight())) {
                    tc.addParameter(testParam, generateStatefulParameterValue(testParam, jsonNode));
                } else if(testParam.getWeight() == null || randomFloat <= testParam.getWeight()) {
                    String body = generateStatefulRequestBody(jsonNode, testOperation.getOpenApiOperation().getRequestBody().getContent().get("application/json"));
                    if(body == null) {
                        ITestDataGenerator generator = getRandomGenerator(nominalGenerators.get(Pair.with(testParam.getName(), testParam.getIn())));
                        if (generator instanceof ObjectPerturbator) {
                            body = ((ObjectPerturbator) generator).getRandomOriginalStringObject();
                            perturbation = true;
                        } else {
                            body = generator.nextValueAsString();
                        }

                    }
                    tc.addParameter(testParam, body);
                }
            }
        }

        List<String> errors = tc.getValidationErrors(OASAPIValidator.getValidator(spec));
        if (!errors.isEmpty()) {
            throw new RESTestException("The test case generated does not conform to the specification: " + errors);
        }

        if (perturbation) {
            perturbate(tc, testOperation);
        }

        return tc;
    }

    private String generateStatefulParameterValue(TestParameter testParam, JsonNode jsonNode) {
        String value;
        JsonNode valueNode = null;
        JsonNode bodyNode = jsonNode.get(this.rand.nextInt(jsonNode.size()));
        if(bodyNode.hasNonNull(testParam.getName())) {
            valueNode = bodyNode.get(testParam.getName());
        }

        if(valueNode == null || !valueNode.isValueNode()) {
            value = getRandomGenerator(nominalGenerators.get(Pair.with(testParam.getName(), testParam.getIn()))).nextValueAsString();
        } else {
            value = valueNode.asText();
        }

        return value;
    }

    private String generateStatefulRequestBody(JsonNode jsonNode, MediaType requestBody) {
        String body = null;

        if(requestBody != null) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            JsonNode bodyNode = jsonNode.get(this.rand.nextInt(jsonNode.size()));
            generateStatefulObjectNode(bodyNode, requestBody.getSchema(), mapper, rootNode);
            if(!rootNode.isEmpty()) {
                body = rootNode.toPrettyString();
            }
        }

        return body;
    }

    private void generateStatefulObjectNode(JsonNode jsonNode, Schema schema, ObjectMapper mapper, ObjectNode rootNode) {
        if(schema.get$ref() != null) {
            schema = spec.getSpecification().getComponents().getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }
        for(Object o : schema.getProperties().entrySet()) {
            Map.Entry<String, Schema> entry = (Map.Entry<String, Schema>) o;
            JsonNode childNode;
            if(entry.getValue().getType().equals("object")) {
                childNode = mapper.createObjectNode();
                generateStatefulObjectNode(jsonNode, entry.getValue(), mapper, (ObjectNode)childNode);
            } else {
                childNode = jsonNode.findParent(entry.getKey());
                if(childNode != null && childNode.isObject()) {
                    childNode = childNode.get(entry.getKey());
                } else {
                    childNode = null;
                }
            }

            if(childNode == null || childNode.isNull() || childNode.isMissingNode()) {
                rootNode.removeAll();
                break;
            } else {
                rootNode.set(entry.getKey(), childNode);
            }
        }
    }

    @Override
    protected boolean hasNext() {
        return nTests < numberOfTests;
    }
}
