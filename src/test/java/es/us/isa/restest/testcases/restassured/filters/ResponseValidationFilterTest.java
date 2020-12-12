package es.us.isa.restest.testcases.restassured.filters;

import es.us.isa.restest.testcases.restassured.filters.ResponseValidationFilter;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.fail;

public class ResponseValidationFilterTest {

    private static final String OAI_JSON_URL = "src/test/resources/AnApiOfIceAndFire/swagger.yaml";
    private static final String OAI_FAULTY_JSON_URL = "src/test/resources/AnApiOfIceAndFire/swaggerFaulty.yaml";
    private final ResponseValidationFilter validationFilter = new ResponseValidationFilter(OAI_JSON_URL);
    private final ResponseValidationFilter validationFaultyFilter = new ResponseValidationFilter(OAI_FAULTY_JSON_URL);

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "http://anapioficeandfire.com/";
    }

    @Test
    public void shouldValidateJSONResponse() {

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .queryParam("name", "Cersei Lannister")
                    .filter(validationFilter)
                    .when()
                    .get("/api/characters");

            response.then().log().all();
            System.out.println("Test passed.");
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }
    }

    @Test(expected= RuntimeException.class)
    public void shouldNotValidateJSONResponse() {
        Response response = RestAssured
                .given()
                .log().all()
                .queryParam("name", "Petyr Baelish")
                .filter(validationFaultyFilter)
                .when()
                .get("/api/characters");

        response.then().log().all();

        fail("This test should not validate the response");
    }
}
