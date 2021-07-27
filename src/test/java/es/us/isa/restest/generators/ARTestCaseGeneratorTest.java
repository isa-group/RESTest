package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.RESTestException;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class ARTestCaseGeneratorTest {

    //BIKEWISE

    @Test
    public void bikewiseARTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        String testConf = "src/test/resources/Bikewise/fullConf.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        ARTestCaseGenerator generator = new ARTestCaseGenerator(spec, conf, numTestCases);
        generator.setDiversity("LEVENSHTEIN");

        Collection<TestCase> testCases = generator.generate();

        // Expected results
        int expectedNumberOfTestCases = 20;

        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
        assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "BikewiseTest", "restassured", basePath.toLowerCase(), false);
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);
    }

    @Test
    public void bikewiseARTestCaseGeneratorWithoutFaults() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        String testConf = "src/test/resources/Bikewise/fullConf.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        ARTestCaseGenerator generator = new ARTestCaseGenerator(spec, conf, numTestCases);
        generator.setDiversity("LEVENSHTEIN");
        generator.setFaultyRatio(0f);

        Collection<TestCase> testCases = generator.generate();

        // Expected results
        int expectedNumberOfTestCases = 20;

        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
        assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "BikewiseTest", "restassured", basePath.toLowerCase(), false);
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);
    }

    // COMMENTS

    @Test
    public void commentsARTestCaseGeneratorWithFaults() throws RESTestException {

        // Load specification
        String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
        String testConf = "src/test/resources/Comments/testConf.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 10;

        // Faulty ratio
        float faultyRatio = 0.5f;
        float faultyDependencyRatio= 0.1f;

        // Create generator and filter
        ARTestCaseGenerator generator = new ARTestCaseGenerator(spec, conf, numTestCases);
        generator.setDiversity("LEVENSHTEIN");
        generator.setFaultyDependencyRatio(faultyDependencyRatio);
        generator.setFaultyRatio(faultyRatio);

        Collection<TestCase> testCases = generator.generate();

        // Expected results
        int expectedNumberOfTestCases = 40;
        int expectedNumberOfInvalidTestCases = 15;
        int expectedNumberOfValidTestCases = 25;

        // Total number of test cases
        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
        assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

        // Invalid test cases
        assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
        assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
        assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));


        // Write test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsConstraintBasedTest", "restassured", basePath, true);
        writer.setOAIValidation(true);
        writer.write(testCases);
    }

    // AMADEUS HOTEL

    @Test
    public void amadeusHotelARTestCaseGeneratorWithFaults() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        String testConf = "src/test/resources/AmadeusHotel/defaultConf.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 40;

        // Faulty ratio
        float faultyRatio = 0.2f;

        // Faulty dependency ratio
        float faultyDependencyRatio = 0.5f;

        // Create generator and filter
        ARTestCaseGenerator generator = new ARTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);
        generator.setFaultyDependencyRatio(faultyDependencyRatio);
        generator.setDiversity("LEVENSHTEIN");
        generator.setNumberOfCandidates(10);


        Collection<TestCase> testCases = generator.generate();

        // Expected results
        int expectedNumberOfTestCases = 40;
        int expectedNumberOfInvalidTestCases = (int) (faultyRatio*expectedNumberOfTestCases);
        int expectedNumberOfInvalidTestCasesDueToDependencies = (int) (faultyRatio * faultyDependencyRatio * expectedNumberOfTestCases);
        int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;
        int expectedNumberOfValidTestCasesAccordingToValidator = expectedNumberOfTestCases - (expectedNumberOfInvalidTestCases - expectedNumberOfInvalidTestCasesDueToDependencies);

        // Total number of test cases
        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
        assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCasesAccordingToValidator, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

        // Invalid test cases
        assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
        assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
        assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases - expectedNumberOfInvalidTestCasesDueToDependencies, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "AmadeusHotelTest", "restassured", basePath.toLowerCase(), false);
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
}
