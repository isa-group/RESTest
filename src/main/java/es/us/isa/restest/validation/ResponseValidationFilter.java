package es.us.isa.restest.validation;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReportFormatter;
import com.atlassian.oai.validator.restassured.RestAssuredRequest;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import com.atlassian.oai.validator.util.StringUtils;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST-Assured filter for validating ONLY responses from the API. The original
 * Atlassian OpenApiValidationFilter validates both the request and response. We
 * only want to validate the response, since we may intentionally send invalid inputs
 * to the API in order to create faulty test cases. Errors in the request will
 * be emitted as warnings.
 *
 * @author Alberto Martin-Lopez
 */
public class ResponseValidationFilter implements Filter {
    private final SwaggerRequestResponseValidator validator;

    public ResponseValidationFilter(String specUrlOrDefinition) {
        StringUtils.requireNonEmpty(specUrlOrDefinition, "A spec is required");
        this.validator = SwaggerRequestResponseValidator.createFor(specUrlOrDefinition)
                .withLevelResolver(
                        LevelResolver.create()
                                .withLevel("validation.request", ValidationReport.Level.WARN)
                                .build()
                )
                .build();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        ValidationReport validationReport = this.validator.validate(RestAssuredRequest.of(requestSpec), RestAssuredResponse.of(response));
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
}
