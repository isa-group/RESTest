package es.us.isa.rester.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class JSONManagerTest {

    @Test
    public void testJsonParse() {
        List<String> jsonPaths = new ArrayList<>();
        jsonPaths.add("src/test/resources/jsonData/jsonSample.json");
        jsonPaths.add("src/test/resources/jsonData/jsonSample2.json");

        List<Object> jsonObjects = JSONManager.readValues(jsonPaths);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> objectMap = objectMapper.convertValue(jsonObjects.get(0), Map.class);

        System.out.println(objectMap.get("id"));
        System.out.println(objectMap.get("name"));
        System.out.println(objectMap.get("photoUrls"));
        System.out.println(objectMap.get("category"));

        assertEquals("Wrong number of elements", 2, jsonObjects.size());
        assertEquals("Wrong id field", 1000, objectMap.get("id"));
        assertTrue("Wrong name field", objectMap.get("name").equals("Toby"));
    }
}