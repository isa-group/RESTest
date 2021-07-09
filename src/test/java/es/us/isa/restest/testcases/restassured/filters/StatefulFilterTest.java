package es.us.isa.restest.testcases.restassured.filters;

import es.us.isa.restest.util.FileManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StatefulFilterTest {

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "http://anapioficeandfire.com/";
    }

    @Test
    public void shouldSaveResponsesInJson() {
        String dirPath = "src/test/resources/restest-test-resources";
        StatefulFilter filter = new StatefulFilter(dirPath);
        filter.setOperationId("prueba");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .get("/api/characters/823");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        assertTrue("The JSON file was not created", FileManager.checkIfExists(dirPath + "/prueba_data.json"));

        FileManager.deleteFile(dirPath + "/prueba_data.json");
    }
}
