package es.us.isa.rester.specification;

import static org.junit.Assert.*;

import org.junit.Test;

import es.us.isa.rester.specification.OpenAPISpecification;

public class OpenAPISpecificationTest {

	@Test
	public void testOpenAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("http://petstore.swagger.io/v2/swagger.json");
		assertEquals("Wrong parsing", "petstore.swagger.io", spec.getSpecification().getHost());
	}

	@Test
	public void testGetResponsesFromAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/specifications/petstore.json");
		assertEquals("Wrong number of responses", 2, spec.getSpecification().getPath("/pet/findByStatus").getGet().getResponses().size());
	}
	
	@Test
	public void testReadAmadeusAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/main/resources/Amadeus/spec.json");
		assertEquals("Wrong number of paths", 23, spec.getSpecification().getPaths().size());
	}
	
	@Test
	public void testReadBigOvenAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/main/resources/BigOven/spec.json");
		assertEquals("Wrong number of paths", 49, spec.getSpecification().getPaths().size());
	}
	
	@Test
	public void testReadSpotifyAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/main/resources/Spotify/spec.json");
		assertEquals("Wrong number of paths", 27, spec.getSpecification().getPaths().size());
	}
	
}
