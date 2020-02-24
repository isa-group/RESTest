package es.us.isa.restest.configuration.generators;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;

public class DefaultTestConfigurationGeneratorTest {

	@Test
	public void testBigOvenTestConfigurationGeneration() {
		
		String specPath="src/test/resources/BigOven/spec.yaml";
		String confPath="src/test/resources/BigOven/defaultConf.yaml";
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
		
		String specPath="src/test/resources/Spotify/spec.yaml";
		String confPath="src/test/resources/Spotify/defaultConf.yaml";
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
		
		String specPath="src/test/resources/Amadeus/spec.yaml";
		String confPath="src/test/resources/Amadeus/defaultConf.yaml";
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
	public void testAmadeusHotelTestConfigurationGeneration() {

		String specPath="src/test/resources/AmadeusHotel/swagger.yaml";
		String confPath="src/test/resources/AmadeusHotel/defaultConf.yaml";
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
	public void testPlaylistTestConfigurationGeneration() {
		
		String specPath="src/test/resources/Playlist/spec.yaml";
		String confPath="src/test/resources/Playlist/defaultConf.yaml";
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

		String specPath="src/test/resources/Amadeus/spec.yaml";
		String confPath="src/test/resources/Amadeus/fullConf.yaml";
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

		String specPath="src/test/resources/Petstore/swagger.yaml";
		String confPath="src/test/resources/Petstore/fullConf.yaml";
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

		String specPath="src/test/resources/SimpleAPI/swagger.yaml";
		String confPath="src/test/resources/SimpleAPI/fullConf.yaml";
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

		String specPath="src/test/resources/Bikewise/swagger.yaml";
		String confPath="src/test/resources/Bikewise/fullConf.yaml";
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

		String specPath="src/test/resources/DataAtWork/swagger.yaml";
		String confPath="src/test/resources/DataAtWork/fullConf.yaml";
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
	public void testYouTubeFullTestConfigurationGeneration() {

		String specPath="src/main/resources/YouTube/swagger.yaml";
		String confPath="src/main/resources/YouTube/testConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
//		TestConfigurationFilter filter = new TestConfigurationFilter();
//		filter.setPath(null);
//		filter.addAllMethods();
//		filters.add(filter);

		// Filter 1
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/activities");
		filter.addGetMethod();
		filter.addPostMethod();
		filters.add(filter);

		// Filter 2
		TestConfigurationFilter filter2 = new TestConfigurationFilter();
		filter2.setPath("/search");
		filter2.addGetMethod();
		filters.add(filter2);

		// Filter 3
		TestConfigurationFilter filter3 = new TestConfigurationFilter();
		filter3.setPath("/videos");
		filter3.addGetMethod();
		filter3.addPostMethod();
		filters.add(filter3);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testCommentsTestConfigurationGeneration() {

		String specPath="src/test/resources/Comments/swagger.yaml";
		String confPath="src/test/resources/Comments/testConf.yaml";
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
	public void testComments2TestConfigurationGeneration() {

		String specPath="src/test/resources/Comments/swagger_forTestSuite2.yaml";
		String confPath="src/test/resources/Comments/testConf_forTestSuite2.yaml";
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
	public void testEventsTestConfigurationGeneration() {

		String specPath="src/test/resources/Events/swagger.yaml";
		String confPath="src/test/resources/Events/testConf.yaml";
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
	public void testTravelTestConfigurationGeneration() {

		String specPath="src/test/resources/Travel/swagger.yaml";
		String confPath="src/test/resources/Travel/testConf.yaml";
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
	public void testYouTubeSearchTestConfigurationGeneration() {

		String specPath="src/test/resources/YouTube/swagger.yaml";
		String confPath="src/test/resources/YouTube/testConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();

		// Filter 1
		TestConfigurationFilter filter2 = new TestConfigurationFilter();
		filter2.setPath("/search");
		filter2.addGetMethod();
		filters.add(filter2);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}

	@Test
	public void testOMDbTestConfigurationGeneration() {

		String specPath="src/test/resources/OMDb/swagger.yaml";
		String confPath="src/test/resources/OMDb/testConf.yaml";
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
	public void testMemesTestConfigurationGeneration() {

		String specPath="src/test/resources/Memes/swagger.yaml";
		String confPath="src/test/resources/Memes/testConf.yaml";
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
	public void testMarvelTestConfigurationGeneration() {

		String specPath="src/test/resources/Marvel/swagger.yaml";
		String confPath="src/test/resources/Marvel/testConf.yaml";
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
	public void testTwitterTestConfigurationGeneration() {

		String specPath="src/test/resources/Twitter/swagger.yaml";
		String confPath="src/test/resources/Twitter/testConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);		// null = All paths
		filter.addAllMethods();
		filters.add(filter);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
	}


}
