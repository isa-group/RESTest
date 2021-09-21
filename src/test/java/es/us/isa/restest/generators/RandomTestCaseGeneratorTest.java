package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.RESTestException;

import org.junit.Ignore;
import org.junit.Test;

import static es.us.isa.restest.coverage.CriterionType.AUTHENTICATION;
import static es.us.isa.restest.coverage.CriterionType.OPERATION;
import static es.us.isa.restest.coverage.CriterionType.PARAMETER;
import static es.us.isa.restest.coverage.CriterionType.PARAMETER_VALUE;
import static es.us.isa.restest.coverage.CriterionType.PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RandomTestCaseGeneratorTest {

	// PET STORE

	@Test
	public void petstoreTestCaseGeneratorWithFilters() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Petstore/swagger.yaml";
		String testConf = "src/test/resources/Petstore/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

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

		
		// Expected results
		int expectedNumberOfTestCases = 18;	
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute faulty)", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "PetstoreTest",
				"restassured", basePath.toLowerCase(), true);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	@Test
	public void petstoreTestCaseGeneratorWithFiltersAndFaults() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Petstore/swagger.yaml";
		String testConf = "src/test/resources/Petstore/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

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

		
		// Expected results
		int expectedNumberOfTestCases = 18;
		int expectedNumberOfInvalidTestCases = 6;
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

		// Check coverage
		/*
		 * CoverageGatherer coverageGatherer = new CoverageGatherer(spec); CoverageMeter
		 * coverageMeter = new CoverageMeter(coverageGatherer, testCases);
		 * System.out.println("Total coverage: " + coverageMeter.getTotalCoverage());
		 * System.out.println("Input coverage: " + coverageMeter.getInputCoverage());
		 * System.out.println("PATH coverage: " +
		 * coverageMeter.getCriterionTypeCoverage(PATH));
		 * System.out.println("OPERATION coverage: " +
		 * coverageMeter.getCriterionTypeCoverage(OPERATION));
		 * System.out.println("PARAMETER coverage: " +
		 * coverageMeter.getCriterionTypeCoverage(PARAMETER));
		 * System.out.println("PARAMETER_VALUE coverage: " +
		 * coverageMeter.getCriterionTypeCoverage(PARAMETER_VALUE));
		 * System.out.println("AUTHENTICATION coverage: " +
		 * coverageMeter.getCriterionTypeCoverage(AUTHENTICATION));
		 * System.out.println("INPUT_CONTENT_TYPE coverage: " +
		 * coverageMeter.getCriterionTypeCoverage(OPERATION));
		 * 
		 */

	}

	// BIKEWISE

	@Test
	public void bikewiseFullTestCaseGenerator() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
		String testConf = "src/test/resources/Bikewise/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 20;		// All should be valid
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "BikewiseTest",
				"restassured", basePath.toLowerCase(), false);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	@Test
	public void bikewiseFullTestCaseGeneratorWithFaults() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";
		String testConf = "src/test/resources/Bikewise/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 0.2f;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();

		
		// Expected results
		int expectedNumberOfTestCases = 20;
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
	public void memesTestCaseGeneratorWithInvalidGeneratorWithoutFaults() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Memes/swagger_forTestSuite.yaml";
		String testConf = "src/test/resources/Memes/testConf_forTestSuite.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 0f;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
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
	public void memesTestCaseGeneratorWithInvalidGeneratorWithFaults() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Memes/swagger_forTestSuite.yaml";
		String testConf = "src/test/resources/Memes/testConf_forTestSuite.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Faulty ratio
		float faultyRatio = 1f;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
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

		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());

		// Assert that the invalid generator was used everytime, since no mutation could be applied to make the test case invalid
		for (TestCase tc: testCases)
			if (tc.getPath().equals("/gallery/{id}"))
				assertEquals("Incorrect parameter value", "invalid_value", tc.getPathParameters().get("id"));
	}

	// AMADEUS

	@Test
	public void amadeusHotelFullTestCaseGenerator() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";
		String testConf = "src/test/resources/AmadeusHotel/defaultConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 4;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

	
		// Expected results
		int expectedNumberOfTestCases = 4;

		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", 4, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"AmadeusHotelTest", "restassured", basePath.toLowerCase(), true);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	@Test
	public void amadeusSearchHotelsWithFilter() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Amadeus/spec.yaml";
		String testConf = "src/test/resources/Amadeus/confTest.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf,
				spec);

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

		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", numTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", numTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", numTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		/*
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"AmadeusHotelSearchTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);
		
		*/

	}

	// SPOTIFY

	@Test
	public void spotifyGetAlbumWithFilter() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.yaml";
		String testConf = "src/test/resources/Spotify/confTest.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf,
				spec);

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

		// Total number of test cases
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", numTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", numTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", numTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"SpotifyGetAlbumTest", "restassured", basePath.toLowerCase(), false);
		writer.setOAIValidation(true);
		writer.write(testCases);

	}

	@Test
	public void spotifyGetArtistWithFilter() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.json";
		String testConf = "src/test/resources/Spotify/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

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

		/*
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"SpotifyGetArtistTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);
		*/

	}

	@Test
	public void spotifySearchWithFilter() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.yaml";
		String testConf = "src/test/resources/Spotify/confTest.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf,
				spec);

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

		// Total number of test cases
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", numTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", numTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", numTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		/*
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"SpotifySearchTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);
		*/

	}

	@Test
	public void spotifyGetArtistWithFilterAndFaults() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.json";
		String testConf = "src/test/resources/Spotify/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

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

		
		// Expected results
		int expectedNumberOfTestCases = numTestCases;
		int expectedNumberOfInvalidTestCases = (int) (faultyRatio * expectedNumberOfTestCases);
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		
		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty()).count());
		assertEquals("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases, GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));

		/*
		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"SpotifyGetArtistTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);
		*/

	}

	// DATA AT WORK

	@Test
	public void dataAtWorkFullTestCaseGeneratorTest() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/DataAtWork/swagger.yaml";
		String testConf = "src/test/resources/DataAtWork/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 1;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 13;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"DataAtWorkTest", "restassured", basePath.toLowerCase(), true);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	// FOURSQUARE

	@Test
	public void foursquareFullTestCaseGenerator() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Foursquare/swagger.yaml";
		String testConf = "src/test/resources/Foursquare/testConf_forTestSuite2.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation (HTTP method)
		int numTestCases = 10;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

		
		// Expected results
		int expectedNumberOfTestCases = 10;
		
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"FoursquareTest", "restassured", basePath.toLowerCase(), false);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	// MARVEL

	@Test
	public void marvelFullTestCaseGenerator() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Marvel/swagger.yaml";
		String testConf = "src/test/resources/Marvel/testConf_forTestSuite2.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf,
				spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 4;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 24;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "MarvelTest",
				"restassured", basePath.toLowerCase(), true);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	// PLAYLIST

	@Test
	public void playlistFullTestCaseGenerator() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Playlist/spec.yaml";
		String testConf = "src/test/resources/Playlist/defaultConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path
		int numTestCases = 3;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 30;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "PlaylistTest",
				"restassured", basePath.toLowerCase(), false);
		writer.setOAIValidation(true);
		writer.write(testCases);
	}

	// SIMPLE API

	@Test
	public void simpleAPIFullTestCaseGenerator() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/SimpleAPI/swagger.yaml";
		String testConf = "src/test/resources/SimpleAPI/fullConfRight.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 5;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();

		// Expected results
		int expectedNumberOfTestCases = 15;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured",
				"SimpleAPITest", "restassured", basePath.toLowerCase(), true);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	// TUMBLR

	@Test
	public void tumblrFullTestCaseGenerator() throws RESTestException {
		// Load specification
		String OAISpecPath = "src/test/resources/Tumblr/swagger.yaml";
		String testConf = "src/test/resources/Tumblr/testConf_forTestSuite2.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf,
				spec);

		// Set number of test cases to be generated on each path, on each operation
		// (HTTP method)
		int numTestCases = 4;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

		Collection<TestCase> testCases = generator.generate();


		// Expected results
		int expectedNumberOfTestCases = 4;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfTestCases, testCases.stream().filter(c -> !c.getFaulty()).count());
		assertEquals("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfTestCases, GeneratorTestHelper.numberOfValidTestCases(testCases, spec));

		// Write RESTAssured test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "TumblrTest",
				"restassured", basePath.toLowerCase(), false);
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}

	// COMMENTS

	@Test
	public void commentsFullTestCaseGeneratorWithFaults() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		String testConf = "src/test/resources/Comments/testConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path
		int numTestCases = 20;

		// Faulty ratio
		float faultyRatio = 0.5f;

		// Create generator
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();
		
		// Expected results
		int expectedNumberOfTestCases = 80;
		int expectedNumberOfInvalidTestCases = 30;
		int expectedNumberOfValidTestCases = expectedNumberOfTestCases - expectedNumberOfInvalidTestCases;
		
		// Total number of test cases
		assertEquals("Incorrect number of test cases", expectedNumberOfTestCases, testCases.size());
		
		// Valid test cases
		assertEquals("Incorrect number of valid test cases generated (according to the generator counter)", expectedNumberOfValidTestCases, generator.getnNominal());
		assertEquals("Incorrect number of valid test cases (according to the attribute 'faulty')", expectedNumberOfValidTestCases, testCases.stream().filter(c -> c.getFaulty() != null && !c.getFaulty()).count());
		assertTrue("Incorrect number of valid test cases (according to the OAS validator)", expectedNumberOfValidTestCases <= GeneratorTestHelper.numberOfValidTestCases(testCases, spec));
		
		// Invalid test cases
		assertEquals("Incorrect number of faulty test cases generated (according to the generator counter)", expectedNumberOfInvalidTestCases, generator.getnFaulty());
		assertEquals("Incorrect number of faulty test cases (according to the attribute 'faulty')", expectedNumberOfInvalidTestCases, testCases.stream().filter(c -> c.getFaulty() == null || c.getFaulty()).count()); // One of the 4 operations cannot be mutated.
		assertTrue("Incorrect number of faulty test cases (according to the OAS validator)", expectedNumberOfInvalidTestCases >= GeneratorTestHelper.numberOfInvalidTestCases(testCases, spec));

		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsTest",
				"restassured", basePath, true);
		writer.setOAIValidation(true);
		writer.write(testCases);
	}

	@Test
	public void commentsFullTestCaseGeneratorWithPerturbationAndFaults() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Comments/swagger.yaml";
		String testConf = "src/test/resources/Comments/testConf_forTestSuite3.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO
				.loadConfiguration(testConf, spec);

		// Set number of test cases to be generated on each path
		int numTestCases = 10;

		// Faulty ratio
		float faultyRatio = 0.1f;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();

		
		// Expected results
		int expectedNumberOfTestCases = 30;
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

		/*
		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsTest",
				"restassured", basePath);
		writer.setOAIValidation(true);
		writer.write(testCases);
		*/
	}

	// TRAVEL

	@Test

	public void travelTestCaseGeneratorWithFaults() throws RESTestException {

		// Load specification
		String OAISpecPath = "src/test/resources/Travel/swagger.yaml";
		String testConf = "src/test/resources/Travel/testConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(testConf,
				spec);

		// Set number of test cases to be generated on each path
		int numTestCases = 20;

		// Faulty ratio
		float faultyRatio = 0.5f;

		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		generator.setFaultyRatio(faultyRatio);

		Collection<TestCase> testCases = generator.generate();

		
		// Expected results
		int expectedNumberOfTestCases = 120;
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

		// Write test cases
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "TravelTest",
				"restassured", basePath, false);
		writer.setOAIValidation(true);
		writer.write(testCases);
	}

}
