package es.us.isa.restest.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static es.us.isa.restest.util.FileManager.readFile;
import static java.lang.System.exit;

/**
 * This class manages multiple API keys or auth headers for a single API. Given
 * a JSON file containing all API keys or auth headers, the AuthManager automatically
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

    private final String authBasePath = "src/main/resources/auth/";
    private String authPath;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Map.Entry<String, List<String>> authProperties;
    private int counter;

    public AuthManager(String authRelativePath) {
        authPath = authBasePath + authRelativePath;
        this.counter = 0;
        try {
            authProperties = objectMapper.readValue(readFile(authPath), new TypeReference<Map.Entry<String, List<String>>>(){});
        } catch (IOException e) {
            System.err.println("Error parsing authProperties file: " + authPath + ". Message: " + e.getMessage());
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Get API key.
     */
    public String getAuthProperty() {
        this.counter = this.authProperties.getValue().size() == this.counter? 0 : this.counter;
        String authProperty = this.authProperties.getValue().get(counter);
        counter++;
        return authProperty;
    }

    /**
     * Get name of the parameter that represents an API key (e.g., "key", "apikey", etc.).
     */
    public String getAuthPropertyName() {
        return  authProperties.getKey();
    }
}
