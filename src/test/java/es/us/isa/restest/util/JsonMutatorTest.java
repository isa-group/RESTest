package es.us.isa.restest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import es.us.isa.jsonmutator.JsonMutator;

public class JsonMutatorTest {

    @Test
    public void test() {
//        System.out.println(System.getProperty("user.dir"));

        // Read JSON file
        String jsonPath = "src/test/resources/JsonMutator/test-object.json";
        String jsonString = "";
        try {
            jsonString = new String(Files.readAllBytes(Paths.get(jsonPath)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create mutator object (optionally change default configuration with the setters)
        JsonMutator mutator = new JsonMutator();
//        mutator.setMutateValueProb(0.5f);

        // Create JsonNode and mutate it
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            System.out.println("Original JSON object:\n.\n.\n.\n");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readValue(jsonNode.toString(), Object.class)));
            JsonNode mutatedJsonNode1 = mutator.mutateJson(jsonNode, true);
            System.out.println(".\n.\n.\nSingle order mutation:\n.\n.\n.\n");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readValue(mutatedJsonNode1.toString(), Object.class)));
            JsonNode mutatedJsonNode2 = mutator.mutateJson(jsonNode, false);
            System.out.println(".\n.\n.\nMultiple order mutation:\n.\n.\n.\n");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readValue(mutatedJsonNode2.toString(), Object.class)));
            JsonNode mutatedJsonNode3 = mutator.mutateJson(jsonNode, true);
            System.out.println(".\n.\n.\nSingle order mutation:\n.\n.\n.\n");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readValue(mutatedJsonNode3.toString(), Object.class)));
        } catch (IOException e) {
//            e.printStackTrace();
            System.out.println("Unable to get properties, it is not formatted in JSON");
        }

    }
}
