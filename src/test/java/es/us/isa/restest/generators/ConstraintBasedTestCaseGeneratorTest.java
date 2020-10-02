package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ConstraintBasedTestCaseGeneratorTest {

	
	// NO CONSTRAINTS

    @Test
    public void bikewiseFullTestCaseGeneratorNoConstraints() {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Bikewise/fullConf.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 20, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "BikewiseTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
    @Test
    public void bikewiseFullTestCaseGeneratorNoConstraintsWithFaults() {
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
        

        assertEquals("Incorrect number of test cases", 20, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
        assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());
    }
    
    
	@Test
	public void spotifyGetArtistWithFilterAndFaults() {


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

		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
        assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());

	}

 
	
	// CONSTRAINT-BASED TEST CASE GENERATION. NO FILTERS. NO FAULTS
	
    @Test
    public void amadeusHotelFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/AmadeusHotel/defaultConf.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 4;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);


        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 4, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "AmadeusHotelTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }

	@Test
	public void commentsFullTestCaseGeneratorWithPerturbationAndFaults() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Comments/testConf_forTestSuite3.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
        // Faulty ratio
        float faultyRatio = 0.1f;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();
		
		assertEquals("Incorrect number of test cases", 30, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
        assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "CommentsTest", "restassured", basePath);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
	
	// CONSTRAINT-BASED TEST CASE GENERATION WITH FAULTS. NO FILTERS
	
	
    @Test
    public void amadeusHotelFullTestCaseGeneratorWithFaults() {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/AmadeusHotel/defaultConf.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 40;
        
        // Faulty ratio
        float faultyRatio = 0.2f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);


        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 40, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
        assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "AmadeusHotelTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
	
	
	
	
	
	
	/*
	
	@Test
	public void commentsConstraintBasedTestCaseGeneratorWithFilters() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Comments/testConf.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
		// Create generator and filter
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();
		
		assertEquals("Incorrect number of test cases", 40, testCases.size());
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "CommentsConstraintBasedTest", "restassured", basePath);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
	
	*/
	
	
	/*
	@Test
	public void commentsConstraintBasedTestCaseGeneratorWithFilters() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Comments/testConf.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
		// Create generator and filter
		ConstraintBasedTestCaseGenerator generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyDependencyRatio(0.1f);
		generator.setFaultyRatio(0.5f);

		Collection<TestCase> testCases = generator.generate();
		
		assertEquals("Incorrect number of test cases", 40, testCases.size());
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "CommentsConstraintBasedTest", "restassured", basePath);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
	
	*/
}
