package es.us.isa.restest.validation;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReportFormatter;
import com.atlassian.oai.validator.restassured.RestAssuredRequest;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import com.atlassian.oai.validator.util.StringUtils;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.*;

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
    private final SwaggerRequestResponseValidator validator;

    public ResponseValidationFilter(String specUrlOrDefinition) {
        requireNonEmpty(specUrlOrDefinition, "A Swagger URL is required");

        this.validator = SwaggerRequestResponseValidator.createFor(specUrlOrDefinition).build();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        ValidationReport validationReport = this.validator.validateOnlyResponse(RestAssuredRequest.of(requestSpec), RestAssuredResponse.of(response));
        if (validationReport.hasErrors()) {
            throw new SwaggerValidationException(validationReport);
        } else {
            return response;
        }
    }

    static class SwaggerValidationException extends RuntimeException {
        public SwaggerValidationException(final ValidationReport report) {
            super(ValidationReportFormatter.format(report));
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-3; // Fourth lowest priority of all filters, so it runs fourth-to-last before sending the request and fourth after sending it
    }
}
