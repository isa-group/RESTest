package es.us.isa.restest.validation;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST-Assured filter to assert that faulty test cases return a 4XX status code
 * and that nominal test cases fulfilling all inter-parameter dependencies do not
 * return a 400 status code. A test case may be faulty in two situations:
 * <ol>
 *     <li>If the test case was made faulty on purpose (e.g., removing a required
 *     parameter).</li>
 *     <li>If the request body does not conform to the Swagger schema. This happens
 *     when the {@link es.us.isa.restest.inputs.perturbation.ObjectPerturbator ObjectPerturbator}
 *     mutates a valid input request body into an invalid one.</li>
 * </ol>
 */
public class NominalOrFaultyTestCaseFilter implements OrderedFilter {
    private Boolean testCaseIsFaulty; // Whether this test case is faulty or not
    private Boolean dependenciesFulfilled; // Whether this test case fulfills all inter-parameter dependencies or not
    private String faultyReason; // Why the test case is faulty

    public NominalOrFaultyTestCaseFilter(Boolean testCaseIsFaulty, Boolean dependenciesFulfilled, String faultyReason) {
        this.testCaseIsFaulty = testCaseIsFaulty;
        this.dependenciesFulfilled = dependenciesFulfilled;
        this.faultyReason = faultyReason;
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        // If test case [is faulty] AND [returned status code below 400 (5XX is handled by a previous filter)]
        if (testCaseIsFaulty && response.getStatusCode() < 400) {
            throw new RuntimeException("This faulty test case was expecting a 4XX status code(" + faultyReason + "), but received other. Conformance error found.");
        // If test case [is valid] AND [returned status code 400]
        } else if (!testCaseIsFaulty && dependenciesFulfilled && response.getStatusCode() == 400) {
            throw new RuntimeException("This test case's input was correct, but received a 400 (Bad Request) status code. Conformance error found.");
        }

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-2; // Third lowest priority of all filters, so it runs third-to-last before sending the request and third after sending it
    }
}
