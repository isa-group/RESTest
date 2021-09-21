package es.us.isa.restest.testcases.restassured.filters;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST-Assured filter to assert that the status code is less than 500
 *
 * @author Alberto Martin-Lopez
 */
public class StatusCode5XXFilter extends RESTestFilter implements OrderedFilter {

    public StatusCode5XXFilter() {
        super();
    }

    public StatusCode5XXFilter(Boolean testCaseIsFaulty, Boolean dependenciesFulfilled, String faultyReason) {
        super(testCaseIsFaulty, dependenciesFulfilled, faultyReason);
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        filterValidation(response);

        return response;
    }

    // If 5XX status code is found, throw exception
    public void filterValidation(Response response) {
        if (response.getStatusCode() >= 500) {
            if (testCaseIsFaulty != null && testCaseIsFaulty)
                saveTestResultAndThrowException(response, "Status code 5XX with invalid request: " + faultyReason);
            else if (dependenciesFulfilled != null && dependenciesFulfilled)
                saveTestResultAndThrowException(response, "Status code 5XX with valid request.");
            else // This occurs when using RT (nominal test case but dependencies may not be fulfilled)
                saveTestResultAndThrowException(response, "Status code 5XX.");
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-1; // Second lowest priority of all filters, so it runs second-to-last before sending the request and second after sending it
    }
}
