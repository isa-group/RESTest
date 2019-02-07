package es.us.isa.rester.generators;

import static org.junit.Assert.*;

import java.util.Collection;

import org.junit.Test;

import es.us.isa.rester.configuration.TestConfigurationIO;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.testcases.TestCase;
import es.us.isa.rester.testcases.writters.RESTAssuredWritter;

public class DataAtWorkFullTestCaseGeneratorTest {

    @Test
    public void dataAtWorkFullTestCaseGeneratorTest() {
        // Load specification
        String OAISpecPath = "src/main/resources/DataAtWork/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/main/resources/DataAtWork/fullConf.yaml");

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 1;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 13, testCases.size());

        // Write RESTAssured test cases
        RESTAssuredWritter writer = new RESTAssuredWritter();
        writer.setOAIValidation(true);
        writer.setLogging(true);
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        writer.write(OAISpecPath, "src/generation/java", "DataAtWork", null, basePath.toLowerCase(), testCases);

    }
}
