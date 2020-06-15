package es.us.isa.restest.specification;

import static org.junit.Assert.*;

import org.junit.Test;

public class OpenAPISpecificationTest {

	@Test
	public void testOpenAPISpecificationv3() {
		OpenAPISpecification spec = new OpenAPISpecification("http://petstore.swagger.io/v2/swagger.json");
		assertEquals("Wrong parsing", "https://petstore.swagger.io/v2", spec.getSpecification().getServers().get(0).getUrl());
	}

	@Test
	public void testGetResponsesFromAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Petstore/swagger.json");
		assertEquals("Wrong number of responses", 2, spec.getSpecification().getPaths().get("/pet/findByStatus").getGet().getResponses().size());
	}
	
	@Test
	public void testReadAmadeusAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Amadeus/spec.json");
		assertEquals("Wrong number of paths", 23, spec.getSpecification().getPaths().size());
	}
	
	@Test
	public void testReadBigOvenAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/BigOven/spec.json");
		assertEquals("Wrong number of paths", 49, spec.getSpecification().getPaths().size());
	}
	
	@Test
	public void testReadSpotifyAPISpecification() {
		OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Spotify/spec.json");
		assertEquals("Wrong number of paths", 27, spec.getSpecification().getPaths().size());
	}
	
}
