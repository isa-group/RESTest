package es.us.isa.rester.restassured;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.atlassian.oai.validator.restassured.SwaggerValidationFilter;

import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.response.Response;

public class MarvelAPITest {

	static PrintStream fileOutPutStream;
	private static final String SWAGGER_JSON_URL = "src/test/resources/specifications/marvel.json";
	private final SwaggerValidationFilter validationFilter = new SwaggerValidationFilter(SWAGGER_JSON_URL);
	
	@BeforeClass
	public static void setUp() throws FileNotFoundException {
		RestAssured.baseURI = "http://gateway.marvel.com";
		fileOutPutStream = new PrintStream(new File("src/test/java/es/us/isa/rester/restassured/logs.txt"));
	}
	
	@Test
	@Ignore
	  public void v1publiccharactersGETTest() {
	    
		try {
		    Response response = RestAssured
		      .given()
		        .param("orderBy", "-name")
		        .param("name", "Hulk")
		        .param("ts", "1")
		        .param("apikey", "54e348a2843224f54203607a58f18dae")
		        .param("hash", "3f4fc52453eb425339ca7acc493ac20d")
		        .filter(new RequestLoggingFilter(fileOutPutStream))		// Send logs to a filter
				//.filter(validationFilter)								// OAI Schema validation (Fail!)
		        .get("/v1/public/characters");
		    
		    response.then()
	        .contentType("application/json");
		    
		    switch(response.getStatusCode()) {
		      case 200:
		        response.then()
		          .contentType("application/json")
		          .body("code", CoreMatchers.notNullValue())
		          .body("code", CoreMatchers.isA(Integer.class))
		          .body("status", CoreMatchers.notNullValue())
		          .body("status", CoreMatchers.isA(String.class))
		          .body("copyright", CoreMatchers.notNullValue())
		          .body("copyright", CoreMatchers.isA(String.class))
		          .body("attributionText", CoreMatchers.notNullValue())
		          .body("attributionText", CoreMatchers.isA(String.class))
		          .body("attributionHTML", CoreMatchers.notNullValue())
		          .body("attributionHTML", CoreMatchers.isA(String.class))
		          .body("data", CoreMatchers.notNullValue())
		          .body("data", CoreMatchers.isA(Object.class))
		          .body("etag", CoreMatchers.notNullValue())
		          .body("etag", CoreMatchers.isA(String.class));
				 
		       break;
		      case 409:
		        response.then()
		          .contentType("application/json");
		        break;
		      case 401:
		        response.then()
		          .contentType("application/json");
		        break;
		      case 405:
		        response.then()
		          .contentType("application/json");
		        break;
		      case 403:
		        response.then()
		          .contentType("application/json");
		        break;
		      default:
		        assertTrue("Status code not defined: " + response.getStatusLine(), false);
		    }
		} catch (RuntimeException ex) {
			System.err.println("Validation results: " + ex.getMessage());
			fail("Validation failed");
		}
	  }
	
	@Test
	@Ignore
	  public void v1publiccharactersGETTest2() {
	    
	    Response response = RestAssured
	      .given()
	        .param("orderBy", "-name")
	        .param("name", "Batman")
	        .param("ts", "1")
	        .param("apikey", "54e348a2843224f54203607a58f18dae")
	        .param("hash", "3f4fc52453eb425339ca7acc493ac20d")
	        .filter(new RequestLoggingFilter(fileOutPutStream))		// Send logs to a filter
			.filter(validationFilter)								// OAI Schema validation
	        .get("/v1/public/characters");
	    switch(response.getStatusCode()) {
	      case 200:
	        response.then()
	          .contentType("application/json")
	          .body("code", CoreMatchers.notNullValue())
	          .body("code", CoreMatchers.isA(Integer.class))
	          .body("status", CoreMatchers.notNullValue())
	          .body("status", CoreMatchers.isA(String.class))
	          .body("copyright", CoreMatchers.notNullValue())
	          .body("copyright", CoreMatchers.isA(String.class))
	          .body("attributionText", CoreMatchers.notNullValue())
	          .body("attributionText", CoreMatchers.isA(String.class))
	          .body("attributionHTML", CoreMatchers.notNullValue())
	          .body("attributionHTML", CoreMatchers.isA(String.class))
	          .body("data", CoreMatchers.notNullValue())
	          .body("data", CoreMatchers.isA(Object.class))
	          .body("etag", CoreMatchers.notNullValue())
	          .body("etag", CoreMatchers.isA(String.class));        
	       break;
	      case 409:
	        response.then()
	          .contentType("application/json");
	        break;
	      case 401:
	        response.then()
	          .contentType("application/json");
	        break;
	      case 405:
	        response.then()
	          .contentType("application/json");
	        break;
	      case 403:
	        response.then()
	          .contentType("application/json");
	        break;
	      default:
	        assertTrue("Status code not defined: " + response.getStatusLine(), false);
	    }
	  }

}
