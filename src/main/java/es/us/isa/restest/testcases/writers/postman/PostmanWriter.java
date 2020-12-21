package es.us.isa.restest.testcases.writers.postman;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.postman.pojos.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** This class defines a test writer for Postman. It creates a JSON file with a collection of
 * Postman requests.
 *
 * @author Alberto Martin-Lopez
 *
 */
public class PostmanWriter implements IWriter {

    private String collectionName;                      // Identifier for the test suite. Random string if not set
    private String jsonPath = "src/test/resources";     // Path to write JSON file (Postman collection)
    private String baseURI;							    // API base URI

    private static Logger logger = LogManager.getLogger(PostmanWriter.class.getName());

    private static final String postmanSchema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

    public PostmanWriter(String baseUri) {
        collectionName = "postman_" + RandomStringUtils.randomAlphanumeric(10);
        this.baseURI = baseUri;
    }

    @Override
    public void write(Collection<TestCase> testCases) {
        PostmanCollectionObject postmanTestSuite = createPostmanTestSuite(testCases);
        saveToFile(jsonPath, collectionName, postmanTestSuite);
    }

    private PostmanCollectionObject createPostmanTestSuite(Collection<TestCase> testCases) {
        PostmanCollectionObject postmanTestSuite = new PostmanCollectionObject();

        // Test suite info
        Info info = new Info(UUID.randomUUID().toString(), collectionName, postmanSchema);
        postmanTestSuite.setInfo(info);

        // Test cases
        List<Item> postmanRequests = new ArrayList<>();

        for (TestCase tc: testCases) {
            Item postmanRequest = new Item();

            // Request ID
            postmanRequest.setName(tc.getId());

            // Request content
            Request request = new Request();

            // Request method
            request.setMethod(tc.getMethod().toString());

            // Request headers
            request.setHeader(getPostmanHeaders(tc));

            // Request body
            if ((tc.getBodyParameter() != null && !tc.getBodyParameter().equals("")) || !tc.getFormParameters().isEmpty())
                request.setBody(getPostmanBody(tc));

            // Request URL
            request.setUrl(getPostmanUrl(tc));

            postmanRequest.setRequest(request);

            // Response property is already set

            postmanRequests.add(postmanRequest);
        }

        postmanTestSuite.setItem(postmanRequests);

        return postmanTestSuite;
    }

    private Url getPostmanUrl(TestCase tc) {
        Url url = new Url();
        String pathParamsString = tc.getPath();
        for (Map.Entry<String, String> pathParam : tc.getPathParameters().entrySet())
            pathParamsString = pathParamsString.replace("{" + pathParam.getKey() + "}", pathParam.getValue());
        StringBuilder queryParamsString = new StringBuilder("?");
        List<Query> queryParams = new ArrayList<>();
        for (Map.Entry<String, String> queryParam : tc.getQueryParameters().entrySet()) {
            String queryParamName = null;
            String queryParamValue;
            try {
                queryParamName = queryParam.getKey();
                queryParamValue = URLEncoder.encode(queryParam.getValue(), StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                logger.error("Query parameter {} could not be URLencoded. Adding it without encoding.", queryParam.getKey());
                queryParamValue = queryParam.getValue();
            }
            queryParams.add(new Query(queryParamName, queryParamValue));
            queryParamsString.append(queryParamName).append("=").append(queryParamValue).append("&");
        }

        String fullUrl = baseURI.replaceAll("/$", "") + pathParamsString + queryParamsString.substring(0, queryParamsString.length()-1);

        url.setRaw(fullUrl);
        url.setProtocol(baseURI.split("://")[0]);
        url.setHost(Arrays.asList(fullUrl.split("://")[1].split("/")[0].split("\\.")));
        url.setPath(Arrays.asList(fullUrl.split("/", 4)[3].split("\\?")[0].split("/")));
        url.setQuery(queryParams);

        return url;
    }

    private Body getPostmanBody(TestCase tc) {
        Body body = new Body();
        if (tc.getInputFormat().equals("application/json")) {
            body.setMode("raw");
            body.setRaw(tc.getBodyParameter());
        } else if (tc.getInputFormat().equals("application/x-www-form-urlencoded")) {
            body.setMode("urlencoded");
            List<Urlencoded> formParameters = new ArrayList<>();
            for (Map.Entry<String, String> tcFormParameter: tc.getFormParameters().entrySet()) {
                formParameters.add(new Urlencoded(tcFormParameter.getKey(), tcFormParameter.getValue(), "text"));
            }
            body.setUrlencoded(formParameters);
        }
        return body;
    }

    private List<Header> getPostmanHeaders(TestCase tc) {
        List<Header> headers = new ArrayList<>();
        if (tc.getInputFormat() != null)
            headers.add(new Header("Content-Type", tc.getInputFormat(), null));
        for (Map.Entry<String, String> tcHeader: tc.getHeaderParameters().entrySet())
            headers.add(new Header(tcHeader.getKey(), tcHeader.getValue(), null));
        return headers;
    }

    private void saveToFile(String path, String fileName, PostmanCollectionObject content) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(new File(path + "/" + fileName + ".postman_collection.json"), content);
        } catch (IOException e) {
            logger.error("Error exporting Postman test suite to JSON: {}", e.getMessage());
            logger.error(e);
        }
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    public String getBaseURI() {
        return baseURI;
    }

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }
}
