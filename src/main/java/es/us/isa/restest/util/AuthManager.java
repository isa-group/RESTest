package es.us.isa.restest.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static es.us.isa.restest.util.FileManager.readFile;
import static java.lang.System.exit;

/**
 * This class manages multiple API keys for a single API. Given a JSON file
 * containing all API keys, the AuthManager automatically
 * selects one of them randomly.<br/><br/>
 *
 * The format of the JSON file should be as follows:<br/>
 * <pre>
 * {
 *     "APIKEY_PARAMETER_NAME": [
 *         "APIKEY_VALUE_1",
 *         "APIKEY_VALUE_2",
 *         "APIKEY_VALUE_3"
 *     ]
 * }
 * </pre>
 */
public class AuthManager {

    private final String apikeysBasePath = "src/main/resources/auth/";
    private String apikeysPath;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Map.Entry<String, List<String>> apikeys;
    private int counter;

    public AuthManager(String apikeysRelativePath) {
        apikeysPath = apikeysBasePath + apikeysRelativePath;
        this.counter = 0;
        try {
            apikeys = objectMapper.readValue(readFile(apikeysPath), new TypeReference<Map.Entry<String, List<String>>>(){});
        } catch (IOException e) {
            System.err.println("Error parsing APIkeys file: " + apikeysPath + ". Message: " + e.getMessage());
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Get API key.
     */
    public String getApikey() {
        this.counter = this.apikeys.getValue().size() == this.counter? 0 : this.counter;
        String apiKey = this.apikeys.getValue().get(counter);
        counter++;
        return apiKey;
    }

    /**
     * Get name of the parameter that represents an API key (e.g., "key", "apikey", etc.).
     */
    public String getApikeyName() {
        return  apikeys.getKey();
    }
}
