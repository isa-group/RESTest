package es.us.isa.rester.configuration.generators;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.util.TestConfigurationFilter;

public class DefaultTestConfigurationGeneratorTest {

	@Test
	public void testBigOvenTestConfigurationGeneration() {
		
		String specPath="src/main/resources/BigOven/spec.yaml";
		String confPath="src/main/resources/BigOven/defaultConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/recipes");
		filter.addGetMethod();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}
	
	
	@Test
	public void testSpotifyTestConfigurationGeneration() {
		
		String specPath="src/main/resources/Spotify/spec.yaml";
		String confPath="src/main/resources/Spotify/defaultConf.yaml";
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
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		
	}
	
	@Test
	public void testAmadeusTestConfigurationGeneration() {
		
		String specPath="src/main/resources/Amadeus/spec.yaml";
		String confPath="src/main/resources/Amadeus/defaultConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/hotels/search-airport");
		filter.addGetMethod();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}
	
	@Test
	public void testPlaylistTestConfigurationGeneration() {
		
		String specPath="src/main/resources/Playlist/spec.yaml";
		String confPath="src/main/resources/Playlist/defaultConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);		// null = All paths
		filter.addAllMethods();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testAmadeusFullTestConfigurationGeneration() {

		String specPath="src/main/resources/Amadeus/spec.yaml";
		String confPath="src/main/resources/Amadeus/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		filters.add(filter);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testPetstoreFullTestConfigurationGeneration() {

		String specPath="src/main/resources/Petstore/swagger.yaml";
		String confPath="src/main/resources/Petstore/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		filters.add(filter);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testSimpleAPIFullTestConfigurationGeneration() {

		String specPath="src/main/resources/SimpleAPI/swagger.yaml";
		String confPath="src/main/resources/SimpleAPI/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		filters.add(filter);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testBikewiseFullTestConfigurationGeneration() {

		String specPath="src/main/resources/Bikewise/swagger.yaml";
		String confPath="src/main/resources/Bikewise/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		filters.add(filter);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testDataAtWorkFullTestConfigurationGeneration() {

		String specPath="src/main/resources/DataAtWork/swagger.yaml";
		String confPath="src/main/resources/DataAtWork/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		filters.add(filter);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

}
