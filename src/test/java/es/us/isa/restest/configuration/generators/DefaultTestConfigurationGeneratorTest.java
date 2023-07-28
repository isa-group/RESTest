package es.us.isa.restest.configuration.generators;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.specification.OpenAPISpecification;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.FileManager.deleteFile;
import static org.junit.Assert.assertTrue;

public class DefaultTestConfigurationGeneratorTest {

	@Test
	public void testBigOvenTestConfigurationGeneration() {
		
		String specPath="src/test/resources/BigOven/spec.yaml";
		String confPath="src/test/resources/BigOven/defaultConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("/recipes:get")
		);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}
	
	
	@Test
	public void testSpotifyTestConfigurationGeneration() {
		
		String specPath="src/test/resources/Spotify/spec.yaml";
		String confPath="src/test/resources/Spotify/forReadmeConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

//		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
//
//		//We create the filter for the first operation: get an album
//		TestConfigurationFilter albumFilter = new TestConfigurationFilter();
//		albumFilter.setPath("/albums/{id}");     //This is the endpoint of the operation
//		albumFilter.addGetMethod();              //It is a GET operation, so we only add the GET method to the operation
//
//		//We create the filter for the second operation: get an artist
//		TestConfigurationFilter artistFilter = new TestConfigurationFilter();
//		artistFilter.setPath("/artists/{id}");      //This is the endpoint of the operation
//		artistFilter.addGetMethod();                                  //It is a GET operation, so we only add the GET method to the operation
//
//		//Adding the filters to the list
//		filters.add(albumFilter);
//		filters.add(artistFilter);

		//Generating the test configuration file:
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath
//				, filters
		);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
		
	}
	
	@Test
	public void testAmadeusTestConfigurationGeneration() {
		
		String specPath="src/test/resources/Amadeus/spec.yaml";
		String confPath="src/test/resources/Amadeus/defaultConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("/hotels/search-airport:get")
		);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testAmadeusHotelTestConfigurationGeneration() {

		String specPath="src/test/resources/AmadeusHotel/swagger.yaml";
		String confPath="src/test/resources/AmadeusHotel/defaultConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}
	
	@Test
	public void testPlaylistTestConfigurationGeneration() {
		
		String specPath="src/test/resources/Playlist/spec.yaml";
		String confPath="src/test/resources/Playlist/defaultConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testAmadeusFullTestConfigurationGeneration() {

		String specPath="src/test/resources/Amadeus/spec.yaml";
		String confPath="src/test/resources/Amadeus/fullConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testPetstoreFullTestConfigurationGeneration() {

		String specPath="src/test/resources/Petstore/swagger.yaml";
		String confPath="src/test/resources/Petstore/fullConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testSimpleAPIFullTestConfigurationGeneration() {

		String specPath="src/test/resources/SimpleAPI/swagger.yaml";
		String confPath="src/test/resources/SimpleAPI/fullConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testBikewiseFullTestConfigurationGeneration() {

		String specPath="src/test/resources/Bikewise/swagger.yaml";
		String confPath="src/test/resources/Bikewise/fullConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testDataAtWorkFullTestConfigurationGeneration() {

		String specPath="src/test/resources/DataAtWork/swagger.yaml";
		String confPath="src/test/resources/DataAtWork/fullConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testYouTubeFullTestConfigurationGeneration() {

		String specPath="src/test/resources/YouTube/swagger.yaml";
		String confPath="src/test/resources/YouTube/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("/activities:get,post"),
				TestConfigurationFilter.parse("/search:get"),
				TestConfigurationFilter.parse("/videos:get,post")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testCommentsTestConfigurationGeneration() {

		String specPath="src/test/resources/Comments/swagger.yaml";
		String confPath="src/test/resources/Comments/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testComments2TestConfigurationGeneration() {

		String specPath="src/test/resources/Comments/swagger_forTestSuite2.yaml";
		String confPath="src/test/resources/Comments/testConf_forTestSuite2_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testEventsTestConfigurationGeneration() {

		String specPath="src/test/resources/Events/swagger.yaml";
		String confPath="src/test/resources/Events/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testTravelTestConfigurationGeneration() {

		String specPath="src/test/resources/Travel/swagger.yaml";
		String confPath="src/test/resources/Travel/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testYouTubeSearchTestConfigurationGeneration() {

		String specPath="src/test/resources/YouTube/swagger.yaml";
		String confPath="src/test/resources/YouTube/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("/search:get")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testOMDbTestConfigurationGeneration() {

		String specPath="src/test/resources/OMDb/swagger.yaml";
		String confPath="src/test/resources/OMDb/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testMemesTestConfigurationGeneration() {

		String specPath="src/test/resources/Memes/swagger.yaml";
		String confPath="src/test/resources/Memes/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testMarvelTestConfigurationGeneration() {

		String specPath="src/test/resources/Marvel/swagger.yaml";
		String confPath="src/test/resources/Marvel/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testTwitterTestConfigurationGeneration() {

		String specPath="src/test/resources/Twitter/swagger.yaml";
		String confPath="src/test/resources/Twitter/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testFoursquareTestConfigurationGeneration() {

		String specPath="src/test/resources/Foursquare/swagger.yaml";
		String confPath="src/test/resources/Foursquare/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testBingWebSearchTestConfigurationGeneration() {

		String specPath="src/test/resources/BingWebSearch/swagger.yaml";
		String confPath="src/test/resources/BingWebSearch/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testTumblrTestConfigurationGeneration() {

		String specPath="src/test/resources/Tumblr/swagger.yaml";
		String confPath="src/test/resources/Tumblr/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testStripeTestConfigurationGeneration() {

		String specPath="src/test/resources/Stripe/swagger.yaml";
		String confPath="src/test/resources/Stripe/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testAnApiOfIceAndFireTestConfigurationGeneration() {
		String specPath="src/test/resources/AnApiOfIceAndFire/swagger.yaml";
		String confPath="src/test/resources/AnApiOfIceAndFire/testConf_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}

	@Test
	public void testScoutApiTestConfigurationGeneration() {
		String specPath="src/test/resources/restest-test-resources/swagger-scout.json";
		String confPath="src/test/resources/restest-test-resources/testConf-scout_test.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);

		List<TestConfigurationFilter> filters = List.of(
				TestConfigurationFilter.parse("*:all")
		);

		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);
		gen.generate(confPath, filters);
		assertTrue(checkIfExists(confPath));
		deleteFile(confPath);
	}


}
