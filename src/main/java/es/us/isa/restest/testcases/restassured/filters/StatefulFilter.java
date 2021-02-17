package es.us.isa.restest.testcases.restassured.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import es.us.isa.restest.util.FileManager;
import es.us.isa.restest.util.JSONManager;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;

public class StatefulFilter extends RESTestFilter implements OrderedFilter {

    private String specDirPath;
    private String operationId;
    private ObjectMapper objectMapper;

    public StatefulFilter(String specDirPath) {
        requireNonEmpty(specDirPath, "The specification directory path is required");

        this.specDirPath = specDirPath;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        if (response.getStatusCode() < 400) {
            String body = response.getBody().prettyPrint();
            ArrayNode originalNode = objectMapper.createArrayNode();

            if (FileManager.checkIfExists(this.specDirPath + '/' + this.operationId + "_data.json")) {
                originalNode = (ArrayNode) JSONManager.readJSON(this.specDirPath + '/' + this.operationId + "_data.json");
            }
            
            JsonNode newNode = (JsonNode) JSONManager.readJSONFromString(body);

            if (newNode.isArray()) {
                originalNode.addAll((ArrayNode) newNode);
            } else {
                originalNode.add(newNode);
            }

            String newFileData = JSONManager.getStringFromJSON(originalNode);
            FileManager.writeFile(this.specDirPath + '/' + this.operationId + "_data.json", newFileData);
        }
        return response;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-5; // Sixth lowest priority of all filters, so it runs sixth-to-last before sending the request and sixth after sending it
    }
}
