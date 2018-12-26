package es.us.isa.rester.configuration.generators;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.util.TestConfigurationFilter;

public class DefaultTestConfigurationGeneratorTest {

	@Test
	public void testBigOvenTestConfigurationGeneration() {
		
		String specPath="src/main/resources/BigOven/spec.json";
		String confPath="src/main/resources/BigOven/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/recipes");
		filter.addGetMethod();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator();
		gen.generate(spec, confPath, filters);
	}
	
	
	@Test
	public void testSpotifyTestConfigurationGeneration() {
		
		String specPath="src/main/resources/Spotify/spec.json";
		String confPath="src/main/resources/Spotify/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		
		// Filter 1
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/albums");
		filter.addGetMethod();
		filters.add(filter);
		
		// Filter 2
		TestConfigurationFilter filter2 = new TestConfigurationFilter();
		filter2.setPath("/search");
		filter2.addGetMethod();
		filters.add(filter2);

		// Filter 3
		TestConfigurationFilter filter3 = new TestConfigurationFilter();
		filter3.setPath("/artists");
		filter3.addGetMethod();
		filters.add(filter3);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator();
		gen.generate(spec, confPath, filters);
		
	}
	
	@Test
	public void testAmadeusTestConfigurationGeneration() {
		
		String specPath="src/main/resources/Amadeus/spec.json";
		String confPath="src/main/resources/Amadeus/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/hotels/search-airport");
		filter.addGetMethod();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator();
		gen.generate(spec, confPath, filters);
	}
	
	@Test
	public void testPlaylistTestConfigurationGeneration() {
		
		String specPath="src/main/resources/Playlist/spec.json";
		String confPath="src/main/resources/Playlist/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);		// null = All paths
		filter.addAllMethods();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator();
		gen.generate(spec, confPath, filters);
	}

}
