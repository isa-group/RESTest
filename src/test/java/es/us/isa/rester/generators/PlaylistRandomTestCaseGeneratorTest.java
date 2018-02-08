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

public class PlaylistRandomTestCaseGeneratorTest {

	@Test
	public void spotifyGetAlbum() {
		
		// Load specification
		String OAISpecPath = "src/main/resources/Playlist/spec.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/main/resources/Playlist/confTest.json");
		
		// Set number of test cases to be generated on each path
		int numTestCases = 10;
		
		// Create generator and filter
		AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/songs/{id}");
		filter.addGetMethod();
		filters.add(filter);
		
		Collection<TestCase> testCases = generator.generate(filters);
		
		assertEquals("Incorrect number of test cases", numTestCases, testCases.size());
		
		// Write test cases
		RESTAssuredWritter writer = new RESTAssuredWritter();
		writer.setOAIValidation(true);
		String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
		writer.write(OAISpecPath, "src/generation/java", "Playlist", null, basePath.toLowerCase(), testCases);	
		}
	
	
	

}
