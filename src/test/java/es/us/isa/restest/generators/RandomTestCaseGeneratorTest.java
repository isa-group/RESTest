package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;

import org.junit.Ignore;
import org.junit.Test;

import static es.us.isa.restest.coverage.CriterionType.AUTHENTICATION;
import static es.us.isa.restest.coverage.CriterionType.OPERATION;
import static es.us.isa.restest.coverage.CriterionType.PARAMETER;
import static es.us.isa.restest.coverage.CriterionType.PARAMETER_VALUE;
import static es.us.isa.restest.coverage.CriterionType.PATH;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RandomTestCaseGeneratorTest {

	
	// RANDOM TEST CASE GENERATION. NO FILTERS. NO FAULTS
	
    @Test
    public void iceAndFireFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/AnApiOfIceAndFire/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration (empty)
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/AnApiOfIceAndFire/testConf.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 1;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);


        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 0, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "IAFTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
    
    @Test
    public void amadeusHotelFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/AmadeusHotel/defaultConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 4;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);


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
    public void bikewiseFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Bikewise/fullConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

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
    public void dataAtWorkFullTestCaseGeneratorTest() {
        // Load specification
        String OAISpecPath = "src/test/resources/DataAtWork/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/DataAtWork/fullConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 1;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 13, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "DataAtWorkTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
    @Test
    public void foursquareFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/Foursquare/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Foursquare/testConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 10;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);


        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 10, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "FoursquareTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
    @Test
    public void marvelFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/Marvel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Marvel/testConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 4;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);


        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 24, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "MarvelTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
	@Test
	public void playlistFullTestCaseGenerator() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/Playlist/spec.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Playlist/defaultConf_forTestSuite.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 3;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();
		
		assertEquals("Incorrect number of test cases", 30, testCases.size());
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "PlaylistTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);	
		}
	
	
    @Test
    public void simpleAPIFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/SimpleAPI/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/SimpleAPI/fullConfRight.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 15, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SimpleAPITest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
    
    @Test
    public void tumblrFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/Tumblr/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Tumblr/testConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 4;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);


        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 4, testCases.size());

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "TumblrTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
    
    
    // RANDOM TEST CASE GENERATION WITH FAULTS. NO FILTERS
    
    
    @Test
    public void bikewiseFullTestCaseGeneratorWithFaults() {
        // Load specification
        String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Bikewise/fullConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 5;
        
        // Faulty ratio
        float faultyRatio = 0.2f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

        Collection<TestCase> testCases = generator.generate();
        

        assertEquals("Incorrect number of test cases", 20, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
        assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());
    }
    
    
	@Test
	public void commentsFullTestCaseGeneratorWithFaults() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Comments/testConf_forTestSuite.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 20;
		
        // Faulty ratio
        float faultyRatio = 0.5f;
		
		// Create generator
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();
		
		assertEquals("Incorrect number of test cases", 80, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(c -> c.getFaulty()).count());
        assertEquals("Incorrect number of faulty test cases", 30, testCases.stream().filter(c -> c.getFaulty()).count());			// One of the 4 operations cannot be mutated.
		
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "CommentsTest", "restassured", basePath);
		writer.setOAIValidation(true);
		writer.write(testCases);	
	}
	
    
    @Test
    @Ignore        // To avoid the test failing in Travis
    public void travelTestCaseGeneratorWithFaults() {

        // Load specification
        String OAISpecPath = "src/test/resources/Travel/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Travel/testConf_forTestSuite.yaml", spec);

        // Set number of test cases to be generated on each path
        int numTestCases = 20;
        
        // Faulty ratio
        float faultyRatio = 0.5f;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
        generator.setFaultyRatio(faultyRatio);

        Collection<TestCase> testCases = generator.generate();

        assertEquals("Incorrect number of test cases", 120, testCases.size());
        assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
        assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());

        // Write test cases
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "TravelTest", "restassured", basePath);
        writer.setOAIValidation(true);
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
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
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
    
    
    // RANDOM TEST CASE GENERATION WITH FILTERS. NO FAULTS
    
    
	@Test
	public void amadeusSearchHotelsWithFilter() {
		

		// Load specification
		String OAISpecPath = "src/test/resources/Amadeus/spec.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Amadeus/confTest.yaml", spec);
		
		// Set number of test cases to be generated on each path, on each operation
		int numTestCases = 10;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		
		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/hotels/search-airport");
		filter.addGetMethod();
		filters.add(filter);
		
		Collection<TestCase> testCases = generator.generate(filters);
		
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "AmadeusHotelSearchTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}
    
    
    

	@Test
	public void spotifyGetAlbumWithFilter() {
		

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/confTest.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		
		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/albums");
		filter.addGetMethod();
		filters.add(filter);
		
		Collection<TestCase> testCases = generator.generate(filters);
		
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifyGetAlbumTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);

	}

	@Test
	public void spotifyGetArtistWithFilter() {


		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/defaultConf.json", spec);

		// Set number of test cases to be generated on each path
		int numTestCases = 10;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/artists");
		filter.addGetMethod();
		filters.add(filter);

		Collection<TestCase> testCases = generator.generate(filters);

		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifyGetArtistTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);

	}
	
	
	@Test
	public void spotifySearchWithFilter() {
		

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/confTest.yaml", spec);
		
		// Set number of test cases to be generated on each path
		int numTestCases = 20;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		
		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/search");
		filter.addGetMethod();
		filters.add(filter);
		
		Collection<TestCase> testCases = generator.generate(filters);
		
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifySearchTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}
	
	@Test
	public void petstoreTestCaseGeneratorWithFilters() {
		// Load specification
		String OAISpecPath = "src/test/resources/Petstore/swagger.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration("src/test/resources/Petstore/fullConf_forTestSuite.yaml", spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 3;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/store/order/{orderId}");
		filter.addGetMethod();
		filters.add(filter);

		TestConfigurationFilter filter2 = new TestConfigurationFilter();
		filter2.setPath("/user/login");
		filter2.addGetMethod();
		filters.add(filter2);

		TestConfigurationFilter filter3 = new TestConfigurationFilter();
		filter3.setPath("/pet");
		filter3.addPostMethod();
		filter3.addPutMethod();
		filters.add(filter3);

		TestConfigurationFilter filter4 = new TestConfigurationFilter();
		filter4.setPath("/store/order");
		filter4.addPostMethod();
		filters.add(filter4);

		TestConfigurationFilter filter5 = new TestConfigurationFilter();
		filter5.setPath("/user");
		filter5.addPostMethod();
		filters.add(filter5);

		Collection<TestCase> testCases = generator.generate(filters);

		 assertEquals("Incorrect number of test cases", 18, testCases.size());
		 

		// Check coverage
		/*
		CoverageGatherer coverageGatherer = new CoverageGatherer(spec);
		CoverageMeter coverageMeter = new CoverageMeter(coverageGatherer, testCases);
		System.out.println("Total coverage: " + coverageMeter.getTotalCoverage());
		System.out.println("Input coverage: " + coverageMeter.getInputCoverage());
		System.out.println("PATH coverage: " + coverageMeter.getCriterionTypeCoverage(PATH));
		System.out.println("OPERATION coverage: " + coverageMeter.getCriterionTypeCoverage(OPERATION));
		System.out.println("PARAMETER coverage: " + coverageMeter.getCriterionTypeCoverage(PARAMETER));
		System.out.println("PARAMETER_VALUE coverage: " + coverageMeter.getCriterionTypeCoverage(PARAMETER_VALUE));
		System.out.println("AUTHENTICATION coverage: " + coverageMeter.getCriterionTypeCoverage(AUTHENTICATION));
		System.out.println("INPUT_CONTENT_TYPE coverage: " + coverageMeter.getCriterionTypeCoverage(OPERATION));

		 */
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "PetstoreTest",
				"restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}
	
	
    // RANDOM TEST CASE GENERATION WITH FILTERS AND FAULTS
	
	
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
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
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

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifyGetArtistTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);

	}
	
	@Test
	public void petstoreTestCaseGeneratorWithFiltersAndFaults() {
		// Load specification
		String OAISpecPath = "src/test/resources/Petstore/swagger.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration("src/test/resources/Petstore/fullConf_forTestSuite.yaml", spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 3;
		
        // Faulty ratio
        float faultyRatio = 0.5f;


		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		List<TestConfigurationFilter> filters = new ArrayList<>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/store/order/{orderId}");
		filter.addGetMethod();
		filters.add(filter);

		TestConfigurationFilter filter2 = new TestConfigurationFilter();
		filter2.setPath("/user/login");
		filter2.addGetMethod();
		filters.add(filter2);

		TestConfigurationFilter filter3 = new TestConfigurationFilter();
		filter3.setPath("/pet");
		filter3.addPostMethod();
		filter3.addPutMethod();
		filters.add(filter3);

		TestConfigurationFilter filter4 = new TestConfigurationFilter();
		filter4.setPath("/store/order");
		filter4.addPostMethod();
		filters.add(filter4);

		TestConfigurationFilter filter5 = new TestConfigurationFilter();
		filter5.setPath("/user");
		filter5.addPostMethod();
		filters.add(filter5);

		Collection<TestCase> testCases = generator.generate(filters);
		
		
		assertEquals("Incorrect number of test cases", 18, testCases.size());
	    assertEquals("Incorrect number of faulty test cases generated", generator.getnFaulty(), testCases.stream().filter(TestCase::getFaulty).count());
	    assertEquals("Incorrect number of faulty test cases", (int) (faultyRatio*testCases.size()), testCases.stream().filter(c -> c.getFaulty()).count());
		 
		// Check coverage
		/*
		CoverageGatherer coverageGatherer = new CoverageGatherer(spec);
		CoverageMeter coverageMeter = new CoverageMeter(coverageGatherer, testCases);
		System.out.println("Total coverage: " + coverageMeter.getTotalCoverage());
		System.out.println("Input coverage: " + coverageMeter.getInputCoverage());
		System.out.println("PATH coverage: " + coverageMeter.getCriterionTypeCoverage(PATH));
		System.out.println("OPERATION coverage: " + coverageMeter.getCriterionTypeCoverage(OPERATION));
		System.out.println("PARAMETER coverage: " + coverageMeter.getCriterionTypeCoverage(PARAMETER));
		System.out.println("PARAMETER_VALUE coverage: " + coverageMeter.getCriterionTypeCoverage(PARAMETER_VALUE));
		System.out.println("AUTHENTICATION coverage: " + coverageMeter.getCriterionTypeCoverage(AUTHENTICATION));
		System.out.println("INPUT_CONTENT_TYPE coverage: " + coverageMeter.getCriterionTypeCoverage(OPERATION));

		 */


	}
    
    
}
