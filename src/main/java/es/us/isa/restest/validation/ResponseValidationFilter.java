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
        StringUtils.requireNonEmpty(specUrlOrDefinition, "A spec is required");
        this.validator = SwaggerRequestResponseValidator.createFor(specUrlOrDefinition)
                .withLevelResolver(
                        LevelResolver.create()
                                .withLevel("validation.request", ValidationReport.Level.WARN)
                                .build()
                )
                .withWhitelist(
                        ValidationErrorsWhitelist.create()
                                .withRule(
                                        "Do not consider request schema validation errors as failures",
                                        allOf(
                                                isRequest(),
                                                anyOf(
                                                        // All validation keywords from JSON schema:
                                                        // Any instance type:
                                                        messageHasKey("validation.schema.const"),
                                                        messageHasKey("validation.schema.type"),
                                                        messageHasKey("validation.schema.enum"),
                                                        messageHasKey("validation.schema.definitions"),
                                                        // Numeric:
                                                        messageHasKey("validation.schema.multipleOf"),
                                                        messageHasKey("validation.schema.maximum"),
                                                        messageHasKey("validation.schema.exclusiveMaximum"),
                                                        messageHasKey("validation.schema.minimum"),
                                                        messageHasKey("validation.schema.exclusiveMinimum"),
                                                        // Strings:
                                                        messageHasKey("validation.schema.maxLength"),
                                                        messageHasKey("validation.schema.minLength"),
                                                        messageHasKey("validation.schema.pattern"),
                                                        messageHasKey("validation.schema.format"),
                                                        messageHasKey("validation.schema.formatMaximum"),
                                                        messageHasKey("validation.schema.formatMinimum"),
                                                        messageHasKey("validation.schema.formatExclusiveMaximum"),
                                                        messageHasKey("validation.schema.formatExclusiveMinimum"),
                                                        // Arrays:
                                                        messageHasKey("validation.schema.items"),
                                                        messageHasKey("validation.schema.additionalItems"),
                                                        messageHasKey("validation.schema.maxItems"),
                                                        messageHasKey("validation.schema.minItems"),
                                                        messageHasKey("validation.schema.contains"),
                                                        messageHasKey("validation.schema.minContains"),
                                                        messageHasKey("validation.schema.maxContains"),
                                                        messageHasKey("validation.schema.uniqueItems"),
                                                        // Objects:
                                                        messageHasKey("validation.schema.required"),
                                                        messageHasKey("validation.schema.dependentRequired"),
                                                        messageHasKey("validation.schema.maxProperties"),
                                                        messageHasKey("validation.schema.minProperties"),
                                                        messageHasKey("validation.schema.additionalProperties"),
                                                        messageHasKey("validation.schema.properties"),
                                                        messageHasKey("validation.schema.patternProperties"),
                                                        messageHasKey("validation.schema.dependencies"),
                                                        messageHasKey("validation.schema.propertyNames"),
                                                        messageHasKey("validation.schema.patternRequired"),
                                                        // Compound:
                                                        messageHasKey("validation.schema.allOf"),
                                                        messageHasKey("validation.schema.anyOf"),
                                                        messageHasKey("validation.schema.oneOf"),
                                                        messageHasKey("validation.schema.not"),
                                                        messageHasKey("validation.schema.if"),
                                                        messageHasKey("validation.schema.then"),
                                                        messageHasKey("validation.schema.else")

                                                )
                                        )
                                )
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

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-3; // Fourth lowest priority of all filters, so it runs fourth-to-last before sending the request and fourth after sending it
    }
}
