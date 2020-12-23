package es.us.isa.restest.testcases.writers;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

public class RESTAssuredWriterTest {
	
	@Test
	public void test() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/specifications/petstore.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Create test case
		List<TestCase> testCases = new ArrayList<TestCase>();
		TestCase tc = new TestCase("findPetsByStatusId", false, "findPetsByStatus","/pet/findByStatus" ,HttpMethod.GET);
		tc.setOutputFormat("application/json");
		
		tc.addHeaderParameter("Authorization", "Bearer sklfhskdlafjsklf092359wejtu0349");
		tc.addQueryParameter("status", "pending");

		testCases.add(tc);
		
		// Write test case
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "Petstore", "restassured", "http://petstore.swagger.io", true);
		writer.setOAIValidation(true);
		writer.write(testCases);
		
	}

}
