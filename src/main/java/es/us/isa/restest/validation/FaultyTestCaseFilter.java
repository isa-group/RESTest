package es.us.isa.restest.validation;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
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
 * REST-Assured filter to assert that the status code is between 400 and 500. This
 * is expected when the test case is faulty.
 */
public class FaultyTestCaseFilter implements OrderedFilter {
    private final SwaggerRequestResponseValidator validator;
    private Boolean faultyTestCase; // NOTE: If this is false, the test case may still be faulty, but we don't know a priori

    public FaultyTestCaseFilter(String specUrlOrDefinition) {
        requireNonEmpty(specUrlOrDefinition, "A Swagger URL is required");

        this.validator = SwaggerRequestResponseValidator.createFor(specUrlOrDefinition).build();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        if (response.getStatusCode() < 400 || response.getStatusCode() >= 500) { // If test case returned status code not between 400 and 500
            if (faultyTestCase) { // If test case is faulty FOR SURE, throw exception
                throw new RuntimeException("This faulty test case was expecting a 4XX status code, but received other. Conformance error found.");
            } else { // Test case may still be faulty:
                // Validate only request and, if it does not conform to Swagger schema, throw exception
                ValidationReport validationReport = this.validator.validateOnlyRequest(RestAssuredRequest.of(requestSpec), RestAssuredResponse.of(response));
                if (validationReport.hasErrors()) {
                    throw new RuntimeException("This faulty test case was expecting a 4XX status code, but received other. Conformance error found.");
                }
            }
        }

        return response;
    }

    public Boolean getFaultyTestCase() {
        return faultyTestCase;
    }

    public void setFaultyTestCase(Boolean faultyTestCase) {
        this.faultyTestCase = faultyTestCase;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-2; // Third lowest priority of all filters, so it runs third-to-last before sending the request and third after sending it
    }
}
