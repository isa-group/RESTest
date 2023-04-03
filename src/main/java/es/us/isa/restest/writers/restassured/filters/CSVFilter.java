package es.us.isa.restest.writers.restassured.filters;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * This class is an RestAssured filter that exports the test results to a CSV file.
 */
public class CSVFilter extends RESTestFilter implements OrderedFilter {

    public CSVFilter() {
        super();
    }

    public CSVFilter(String APIName, String testId) {
        super(APIName, testId);
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        // Export output data after receiving API response
        exportTestResultToCSV(response, true, "none");

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE-4; // Fifth lowest priority of all filters, so it runs fifth-to-last before sending the request and fifth after sending it
    }
}
