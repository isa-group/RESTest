package es.us.isa.restest.generators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.perturbation.ObjectPerturbator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.SpecificationVisitor;
import org.javatuples.Pair;

import java.util.*;

import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;

/**
 * This class implements a generator of fuzzing test cases
 * @author José Ramón Fernández
 */

public class FuzzingTestCaseGenerator extends AbstractTestCaseGenerator {

    private Map<String, List<String>> fuzzingMap;

    public FuzzingTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) throws JsonProcessingException {
        super(spec, conf, nTests);
        TypeReference<HashMap<String, List<String>>> typeRef = new TypeReference<HashMap<String, List<String>>>() {};
        this.fuzzingMap = new ObjectMapper().readValue(FileManager.readFile("src/main/resources/fuzzing-dictionary.json"), typeRef);
    }

    @Override
    protected Collection<TestCase> generateOperationTestCases(Operation testOperation) {

        List<TestCase> testCases = new ArrayList<>();

        resetOperation();

        boolean fulfillsDependencies = !hasDependencies(testOperation.getOpenApiOperation());

        while (hasNext()) {
            TestCase test = generateNextTestCase(testOperation);
            test.setFulfillsDependencies(fulfillsDependencies);

            authenticateTestCase(test);
            testCases.add(test);
            updateIndexes(test);
        }

        return testCases;
    }

    @Override
    public TestCase generateNextTestCase(Operation testOperation) {
        TestCase tc = createTestCaseTemplate(testOperation);

        if(testOperation.getTestParameters() != null) {
            for(TestParameter testParam : testOperation.getTestParameters()) {
                if(!testParam.getIn().equals("body")) {
                    generateFuzzyParameter(tc, testParam, testOperation);
                } else {
                    generateFuzzyBody(tc, testParam);
                }
            }
        }
        return tc;
    }

    private void generateFuzzyParameter(TestCase tc, TestParameter testParam, Operation testOperation) {
        //TODO: Different fuzzing lists for string formats (URL, email, binary files) and number formats (float, double, int32, int64)
        if(testParam.getWeight() == null || rand.nextFloat() <= testParam.getWeight()) {
            ParameterFeatures param = SpecificationVisitor.findParameter(testOperation.getOpenApiOperation(), testParam.getName(), testParam.getIn());
            List<String> fuzzingList = fuzzingMap.get(param.getType());
            tc.addParameter(testParam, fuzzingList.get(rand.nextInt(fuzzingList.size())));
        }
    }

    private void generateFuzzyBody(TestCase tc, TestParameter testParam) {
        //TODO: Generate fuzzing bodies
        ITestDataGenerator generator = getRandomGenerator(nominalGenerators.get(Pair.with(testParam.getName(), testParam.getIn())));
        if (generator instanceof ObjectPerturbator) {
            tc.addParameter(testParam, ((ObjectPerturbator) generator).getRandomOriginalStringObject());
        } else {
            tc.addParameter(testParam, generator.nextValueAsString());
        }
    }

    @Override
    protected boolean hasNext() {
        return nTests < numberOfTests;
    }
}
