package es.us.isa.restest.coverage;

//import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

public class CoverageFilter implements OrderedFilter {
    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        System.out.println(response.body().prettyPrint());

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE; // Higher priority than other filters
    }
}
