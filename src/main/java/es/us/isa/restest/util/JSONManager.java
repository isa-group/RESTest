package es.us.isa.restest.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.util.FileManager.readFile;

public class JSONManager {

    private static final Logger logger = LogManager.getLogger(JSONManager.class.getName());

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<Object> readMultipleJSONs(List<String> jsonPaths) {
        List<Object> values = new ArrayList<Object>();
        Object jsonData;

        // For each path, read JSON file
        for (String jsonPath: jsonPaths) {
            jsonData = readJSON(jsonPath);
            if (jsonData != null)
                values.add(jsonData);
        }

        return values;
    }

    public static Object readJSON(String jsonPath) {
        JsonNode jsonData = null;
        try {
            jsonData = objectMapper.readTree(new File(jsonPath));
        } catch (IOException ex) {
            logger.error("Error parsing JSON file: {}", jsonPath);
            logger.error("Exception: ", ex);
        }
        return jsonData;
    }

    public static Object readJSONFromString(String json) {
        JsonNode jsonData = null;
        try {
            jsonData = objectMapper.readTree(json);
        } catch (IOException ex) {
            logger.error("Error parsing JSON String: \n {}", json);
            logger.error("Exception: ", ex);
        }
        return jsonData;
    }

    public static String getStringFromJSON(JsonNode node) {
        String json = null;
        try {
            json = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            logger.error("Error parsing JSON: {}", node);
            logger.error("Exception: ", ex);
        }
        return json;
    }
}