package es.us.isa.restest.validation;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * REST-Assured filter to assert that the status code is between 400 and 500. This
 * is expected when the test case is faulty.
 */
public class FaultyTestCaseFilter implements OrderedFilter {

    public FaultyTestCaseFilter() {
        super();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        // If status code is between 400 and 500, throw exception
        if (response.getStatusCode() >= 400 && response.getStatusCode() < 500) {
            throw new RuntimeException("This faulty test case was expecting a 4XX status code, but received other. Conformance error found.");
        }

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-2; // Third lowest priority of all filters, so it runs third-to-last before sending the request and third after sending it
    }
}
