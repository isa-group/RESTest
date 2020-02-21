package es.us.isa.restest.validation;

import es.us.isa.restest.testcases.TestCaseCounterFilter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

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
    private TestCaseCounterFilter counterFilter; // Used to know whether this test case is faulty or not
    private Boolean dependenciesFulfilled; // Whether this test case fulfills all inter-parameter dependencies or not

    public PossiblyFaultyTestCaseFilter(TestCaseCounterFilter counterFilter, Boolean dependenciesFulfilled) {
        this.counterFilter = counterFilter;
        this.dependenciesFulfilled = dependenciesFulfilled;
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        // If test case [is faulty] AND [returned status code below 400 (5XX is handled by a previous filter)]
        if (counterFilter.getFaultyTestCase() && response.getStatusCode() < 400) {
            throw new RuntimeException("This faulty test case was expecting a 4XX status code, but received other. Conformance error found.");
        // If test case [is valid] AND [returned status code 400]
        } else if (!counterFilter.getFaultyTestCase() && dependenciesFulfilled && response.getStatusCode() == 400) {
            throw new RuntimeException("This test case's input was correct, but received a 400 (Bad Request) status code. Conformance error found.");
        }

        return response;
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
