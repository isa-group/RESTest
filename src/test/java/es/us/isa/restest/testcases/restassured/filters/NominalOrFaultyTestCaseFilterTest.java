package es.us.isa.restest.testcases.restassured.filters;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.fail;

// The parameters used in the HTTP requests correspond to Bikewise
public class NominalOrFaultyTestCaseFilterTest {

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "https://restest-tests-server.herokuapp.com";
    }

    @Test
    public void shouldValidateTestCaseIsNominal() {

        NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter(false, true, "none");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .queryParam("per_page", "13")
                    .queryParam("incident_type", "chop_shop")
                    .queryParam("proximity", "lanthanum")
                    .queryParam("proximity_square", "33")
                    .filter(nominalOrFaultyTestCaseFilter)
                    .when()
                    .get("/statuscodes/200");

            response.then().log().all();
            System.out.println("Test passed.");
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }
    }

    @Test
    public void shouldValidateTestCaseIsFaulty() {

        NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter(true, true, "individual_parameter_constraint");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .queryParam("per_page", "13")
                    .queryParam("incident_type", "abc")
                    .queryParam("proximity", "lanthanum")
                    .queryParam("proximity_square", "33")
                    .filter(nominalOrFaultyTestCaseFilter)
                    .when()
                    .get("/statuscodes/400");

            response.then().log().all();
            System.out.println("Test passed.");
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }
    }

    @Test(expected=RuntimeException.class)
    public void conformanceErrorFoundTestNotNominal() {

        NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter(false, true, "none");

        Response response = RestAssured
                .given()
                .log().all()
                .queryParam("per_page", "13")
                .queryParam("incident_type", "abc")
                .queryParam("proximity", "lanthanum")
                .queryParam("proximity_square", "33")
                .filter(nominalOrFaultyTestCaseFilter)
                .when()
                .get("/statuscodes/400");

        response.then().log().all();
        fail("This test shouldn't be nominal");
    }

    @Test(expected=RuntimeException.class)
    public void conformanceErrorFoundTestNotFaulty() {

        NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter(true, true, "individual_parameter_constraint");

        Response response = RestAssured
                .given()
                .log().all()
                .queryParam("per_page", "13")
                .queryParam("incident_type", "chop_shop")
                .queryParam("proximity", "hey")
                .queryParam("proximity_square", "33")
                .filter(nominalOrFaultyTestCaseFilter)
                .when()
                .get("/statuscodes/200");

        response.then().log().all();
        fail("This test shouldn't be faulty");
    }
}
