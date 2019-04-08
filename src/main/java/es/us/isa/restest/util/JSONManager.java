package es.us.isa.restest.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONManager {

    public static List<Object> readValues(List<String> jsonPaths) {
        List<Object> values = new ArrayList<Object>();

        // For each path, read JSON file
        for (String jsonPath: jsonPaths) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(jsonPath)));
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonData = objectMapper.readTree(content);
                values.add(jsonData);
            } catch (IOException ex) {
                System.err.println("Error parsing JSON file: " + jsonPath + ". Message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        return values;
    }
}