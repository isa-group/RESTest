package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.OASAPIValidator;
import es.us.isa.restest.util.RESTestException;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConstraintBasedTestCaseGeneratorTest {

	
	// BIKEWISE

    @Test
    public void bikewiseFullTestCaseGeneratorNoConstraints() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        String testConf = "src/test/resources/Bikewise/fullConf.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);

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
    public void bikewiseFullTestCaseGeneratorNoConstraintsWithFaults() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Bikewise/fullConf.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;
        
        // Faulty ratio
        float faultyRatio = 0.2f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

        Collection<TestCase> testCases = generator.generate();
        
		// Expected results
		int expectedNumberOfTestCases = 20;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio*expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

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
    }

	@Test
	public void memesTestCaseGeneratorWithInvalidGeneratorWithoutFaults() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Memes/swagger_forTestSuite.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration("src/test/resources/Memes/testConf_forTestSuite.yaml", spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 0f;

		// Create generator and filter
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();


		// Expected results
		int expectedNumberOfTestCases = 10;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio * expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
		assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));
	}

	@Test
	public void memesTestCaseGeneratorWithInvalidGeneratorWithFaultsParameters() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Memes/swagger_forTestSuite.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration("src/test/resources/Memes/testConf_forTestSuite.yaml", spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 1f;
		float faultyDependencyRatio = 0f;

		// Create generator and filter
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);
		generator.setFaultyDependencyRatio(faultyDependencyRatio);

		Collection<TestCase> testCases = generator.generate();


		// Expected results
		int expectedNumberOfTestCases = 10;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio * expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());

		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());

		for (TestCase tc: testCases)
			if (tc.getPath().equals("/gallery/{id}"))
				assertEquals("Incorrect parameter value", "invalid_value", tc.getPathParameters().get("id"));
	}

	@Test
	public void memesTestCaseGeneratorWithInvalidGeneratorWithFaultsDependencies() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Memes/swagger_forTestSuite.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration("src/test/resources/Memes/testConf_forTestSuite.yaml", spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 1f;
		float faultyDependencyRatio = 1f;

		// Create generator and filter
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);
		generator.setFaultyDependencyRatio(faultyDependencyRatio);

		Collection<TestCase> testCases = generator.generate();


		// Expected results
		int expectedNumberOfTestCases = 10;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio * expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec)); // According to OAS, all valid

		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
		assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", 0, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));

		for (TestCase tc: testCases)
			if (tc.getPath().equals("/gallery/{id}"))
				assertEquals("Incorrect parameter value", "invalid_value", tc.getPathParameters().get("id"));
	}

	@Test
	public void memesTestCaseGeneratorWithInvalidGeneratorWithSomeFaultsParametersDependencies() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Memes/swagger_forTestSuite.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration("src/test/resources/Memes/testConf_forTestSuite.yaml", spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 0.8f;
		float faultyDependencyRatio = 0.5f;

		// Create generator and filter
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);
		generator.setFaultyDependencyRatio(faultyDependencyRatio);

		Collection<TestCase> testCases = generator.generate();


		// Expected results
		int expectedNumberOfTestCases = 10;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio * expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());

		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", 8, GeneratorTestHelper.numberOfValidTestCases(testCases, spec)); // 2 valid, 4 with invalid data gen, and 2 violating deps

		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
		assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", 2, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));

		for (TestCase tc: testCases)
			if (tc.getPath().equals("/gallery/{id}") && tc.getFaulty())
				assertEquals("Incorrect parameter value", "invalid_value", tc.getPathParameters().get("id"));
	}
    
    
    // SPOTIFY
    
	@Test
	public void spotifyGetArtistWithFilterAndFaults() throws RESTestException {


		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/defaultConf.json", spec);

		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
        // Faulty ratio
        float faultyRatio = 0.5f;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/artists");
		filter.addGetMethod();
		filters.add(filter);

		Collection<TestCase> testCases = generator.generate(filters);


		// Expected results
		int expectedNumberOfTestCases = numTestCases;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio*expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

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

	}

 
	// AMADEUS
	
    @Test
    public void amadeusHotelFullTestCaseGenerator() throws RESTestException {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        String testConf = "src/test/resources/AmadeusHotel/defaultConf.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 4;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);


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

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "AmadeusHotelTest", "restassured", basePath.toLowerCase(), false);
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
	
    @Test
    public void amadeusHotelFullTestCaseGeneratorWithFaults() throws RESTestException {
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
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);
		((ConstraintBasedTestCaseGenerator)generator).setFaultyDependencyRatio(faultyDependencyRatio);


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
    

    // COMMENTS
    
	@Test
	public void commentsFullTestCaseGeneratorWithPerturbationAndFaults() throws RESTestException {
		
		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		String testConf = "src/test/resources/Comments/testConf_forTestSuite3.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf, spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
        // Faulty ratio
        float faultyRatio = 0.1f;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 30;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio*expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		
		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
		assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsTest", "restassured", basePath, true);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
	
	@Test
	public void commentsConstraintBasedTestCaseGeneratorWithFaults() throws RESTestException {
		
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
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
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
		assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfValidTestCases, testCases.stream().filter(c -> c.getFaulty() != null && !c.getFaulty()).count());
		assertTrue("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCases <= GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		
		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty() == null || c.getFaulty()).count());
		assertTrue("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases >= GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));

		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsConstraintBasedTest", "restassured", basePath, true);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
	
	
	@Test
	public void commentsConstraintBasedTestCaseGeneratorWithFiltersAndFaults() throws RESTestException {
		
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
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyDependencyRatio(faultyDependencyRatio);
		generator.setFaultyRatio(faultyRatio);

		
		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/comments");
		filter.addGetMethod();
		filters.add(filter);
		
		
		Collection<TestCase> testCases = generator.generate(filters);
		
		// Expected results
		int expectedNumberOfTestCases = 10;
		int expectedNumberOfInvalidTestCases = (int) (expectedNumberOfTestCases * faultyRatio);
		int expectedNumberOfInvalidTestCasesDueToDependencies = (int) (expectedNumberOfTestCases * faultyDependencyRatio * faultyDependencyRatio);
		int expectedNumberOfInvalidTestCasesDueToIndividualConstraints = expectedNumberOfInvalidTestCases - expectedNumberOfInvalidTestCasesDueToDependencies;
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;

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
		assertEquals("Incorrect number of faulty test cases generated due to dependency violations", expectedNumberOfInvalidTestCasesDueToDependencies, generator.getnFaultyTestDueToDependencyViolations());
		assertEquals("Incorrect number of faulty test cases generated due to individual constraints", expectedNumberOfInvalidTestCasesDueToIndividualConstraints, generator.getnFaultyTestsDueToIndividualConstraint());
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsConstraintBasedTest", "restassured", basePath, false);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
}
