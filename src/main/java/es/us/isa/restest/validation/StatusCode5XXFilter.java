package es.us.isa.restest.validation;

import es.us.isa.restest.validation.exceptions.StatusCode5XXValidationException;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;


public class StatusCode5XXFilter implements OrderedFilter {

    public StatusCode5XXFilter() {
        super();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        // If 5XX status code is found, throw exception
        if (response.getStatusCode() >= 500) {
            throw new StatusCode5XXValidationException("Received status 5XX. Server error found.");
        }

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-1; // Second lowest priority of all filters, so it runs second-to-last before sending the request and second after sending it
    }
}
