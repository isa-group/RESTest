package es.us.isa.rester.generators;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.configuration.TestConfigurationIO;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.testcases.TestCase;
import es.us.isa.rester.testcases.writters.RESTAssuredWritter;
import es.us.isa.rester.util.TestConfigurationFilter;

public class SimpleAPIFullTestCaseGenerator {

    @Test
    public void simpleAPIFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/main/resources/SimpleAPI/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/main/resources/SimpleAPI/fullConf.yaml");

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

        List<TestConfigurationFilter> filters = new ArrayList<>();
        TestConfigurationFilter filter = new TestConfigurationFilter();
        filter.setPath(null);
        filter.addAllMethods();
        filters.add(filter);

        Collection<TestCase> testCases = generator.generate(filters);

        assertEquals("Incorrect number of test cases", 15, testCases.size());

        // Write RESTAssured test cases
        RESTAssuredWritter writer = new RESTAssuredWritter();
        writer.setOAIValidation(true);
        writer.setLogging(true);
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        writer.write(OAISpecPath, "src/generation/java", "SimpleAPI", null, basePath.toLowerCase(), testCases);

    }
}
