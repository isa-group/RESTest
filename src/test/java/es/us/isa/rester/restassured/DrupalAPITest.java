package es.us.isa.rester.restassured;

import org.hamcrest.CoreMatchers;
import org.junit.Ignore;
import org.junit.Test;

import com.github.fge.jsonschema.SchemaVersion;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import io.restassured.RestAssured;

public class DrupalAPITest {
	
	@Test
	@Ignore
	public void httpRequestDrupal(){
		RestAssured.baseURI = "https://api.github.com";
	    RestAssured
	      .given()
	        .get("/repos/drupal/drupal")
	      .then()
	        .contentType("application/json")
	        .body("id", CoreMatchers.notNullValue());
	}
	
	@Test
	@Ignore
	public void JsonSchemaDrupal() {
		JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.newBuilder()
				.setValidationConfiguration(ValidationConfiguration.newBuilder()
						.setDefaultVersion(SchemaVersion.DRAFTV4).freeze()).freeze();

		RestAssured.baseURI = "https://api.github.com";
		/*
		RestAssured.get("/repos/drupal/drupal")
			.then().assertThat();
				.body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/drupal-schema.json")
					.using(jsonSchemaFactory));
		*/
	}
}
