package es.us.isa.restest.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;
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
 *     "APIKEY_PARAMETER_NAME_1": [
 *         "APIKEY_VALUE_1",
 *         "APIKEY_VALUE_2",
 *         "APIKEY_VALUE_3"
 *     ],
 *     "APIKEY_PARAMETER_NAME_2": [
 *          "APIKEY_VALUE_1",
 *          "APIKEY_VALUE_2",
 *          "APIKEY_VALUE_3"
 *    ],
 *    .
 *    .
 *    .
 *    "APIKEY_PARAMETER_NAME_N": [
 *          "APIKEY_VALUE_1",
 *          "APIKEY_VALUE_2",
 *          "APIKEY_VALUE_3"
 *    ]
 * }
 * </pre>
 * <b>NOTE:</b> You have to specify the auth keys orderly, because the AuthManager will
 * select them in that way. In other words, if you have the following JSON:
 * <pre>
 * {
 *     "apikey_1": [
 *          "abc",
 *          "def"
 *     ],
 *     "apikey_2": [
 *          "ghi",
 *          "jkl"
 *     ]
 * }
 * </pre>
 * the AuthManager will use <pre>apikey_1 = abc, apikey_2 = ghi</pre> or
 * <pre>apikey_1 = def, apikey_2 = jkl</pre> as auth keys.<br/><br/>
 *
 * <b>EVERY ARRAY OF AUTH KEYS MUST HAVE THE SAME SIZE.</b> If not, you will
 * get an exception.
 *
 */
public class AuthManager {

    private final String authBasePath = "src/main/resources/auth/";
    private String authPath;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, List<String>> authProperties;
    private int counter;
    private int itCounter;  /* It resets to zero when the algorithm obtains an auth key for every parameter.
    When this happens, the algorithm knows that it has provided all the auth params requested for an operation,
    so it augments 'counter'. */

    public AuthManager(String authRelativePath) {
        authPath = authBasePath + authRelativePath;
        this.counter = 0;
        this.itCounter = 0;
        try {
            authProperties = objectMapper.readValue(readFile(authPath), new TypeReference<Map<String, List<String>>>(){});
        } catch (IOException e) {
            System.err.println("Error parsing authProperties file: " + authPath + ". Message: " + e.getMessage());
            e.printStackTrace();
            exit(1);
        }
    }

    /**
     * Get auth key.
     * @param propertyName the name of the wanted auth key.
     */
    public String getAuthProperty(String propertyName) {
        itCounter = itCounter == authProperties.size()? 0 : itCounter;
        if(itCounter == 0) {
            int size = 0;
            for(Map.Entry<String, List<String>> property : this.authProperties.entrySet()) {
                size = property.getValue().size();
                break;
            }
            this.counter = size == this.counter? 0 : this.counter;
        }

        String authProperty = this.authProperties.get(propertyName).get(counter);
        if(itCounter == authProperties.size() - 1) counter++;
        itCounter++;
        return authProperty;
    }

    /**
     * Get name of the parameters that represent the auth keys (e.g., "key", "apikey", etc.).
     */
    public Collection<String> getAuthPropertyNames() {
        return  authProperties.keySet();
    }
}
