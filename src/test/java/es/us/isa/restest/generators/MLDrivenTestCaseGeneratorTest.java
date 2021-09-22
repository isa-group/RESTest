package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.RESTestException;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static es.us.isa.restest.util.FileManager.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MLDrivenTestCaseGeneratorTest {

	// AMADEUS
	
    @Test
    public void amadeusHotelFullTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        String testConf = "src/test/resources/AmadeusHotel/defaultConf.yaml";
        String csvTmpTcDirPath = "target/test-data/restassured/";
        String csvTmpTcFilePath = "test-cases_tmp.csv";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Create tmpCsv dir
        createDir(csvTmpTcDirPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 4;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new MLDrivenTestCaseGenerator(spec, conf, numTestCases);
        ((MLDrivenTestCaseGenerator) generator).setCsvTmpTcPath(csvTmpTcDirPath + csvTmpTcFilePath);


        Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 4;
        
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Uncomment to see validation errors
		/*
		for(TestCase tc: testCases)  {
			List<String> errors = tc.getValidationErrors(OASAPIValidator.getValidator(spec));
			System.out.println("Validation: " + errors);
		}
		*/
			
		// Valid test cases
		
		assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());

		// Temporary CSV file
		assertTrue(checkIfExists(((MLDrivenTestCaseGenerator) generator).getCsvTmpTcPath()));
		deleteFile(((MLDrivenTestCaseGenerator) generator).getCsvTmpTcPath());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "AmadeusHotelTest", "restassured", basePath.toLowerCase(), false);
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
	
    @Test
    @Ignore // Stop ignoring when MLDrivenTestCaseGenerator is implemented
    public void amadeusHotelFullTestCaseGeneratorWithFaults() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        String testConf = "src/test/resources/AmadeusHotel/defaultConf.yaml";
        String csvTmpTcDirPath = "target/test-data/restassured/";
        String csvTmpTcFilePath = "test-cases_tmp.csv";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Create tmpCsv dir
        createDir(csvTmpTcDirPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 40;
        
        // Faulty ratio
        float faultyRatio = 0.2f;

		// Faulty dependency ratio
		float faultyDependencyRatio = 0.5f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new MLDrivenTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);
        ((MLDrivenTestCaseGenerator) generator).setCsvTmpTcPath(csvTmpTcDirPath + csvTmpTcFilePath);

        Collection<TestCase> testCases = generator.generate();
        
		// Expected results
		int expectedNumberOfTestCases = 40;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio*expectedNumberOfTestCases);
		int expectedNumberOfInvalidTestCasesDueToDependencies = expectedNumberOfInvalidTestCases;
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;
		int expectedNumberOfValidTestCasesAccordingToValidator = expectedNumberOfTestCases;

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
