package es.us.isa.restest.testcases.restassured.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import es.us.isa.restest.util.FileManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static es.us.isa.restest.util.FileManager.readFile;
import static org.junit.Assert.*;

public class StatefulFilterTest {

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "https://restest-tests-server.herokuapp.com";
    }

    @Test
    public void shouldSaveResponsesInJsonAndStoreCorrectValues() {
        String dirPath = "src/test/resources/restest-test-resources";
        StatefulFilter filter = new StatefulFilter(dirPath);
        filter.setOperation("GET", "/stateful/filter/{id}");

        FileManager.deleteFile(dirPath + "/stateful_data.json");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .pathParam("id", "1")
                    .get("/stateful/filter/{id}");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        assertTrue("The JSON file was not created", FileManager.checkIfExists(dirPath + "/stateful_data.json"));
        try {
            JsonNode dict = new ObjectMapper().readTree(new File(dirPath + "/stateful_data.json"));
            JsonNode operationDict = dict.get("GET/stateful/filter/{id}");

            // Assert all elements saved in stateful_data.json
            assertNotNull(operationDict.get("object1.numberProp1"));
            assertEquals(4, operationDict.get("object1.numberProp1").size());
            assertTrue(operationDict.get("object1.numberProp1").get(0).isNumber());
            assertEquals(1, operationDict.get("object1.numberProp1").get(0).intValue());
            assertTrue(operationDict.get("object1.numberProp1").get(1).isNumber());
            assertEquals(2, operationDict.get("object1.numberProp1").get(1).intValue());
            assertTrue(operationDict.get("object1.numberProp1").get(2).isNumber());
            assertEquals(3, operationDict.get("object1.numberProp1").get(2).intValue());
            assertTrue(operationDict.get("object1.numberProp1").get(3).isTextual());
            assertEquals("3", operationDict.get("object1.numberProp1").get(3).textValue());
            assertNotNull(operationDict.get("object1.numberProp2"));
            assertEquals(1, operationDict.get("object1.numberProp2").size());
            assertTrue(operationDict.get("object1.numberProp2").get(0).isNull());
            assertEquals(null, operationDict.get("object1.numberProp2").get(0).textValue());
            assertNotNull(operationDict.get("stringProp1"));
            assertEquals(1, operationDict.get("stringProp1").size());
            assertTrue(operationDict.get("stringProp1").get(0).isTextual());
            assertEquals("string1", operationDict.get("stringProp1").get(0).textValue());
            assertNotNull(operationDict.get("booleanProp"));
            assertEquals(1, operationDict.get("booleanProp").size());
            assertTrue(operationDict.get("booleanProp").get(0).isBoolean());
            assertEquals(false, operationDict.get("booleanProp").get(0).booleanValue());
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        FileManager.deleteFile(dirPath + "/stateful_data.json");
    }

    @Test
    public void shouldNotRepeatValuesInJson() {
        String dirPath = "src/test/resources/restest-test-resources";
        StatefulFilter filter = new StatefulFilter(dirPath);
        filter.setOperation("GET", "/stateful/filter/{id}");

        FileManager.deleteFile(dirPath + "/stateful_data.json");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .pathParam("id", "1")
                    .get("/stateful/filter/{id}");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        assertTrue("The JSON file was not created", FileManager.checkIfExists(dirPath + "/stateful_data.json"));
        String firstDict = readFile(dirPath + "/stateful_data.json");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .pathParam("id", "1")
                    .get("/stateful/filter/{id}");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        String secondDict = readFile(dirPath + "/stateful_data.json");

        assertEquals("The JSON file should not have been updated", firstDict, secondDict);

        FileManager.deleteFile(dirPath + "/stateful_data.json");
    }

    @Test
    public void shouldNotRepeatValuesInJsonAndUpdate() {
        String dirPath = "src/test/resources/restest-test-resources";
        StatefulFilter filter = new StatefulFilter(dirPath);
        filter.setOperation("GET", "/stateful/filter/{id}");

        FileManager.deleteFile(dirPath + "/stateful_data.json");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .pathParam("id", "1")
                    .get("/stateful/filter/{id}");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        assertTrue("The JSON file was not created", FileManager.checkIfExists(dirPath + "/stateful_data.json"));
        String firstDict = readFile(dirPath + "/stateful_data.json");

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .pathParam("id", "1")
                    .get("/stateful/filter/{id}");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        String secondDict = readFile(dirPath + "/stateful_data.json");

        assertEquals("The JSON file should not have been updated", firstDict, secondDict);

        try {
            Response response = RestAssured
                    .given()
                    .log().all()
                    .filter(filter)
                    .when()
                    .pathParam("id", "2")
                    .get("/stateful/filter/{id}");

            response.then().log().all();
        } catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        String thirdDict = readFile(dirPath + "/stateful_data.json");

        try {
            JsonNode dict = new ObjectMapper().readTree(thirdDict);
            JsonNode operationDict = dict.get("GET/stateful/filter/{id}");

            // Assert all elements saved in stateful_data.json
            assertNotNull(operationDict.get("object1.numberProp1"));
            assertEquals(4, operationDict.get("object1.numberProp1").size());
            assertTrue(operationDict.get("object1.numberProp1").get(0).isNumber());
            assertEquals(1, operationDict.get("object1.numberProp1").get(0).intValue());
            assertTrue(operationDict.get("object1.numberProp1").get(1).isNumber());
            assertEquals(2, operationDict.get("object1.numberProp1").get(1).intValue());
            assertTrue(operationDict.get("object1.numberProp1").get(2).isNumber());
            assertEquals(3, operationDict.get("object1.numberProp1").get(2).intValue());
            assertTrue(operationDict.get("object1.numberProp1").get(3).isTextual());
            assertEquals("3", operationDict.get("object1.numberProp1").get(3).textValue());
            assertNotNull(operationDict.get("object1.numberProp2"));
            assertEquals(2, operationDict.get("object1.numberProp2").size());
            assertTrue(operationDict.get("object1.numberProp2").get(0).isNull());
            assertEquals(null, operationDict.get("object1.numberProp2").get(0).textValue());
            assertTrue(operationDict.get("object1.numberProp2").get(1).isNumber());
            assertEquals(5, operationDict.get("object1.numberProp2").get(1).intValue());
            assertNotNull(operationDict.get("stringProp1"));
            assertEquals(2, operationDict.get("stringProp1").size());
            assertTrue(operationDict.get("stringProp1").get(0).isTextual());
            assertEquals("string1", operationDict.get("stringProp1").get(0).textValue());
            assertTrue(operationDict.get("stringProp1").get(1).isNull());
            assertEquals(null, operationDict.get("stringProp1").get(1).textValue());
            assertNotNull(operationDict.get("booleanProp"));
            assertEquals(2, operationDict.get("booleanProp").size());
            assertTrue(operationDict.get("booleanProp").get(0).isBoolean());
            assertEquals(false, operationDict.get("booleanProp").get(0).booleanValue());
            assertTrue(operationDict.get("booleanProp").get(1).isTextual());
            assertEquals("true", operationDict.get("booleanProp").get(1).textValue());
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            fail(ex.getMessage());
        }

        FileManager.deleteFile(dirPath + "/stateful_data.json");
    }
}
