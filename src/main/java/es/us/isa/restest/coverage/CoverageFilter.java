package es.us.isa.restest.coverage;

//import io.restassured.filter.Filter;
import es.us.isa.restest.testcases.TestResult;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import static es.us.isa.restest.coverage.CoverageMeter.exportCoverageOfTestResultToCSV;

public class CoverageFilter implements OrderedFilter {
    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        // Export output coverage data after receiving API response
        TestResult tr = new TestResult("GETversionincidentsidformatTest_t4a277aq7msz", Integer.toString(response.statusCode()), response.body().print(), response.contentType());
        tr.exportToCSV("target/coverage-results/test-results.csv");
        exportCoverageOfTestResultToCSV("target/coverage-results/test-results-coverage.csv", tr);

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // Lowest priority of all filters, so it runs last before sending the request and first after sending it
    }
}
