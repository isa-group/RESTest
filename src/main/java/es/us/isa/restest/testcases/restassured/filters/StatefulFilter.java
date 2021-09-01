package es.us.isa.restest.testcases.restassured.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;

public class StatefulFilter extends RESTestFilter implements OrderedFilter {

    private String specDirPath;
    private String operationMethod;
    private String operationPath;
    private ObjectMapper objectMapper;

    private static final Logger logger = LogManager.getLogger(StatefulFilter.class.getName());

    public StatefulFilter(String specDirPath) {
        requireNonEmpty(specDirPath, "The specification directory path is required");

        this.specDirPath = specDirPath;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        if (response.getStatusCode() < 400) {
            File jsonFile = new File(this.specDirPath + '/' + "stateful_data.json");
            String body = response.getBody().asString();
            Map<String, Map<String, List<JsonNode>>> allValues = new HashMap<>();

            try {
                if (jsonFile.exists())
                    allValues = objectMapper.readValue(jsonFile, new TypeReference<Map<String, Map<String, List<JsonNode>>>>() {});
                allValues.putIfAbsent(operationMethod + operationPath, new HashMap<>());
                JsonNode bodyNode = objectMapper.readTree(body);
                addResponseBodyValues(allValues.get(operationMethod + operationPath), bodyNode, "");
                objectMapper.writeValue(jsonFile, allValues);
            } catch (IOException e) {
                logger.warn("The response body could not be saved to the JSON: {}", e.getMessage());
            }
        }
        return response;
    }

    private void addResponseBodyValues(Map<String, List<JsonNode>> allValues, JsonNode bodyNode, String prefix) {
        if (bodyNode.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = bodyNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String newPrefix = "".equals(prefix)? entry.getKey() : prefix + '.' + entry.getKey();
                addResponseBodyValues(allValues, entry.getValue(), newPrefix);
            }
        } else if (bodyNode.isArray()) {
            for (Iterator<JsonNode> it = bodyNode.elements(); it.hasNext(); ) {
                addResponseBodyValues(allValues, it.next(), prefix);
            }
        } else if (bodyNode.isValueNode()) {
            allValues.putIfAbsent(prefix, new ArrayList<>());
            if (!allValues.get(prefix).contains(bodyNode))
                allValues.get(prefix).add(bodyNode);
        }

    }

    public void setOperation(String operationMethod, String operationPath) {
        this.operationMethod = operationMethod;
        this.operationPath = operationPath;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // Lowest priority of all filters, so it runs last before sending the request and first after sending it
    }
}
