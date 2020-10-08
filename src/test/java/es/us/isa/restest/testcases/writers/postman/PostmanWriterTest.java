package es.us.isa.restest.testcases.writers.postman;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import org.junit.Test;

import java.util.Collection;

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
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Stripe/testConf.yaml", spec);

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
}
