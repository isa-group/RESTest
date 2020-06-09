package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class TravelRandomTestCaseGenerator {

    @Test
    @Ignore        // To avoid the test failing in Travis
    public void travelFullTestCaseGenerator() {

        // Load specification
        String OAISpecPath = "src/test/resources/Travel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Travel/testConf.yaml");

        // Set number of test cases to be generated on each path
        int numTestCases = 20;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(0.2f);

        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 120, testCases.size());

        // Write test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "TravelTest", "restassured", basePath);
        writer.setOAIValidation(true);
        writer.write(testCases);
    }




}
