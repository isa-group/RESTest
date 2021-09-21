package es.us.isa.restest.testcases.writers;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import static es.us.isa.restest.util.FileManager.readFile;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RESTAssuredWriterTest {
	
	@Test
	public void test() {
		
		// Load specification
		String OAISpecPath = "src/test/resources/specifications/petstore.json";
		String testConf = "src/test/resources/Petstore/fullConf.yaml";
		OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);
		
		// Create test case
		List<TestCase> testCases = new ArrayList<TestCase>();
		TestCase tc = new TestCase("findPetsByStatusId", false, "findPetsByStatus","/pet/findByStatus" ,HttpMethod.GET);
		tc.setOutputFormat("application/json");
		
		tc.addHeaderParameter("Authorization", "Bearer sklfhskdlafjsklf092359wejtu0349");
		tc.addQueryParameter("status", "pending");

		testCases.add(tc);
		
		// Write test case
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "Petstore", "restassured", "http://petstore.swagger.io", false);
		writer.setOAIValidation(true);
		writer.write(testCases);
		
	}

	@Test
	public void statefulFilterEnabledTest() {
		// Load specification and testConf
		String OAISpecPath = "src/test/resources/Comments/swagger_demo.yaml";
		String testConf = "src/test/resources/Comments/testConf_demo2.yaml";

		// Create test case
		List<TestCase> testCases = new ArrayList<TestCase>();
		TestCase tc = new TestCase("getComments", false, "getComments","/comments" ,HttpMethod.GET);
		testCases.add(tc);

		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsTestStateful", "restassured", "http://localhost:8080/api", false);
		assertTrue("The writer should have the statefulFilter enabled", writer.isStatefulFilter());

		writer.write(testCases);
		assertTrue(
				"The generated test class should use the stateful filter",
				readFile("src/generation/java/restassured/CommentsTestStateful.java").contains("statefulFilter.setOperation")
				);
	}

	@Test
	public void statefulFilterDisabledTest() {
		// Load specification and testConf
		String OAISpecPath = "src/test/resources/Comments/swagger_demo.yaml";
		String testConf = "src/test/resources/Comments/testConf_demo.yaml";

		// Create test case
		List<TestCase> testCases = new ArrayList<TestCase>();
		TestCase tc = new TestCase("getComments", false, "getComments","/comments" ,HttpMethod.GET);
		testCases.add(tc);

		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, testConf, "src/generation/java/restassured", "CommentsTestNonStateful", "restassured", "http://localhost:8080/api", false);
		assertFalse("The writer should have the statefulFilter disabled", writer.isStatefulFilter());

		writer.write(testCases);
		assertFalse(
				"The generated test class should NOT use the stateful filter",
				readFile("src/generation/java/restassured/CommentsTestNonStateful.java").contains("statefulFilter.setOperationId")
		);
	}

}
