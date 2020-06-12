package es.us.isa.restest.testcases.restassured.filters;

import es.us.isa.restest.testcases.restassured.filters.StatusCode5XXFilter;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.fail;

public class StatusCode5XXFilterTest {

    private final StatusCode5XXFilter statusCode5XXFilter = new StatusCode5XXFilter();

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "http://anapioficeandfire.com/";
    }

    @Test
    public void shouldNotBeStatus5XX() {

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .queryParam("name", "Cersei Lannister")
                    .filter(statusCode5XXFilter)
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
    public void shouldBeStatus5XX() {
        RestAssured.baseURI = "http://the-internet.herokuapp.com/";
        Response response = RestAssured
                .given()
                .log().all()
                .filter(statusCode5XXFilter)
                .when()
                .get("/status_codes/500");

        response.then().log().all();

        fail("This test should not validate the response");
    }
}
