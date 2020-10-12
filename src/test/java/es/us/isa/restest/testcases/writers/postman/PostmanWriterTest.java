package es.us.isa.restest.testcases.writers.postman;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.postman.pojos.PostmanCollectionObject;
import es.us.isa.restest.util.RESTestException;
import io.swagger.v3.oas.models.PathItem;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class PostmanWriterTest {

    @Test
    public void generateTestCasesAndExportTest() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Travel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Travel/testConf.yaml", spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 4;

        // Faulty ratio
        float faultyRatio = 0.5f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);

        // Generate test cases
        Collection<TestCase> testCases = generator.generate();

        // Create PostmanWriter
        PostmanWriter postmanWriter = new PostmanWriter(spec.getSpecification().getServers().get(0).getUrl());
        postmanWriter.setJsonPath("src/test/resources/restest-test-resources");
        postmanWriter.setCollectionName("test_collection");

        postmanWriter.write(testCases);
    }

    @Test
    public void generateTestCasesWithUrlencodedAndExportTest() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Stripe/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Stripe/testConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 8;

        // Faulty ratio
        float faultyRatio = 0.5f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);

        // Generate test cases
        Collection<TestCase> testCases = generator.generate();

        // Create PostmanWriter
        PostmanWriter postmanWriter = new PostmanWriter(spec.getSpecification().getServers().get(0).getUrl());
        postmanWriter.setJsonPath("src/test/resources/restest-test-resources");
        postmanWriter.setCollectionName("test_collection_urlencoded");

        postmanWriter.write(testCases);
    }

    @Test
    public void compareGeneratedTestSuitesTest() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        PostmanCollectionObject originalTestSuite = objectMapper.readValue(
                new File("src/test/resources/restest-test-resources/compare1.postman_collection.json"),
                PostmanCollectionObject.class
        );

        TestCase tc1 = new TestCase("test1", false, "op1", "/example1", PathItem.HttpMethod.GET);
        tc1.addQueryParameter("offset", "10");

        TestCase tc2 = new TestCase("test2", true, "op2", "/example2/{example_N}", PathItem.HttpMethod.POST);
        tc2.addPathParameter("example_N", "example3");
        tc2.addQueryParameter("ok", "true");
        tc2.addFormParameter("urlparam1", "hey");
        tc2.addFormParameter("urlparam2", "hoy");
        tc2.addHeaderParameter("Authorization", "Bearer example");

        PostmanWriter postmanWriter = new PostmanWriter("http://localhost:8080/api/v1");
        postmanWriter.setCollectionName("compare2");
        postmanWriter.setJsonPath("src/test/resources/restest-test-resources");

        postmanWriter.write(Arrays.asList(tc1, tc2));

        PostmanCollectionObject generatedTestSuite = objectMapper.readValue(
                new File("src/test/resources/restest-test-resources/compare2.postman_collection.json"),
                PostmanCollectionObject.class
        );

        generatedTestSuite.setInfo(originalTestSuite.getInfo());

        assertEquals("The generated test suite should be equal to the original one", originalTestSuite, generatedTestSuite);
    }
}
