package es.us.isa.restest.testcases.restassured.filters;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.JsonValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.RestAssuredRequest;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;

/**
 * REST-Assured filter for validating ONLY responses from the API. The original
 * Atlassian OpenApiValidationFilter validates both the request and response. We
 * only want to validate the response, since we may intentionally send invalid inputs
 * to the API in order to create faulty test cases. Request body validation errors
 * (e.g. omitting a required property) will be whitelisted, while other errors will
 * be emitted as warnings.
 *
 * @author Alberto Martin-Lopez
 */
public class ResponseValidationFilter implements OrderedFilter {
    private final OpenApiInteractionValidator validator;

    public ResponseValidationFilter(final String specUrlOrDefinition) {
        requireNonEmpty(specUrlOrDefinition, "A spec is required");

        this.validator = OpenApiInteractionValidator.createFor(specUrlOrDefinition).build();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        final Response response = ctx.next(requestSpec, responseSpec);
        final Request restAssuredRequest = RestAssuredRequest.of(requestSpec);

        filterValidation(response, restAssuredRequest.getPath(), restAssuredRequest.getMethod().toString());

        return response;
    }

    // If OAS validation error is found, throw exception
    public void filterValidation(Response response, String path, String method) {
        final ValidationReport validationReport = validator.validateResponse(path, Request.Method.valueOf(method), RestAssuredResponse.of(response));
        if (validationReport.hasErrors())
            throw new OpenApiValidationException(validationReport);
    }

    public static class OpenApiValidationException extends RuntimeException {
        public OpenApiValidationException(final ValidationReport report) {
            super(JsonValidationReportFormat.getInstance().apply(report));
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-3; // Fourth lowest priority of all filters, so it runs fourth-to-last before sending the request and fourth after sending it
    }
}
