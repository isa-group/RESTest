package es.us.isa.restest.generators;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;

public class SpotifyRandomTestCaseGeneratorTest {

	@Test
	public void spotifyGetAlbum() {
		

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/confTest.yaml");
		
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
		String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifyGetAlbumTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);

	}

	@Test
	public void spotifyGetArtist() {


		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/defaultConf.json");

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
		String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifyGetArtistTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.write(testCases);

	}
	
	
	@Test
	public void spotifySearch() {
		

		// Load specification
		String OAISpecPath = "src/test/resources/Spotify/spec.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Spotify/confTest.yaml");
		
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
		String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "SpotifySearchTest", "restassured", basePath.toLowerCase());
		writer.setOAIValidation(true);
		writer.setLogging(true);
		writer.write(testCases);

	}
	
	
	

}
