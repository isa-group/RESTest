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
 * REST-Assured filter to assert that faulty test cases return a 4XX status code.
 * This may happen in two situations:
 * <ol>
 *     <li>If the test case was made faulty on purpose (e.g., removing a required
 *     parameter).</li>
 *     <li>If the request body does not conform to the Swagger schema. This happens
 *     when the {@link es.us.isa.restest.inputs.perturbation.ObjectPerturbator ObjectPerturbator}
 *     mutates a valid input request body into an invalid one. This is not known
 *     a priori.</li>
 * </ol>
 */
public class PossiblyFaultyTestCaseFilter implements OrderedFilter {
    private final SwaggerRequestResponseValidator validator;
    private Boolean faultyTestCase; // NOTE: If this is false, the test case may still be faulty, but we don't know a priori
    private Boolean dependenciesFulfilled; // Whether this test case fulfills all inter-parameter dependencies or not

    public PossiblyFaultyTestCaseFilter(String specUrlOrDefinition) {
        requireNonEmpty(specUrlOrDefinition, "A Swagger URL is required");

        this.validator = SwaggerRequestResponseValidator.createFor(specUrlOrDefinition).build();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        ValidationReport validationReport = this.validator.validateOnlyRequest(RestAssuredRequest.of(requestSpec), RestAssuredResponse.of(response));
        Boolean inputHasErrors = validationReport.hasErrors(); // Check if request has errors (e.g., its body does not conform to Swagger schema)

        // If test case [is faulty OR its input is incorrect] AND [returned status code below 400 (5XX is handled by a previous filter)]
        if ((faultyTestCase || inputHasErrors) && response.getStatusCode() < 400) {
            throw new RuntimeException("This faulty test case was expecting a 4XX status code, but received other. Conformance error found.");
        // If test case [is valid] AND [returned status code 400]
        } else if (!faultyTestCase && !inputHasErrors && dependenciesFulfilled && response.getStatusCode() == 400) {
            throw new RuntimeException("This test case's input was correct, but received a 400 (Bad Request) status code. Conformance error found.");
        }

        return response;
    }

    public Boolean getFaultyTestCase() {
        return faultyTestCase;
    }

    public void setFaultyTestCase(Boolean faultyTestCase) {
        this.faultyTestCase = faultyTestCase;
    }

    public Boolean getDependenciesFulfilled() {
        return dependenciesFulfilled;
    }

    public void setDependenciesFulfilled(Boolean dependenciesFulfilled) {
        this.dependenciesFulfilled = dependenciesFulfilled;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-2; // Third lowest priority of all filters, so it runs third-to-last before sending the request and third after sending it
    }
}
