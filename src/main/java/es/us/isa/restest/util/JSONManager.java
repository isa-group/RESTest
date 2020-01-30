package es.us.isa.restest.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static es.us.isa.restest.util.FileManager.readFile;

public class JSONManager {

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
            String content = readFile(jsonPath);
            ObjectMapper objectMapper = new ObjectMapper();
            jsonData = objectMapper.readTree(content);
        } catch (IOException ex) {
            System.err.println("Error parsing JSON file: " + jsonPath + ". Message: " + ex.getMessage());
            ex.printStackTrace();
        }
        return jsonData;
    }
}