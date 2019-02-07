package es.us.isa.rester.testcases.writters;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.testcases.TestCase;
import io.swagger.models.HttpMethod;

public class RESTAssuredWritterTest {
	
	@Test
	public void test() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/specifications/petstore.json";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Create test case
		List<TestCase> testCases = new ArrayList<TestCase>();
		TestCase tc = new TestCase("findPetsByStatus","/pet/findByStatus" ,HttpMethod.GET);
		tc.setOutputFormat("application/json");
		
		tc.addHeaderParameter("Authorization", "Bearer sklfhskdlafjsklf092359wejtu0349");
		tc.addQueryParameter("status", "pending");
		
		tc.setExpectedOutputs(spec.getSpecification().getPath("/pet/findByStatus").getGet().getResponses());
		
		testCases.add(tc);
		
		// Write test case
		RESTAssuredWritter writer = new RESTAssuredWritter();
		writer.setOAIValidation(true);
		writer.write(OAISpecPath, "src/test/java/es/us/isa/generatedtests", "petstore", null, "http://petstore.swagger.io", testCases);
		
	}

}
