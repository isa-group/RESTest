package es.us.isa.restest.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class JSONManagerTest {

    @Test
    public void testJsonParse() {
        List<String> jsonPaths = new ArrayList<>();
        jsonPaths.add("src/test/resources/jsonData/jsonSample.json");
        jsonPaths.add("src/test/resources/jsonData/jsonSample2.json");

        List<Object> jsonObjects = JSONManager.readMultipleJSONs(jsonPaths);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> objectMap1 = objectMapper.convertValue(jsonObjects.get(0), Map.class);
        Map<String, Object> objectMap2 = objectMapper.convertValue(jsonObjects.get(1), Map.class);

        assertEquals("Wrong number of elements", 2, jsonObjects.size());
        assertEquals("Wrong id field", 1000, objectMap1.get("id"));
        assertTrue("Wrong name field", objectMap1.get("name").equals("Toby"));
        assertTrue("Wrong name field", objectMap2.get("name").equals("Max"));
    }

    @Test
    public void readJSONFromStringTest() {
        String json = "{\"id\": 1000, \"name\": \"Toby\"}";

        Object jsonObject = JSONManager.readJSONFromString(json);

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> objectMap = objectMapper.convertValue(jsonObject, Map.class);

        assertEquals("Wrong id field", 1000, objectMap.get("id"));
        assertEquals("Wrong name field", "Toby", objectMap.get("name"));
    }

    @Test
    public void getStringFromJSONTest() throws JsonProcessingException {
        String json = "{\"id\":1000,\"name\":\"Toby\"}";

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(json);
        String jsonContent = JSONManager.getStringFromJSON(node);

        assertEquals(json, jsonContent);
    }
}