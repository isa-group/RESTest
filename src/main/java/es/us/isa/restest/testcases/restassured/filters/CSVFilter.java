package es.us.isa.restest.testcases.restassured.filters;

//import io.restassured.filter.Filter;
import es.us.isa.restest.testcases.TestResult;
import es.us.isa.restest.util.PropertyManager;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import static es.us.isa.restest.coverage.CoverageMeter.exportCoverageOfTestResultToCSV;

public class CSVFilter extends OracleFilter implements OrderedFilter {

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
