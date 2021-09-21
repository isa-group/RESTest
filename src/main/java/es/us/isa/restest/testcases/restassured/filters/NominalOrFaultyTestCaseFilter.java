package es.us.isa.restest.testcases.restassured.filters;

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
public class NominalOrFaultyTestCaseFilter extends RESTestFilter implements OrderedFilter {

    public NominalOrFaultyTestCaseFilter() {
        super();
    }

    public NominalOrFaultyTestCaseFilter(Boolean testCaseIsFaulty, Boolean dependenciesFulfilled, String faultyReason) {
        super(testCaseIsFaulty, dependenciesFulfilled, faultyReason);
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        filterValidation(response);

        return response;
    }

    // If nominal/faulty validation error is found, throw exception
    public void filterValidation(Response response) {
        if(testCaseIsFaulty != null) {
            // If test case [is faulty] AND [returned status code below 400 (5XX is handled by a previous filter)]
            if (testCaseIsFaulty && response.getStatusCode() < 400)
                saveTestResultAndThrowException(response, "This faulty test case was expecting a 4XX status code(" + faultyReason + "), but received a 2XX one.");
            // If test case [is valid] AND [returned status code 400]
            else if (!testCaseIsFaulty && dependenciesFulfilled && response.getStatusCode() == 400)
                saveTestResultAndThrowException(response, "This test case's input was (possibly) correct, but received a 400 (Bad Request) status code.");
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-2; // Third lowest priority of all filters, so it runs third-to-last before sending the request and third after sending it
    }
}
