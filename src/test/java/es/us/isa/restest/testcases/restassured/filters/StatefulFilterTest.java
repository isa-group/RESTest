package es.us.isa.restest.testcases.restassured.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.util.FileManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import static es.us.isa.restest.util.FileManager.readFile;
import static org.junit.Assert.*;

public class StatefulFilterTest {

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "http://anapioficeandfire.com/";
    }

    @Test
    public void shouldSaveResponsesInJson() {
        String dirPath = "src/test/resources/restest-test-resources";
        StatefulFilter filter = new StatefulFilter(dirPath);
        filter.setOperation("GET", "/api/characters/{character-id}");

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

        assertTrue("The JSON file was not created", FileManager.checkIfExists(dirPath + "/stateful_data.json"));

        FileManager.deleteFile(dirPath + "/stateful_data.json");
    }

    @Test
    public void shouldNotRepeatValuesInJson() {
        String dirPath = "src/test/resources/restest-test-resources";
        StatefulFilter filter = new StatefulFilter(dirPath);
        filter.setOperation("GET", "/api/characters/{character-id}");

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

        assertTrue("The JSON file was not created", FileManager.checkIfExists(dirPath + "/stateful_data.json"));
        String jsonContent = readFile(dirPath + "/stateful_data.json");

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

        assertEquals("The JSON file should not have been updated", jsonContent, readFile(dirPath + "/stateful_data.json"));

        FileManager.deleteFile(dirPath + "/stateful_data.json");
    }
}
