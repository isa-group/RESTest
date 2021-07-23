package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import org.junit.Test;

import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FuzzingTestCaseGeneratorTest {

    @Test
    public void bikewiseFuzzingTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Bikewise/fullConf.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        FuzzingTestCaseGenerator gen = new FuzzingTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = gen.generate();

        int expectedNumberOfTestCases = 20;
        int expectedNumberOfValidTestCases = 20;
        int expectedNumberOfInvalidTestCases = 0;

        // Total number of test cases
        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, gen.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());

        // Invalid test cases
        assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, gen.getnFaulty());
        assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
    }

    @Test
    public void commentsFuzzingTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Comments/testConf_forTestSuite3.yaml", spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 10;

        FuzzingTestCaseGenerator gen = new FuzzingTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = gen.generate();

        int expectedNumberOfTestCases = 30;
        int expectedNumberOfInvalidTestCases = 0;
        int expectedNumberOfValidTestCases = 30;

        // Total number of test cases
        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, gen.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());

        // Invalid test cases
        assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, gen.getnFaulty());
        assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());


    }

    @Test
    public void travelFuzzingTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Travel/swagger_betty_test.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Travel/testConf_betty_test.yaml", spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 5;

        FuzzingTestCaseGenerator gen = new FuzzingTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = gen.generate();

        int expectedNumberOfTestCases = 30;
        int expectedNumberOfInvalidTestCases = 0;
        int expectedNumberOfValidTestCases = 30;

        // Total number of test cases
        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, gen.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());

        // Invalid test cases
        assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, gen.getnFaulty());
        assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());


    }

    @Test
    public void comments2FuzzingTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Comments/swagger_forTestSuite6.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Comments/testConf_forTestSuite6.yaml", spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 5;

        FuzzingTestCaseGenerator gen = new FuzzingTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = gen.generate();

        int expectedNumberOfTestCases = 30;
        int expectedNumberOfInvalidTestCases = 0;
        int expectedNumberOfValidTestCases = 30;

        // Total number of test cases
        assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

        // Valid test cases
        assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, gen.getnNominal());
        assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());

        // Invalid test cases
        assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, gen.getnFaulty());
        assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());

        Collection<TestCase> testCasesCommentsMultiplePath = testCases.stream().filter(tc -> tc.getPath().equals("/comments/multiple")).collect(Collectors.toList());

        assertEquals(5, testCasesCommentsMultiplePath.size());

        testCasesCommentsMultiplePath.forEach(tc -> {
            assertTrue("The generated body should be an array of objects",
                    tc.getBodyParameter().matches("^\\[\\{\".*userName.*text.*}]"));
        });
    }
}
