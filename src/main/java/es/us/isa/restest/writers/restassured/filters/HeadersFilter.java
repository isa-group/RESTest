package es.us.isa.restest.writers.restassured.filters;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HeadersFilter extends RESTestFilter implements Filter {

  private Headers headers = null;

  @Override
  public Response filter(FilterableRequestSpecification requestSpec,
      FilterableResponseSpecification responseSpec, FilterContext ctx) {
    if (headers != null) {
      requestSpec.headers(headers);
    }
    return ctx.next(requestSpec, responseSpec);
  }

  public Headers getHeaders() {
    return headers;
  }

  public void setHeaders(String... headers) {
    this.setHeaders(new Headers(Arrays.stream(headers)
        .map(header -> header.split(":", 2))
        .map(pair -> new Header(pair[0].trim(), pair[1].trim()))
        .collect(Collectors.toList())));
  }

  public void setHeaders(Headers headers) {
    this.headers = headers;
  }
}
