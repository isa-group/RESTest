package es.us.isa.restest.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static es.us.isa.restest.util.FileManager.readFile;
import static java.lang.System.exit;

/**
 * This class manages multiple API keys or auth headers for a single API. Given
 * a JSON file containing all API keys or auth headers, the AuthManager automatically
 * selects one of them randomly.<br><br>
 *
 * The format of the JSON file should be as follows:<br>
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
 * <pre>apikey_1 = def, apikey_2 = jkl</pre> as auth keys.<br><br>
 *
 * <b>EVERY ARRAY OF AUTH KEYS MUST HAVE THE SAME SIZE.</b> If not, you will
 * get an exception.
 *
 */
public class AuthManager {

    private static final String AUTH_BASE_PATH = "src/test/resources/auth/";

    // OAuth constants
    private static final String REFRESH_ENDPOINT = "refresh_endpoint";
    private static final String REFRESH_BODY = "refresh_body";
    private static final String REFRESH_HEADERS = "refresh_headers";
    private static final String EXPIRES_IN_PROPERTY = "expires_in_property";
    private static final String RESPONSE_TOKEN_PROPERTY = "response_token_property";
    private static final String OAUTH_HEADER_FORMAT = "oauth_header_format";
    private static final int EXPIRATION_THRESHOLD = 600;

    // AuthManager variables
    private Map<String, List<String>> authProperties;
    private int counter;
    private int itCounter;  /* It resets to zero when the algorithm obtains an auth key for every parameter.
    When this happens, the algorithm knows that it has provided all the auth params requested for an operation,
    so it augments 'counter'. */

    // OAuth JSON properties:
    private String refreshEndpoint;
    private String refreshBody;
    private Map<String, String> refreshHeaders;
    private String expiresInProperty;
    private String responseTokenProperty;
    private String oauthHeaderFormat;

    // Actual OAuth properties
    private Long expiration;
    private String oauthHeader; // Header containing access_token

    private static final Logger logger = LogManager.getLogger(AuthManager.class);

    public AuthManager(String authRelativePath) {
        String authPath = AUTH_BASE_PATH + authRelativePath;
        this.counter = 0;
        this.itCounter = 0;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            authProperties = objectMapper.readValue(readFile(authPath), new TypeReference<Map<String, List<String>>>(){});
        } catch (IOException e) {
            logger.error("Error parsing authProperties file: {}", authPath);
            logger.error("Exception: ", e);
            exit(1);
        }
    }

    public AuthManager(String oauthRelativePath, boolean oauth) {
        String authPath = AUTH_BASE_PATH + oauthRelativePath;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode oauthProperties = objectMapper.readTree(readFile(authPath));
            refreshEndpoint = oauthProperties.get(REFRESH_ENDPOINT).textValue();
            refreshBody = oauthProperties.get(REFRESH_BODY).textValue();
            refreshHeaders = objectMapper.convertValue(oauthProperties.get(REFRESH_HEADERS), new TypeReference<Map<String, String>>(){});
            expiresInProperty = oauthProperties.get(EXPIRES_IN_PROPERTY).textValue();
            responseTokenProperty = oauthProperties.get(RESPONSE_TOKEN_PROPERTY).textValue();
            oauthHeaderFormat = oauthProperties.get(OAUTH_HEADER_FORMAT).textValue();
        } catch (Exception e) {
            logger.error("Error parsing oauthProperties file: {}", authPath);
            logger.error("Exception: ", e);
            throw new IllegalArgumentException(e);
        }
        expiration = nowInSeconds();
    }

    public String getUpdatedOauthHeader() {
        refreshTokenIfNeeded();
        return oauthHeader;
    }

    private void refreshTokenIfNeeded() {
        if (nowInSeconds() > expiration - EXPIRATION_THRESHOLD)
            refreshToken();
    }

    private void refreshToken() {
        String oauthResponse = RestAssured
                .given()
                .headers(refreshHeaders)
                .body(refreshBody)
                .when()
                .post(refreshEndpoint)
                .getBody()
                .asString();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode oauthResponseProperties = objectMapper.readTree(oauthResponse);
            expiration = nowInSeconds() + oauthResponseProperties.get(expiresInProperty).asLong();
            oauthHeader = oauthHeaderFormat.replace(
                    RESPONSE_TOKEN_PROPERTY,
                    oauthResponseProperties.get(responseTokenProperty).textValue()
            );
        } catch (Exception e) {
            logger.error("Error parsing OAuth response: {}", oauthResponse);
            logger.warn("OAuth token not refreshed, will try again next time.");
        }
    }

    private long nowInSeconds() {
        return new Date().getTime() / 1000;
    }

    /**
     * Get auth key.
     * @param propertyName the name of the wanted auth key.
     * @return an auth key
     */
    public String getAuthProperty(String propertyName) {
        itCounter = itCounter == authProperties.size()? 0 : itCounter;
        if(itCounter == 0) {
            int size = this.authProperties.entrySet().iterator().next().getValue().size();
            this.counter = size == this.counter? 0 : this.counter;
        }

        String authProperty = this.authProperties.get(propertyName).get(counter);
        if(itCounter == authProperties.size() - 1) counter++;
        itCounter++;
        return authProperty;
    }

    /**
     * Get name of the parameters that represent the auth keys (e.g., "key", "apikey", etc.).
     * @return the auth property names
     */
    public Collection<String> getAuthPropertyNames() {
        return  authProperties.keySet();
    }

    public Long getExpiration() {
        return expiration;
    }
}
