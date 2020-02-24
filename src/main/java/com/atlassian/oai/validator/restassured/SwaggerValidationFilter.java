package com.atlassian.oai.validator.restassured;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReportFormatter;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.Objects;

import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;

/**
 * A {@link Filter} that performs Swagger API validation on a request/response interaction.
 * <p>
 * To use, simply add it as a filter to your rest-assured given-when-then interaction:
 * <pre>
 *     private final SwaggerValidationFilter validationFilter = new SwaggerValidationFilter(SWAGGER_JSON_URL);
 *     ...
 *     given()
 *          .filter(validationFilter)
 *     .when()
 *          .get("/my/path")
 *     .then()
 *          .assertThat()
 *          .statusCode(200);
 * </pre>
 * <p>
 * If validation fails, a {@link SwaggerValidationException} will be thrown describing the validation failure.
 */
public class SwaggerValidationFilter implements Filter {

    private final SwaggerRequestResponseValidator validator;

    public SwaggerValidationFilter(final String swaggerJsonUrl) {
        requireNonEmpty(swaggerJsonUrl, "A Swagger URL is required");

        this.validator = SwaggerRequestResponseValidator.createFor(swaggerJsonUrl).build();
    }

    public SwaggerValidationFilter(final SwaggerRequestResponseValidator validator) {
        Objects.requireNonNull(validator, "A validator is required");

        this.validator = validator;
    }

    @Override
    public Response filter(final FilterableRequestSpecification requestSpec,
                           final FilterableResponseSpecification responseSpec,
                           final FilterContext ctx) {

        final Response response = ctx.next(requestSpec, responseSpec);

        final ValidationReport validationReport =
                validator.validate(RestAssuredRequest.of(requestSpec), RestAssuredResponse.of(response));

        if (validationReport.hasErrors()) {
            throw new SwaggerValidationException(validationReport);
        }

        return response;
    }

    static class SwaggerValidationException extends RuntimeException {
        public SwaggerValidationException(final ValidationReport report) {
            super(ValidationReportFormatter.format(report));
        }
    }
}
