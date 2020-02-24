package es.us.isa.restest.testcases;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.RestAssuredRequest;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import static com.atlassian.oai.validator.util.StringUtils.requireNonEmpty;

/**
 * REST-Assured filter to count number of faulty and nominal test cases. First,
 * it validates the request to know whether it is faulty or not. Then, it updates
 * the counters and forwards the request to the next filter.
 */
public class TestCaseCounterFilter implements OrderedFilter {
    private final SwaggerRequestResponseValidator validator;
    private Integer nFaulty;
    private Integer nNominal;
    private Boolean faultyTestCaseForSure;
    private Boolean faultyTestCase;

    public TestCaseCounterFilter(String specUrlOrDefinition, Integer nFaulty, Integer nNominal, Boolean faultyTestCaseForSure) {
        requireNonEmpty(specUrlOrDefinition, "A Swagger URL is required");
        this.nFaulty = nFaulty;
        this.nNominal = nNominal;
        this.faultyTestCaseForSure = faultyTestCaseForSure;
        this.validator = SwaggerRequestResponseValidator.createFor(specUrlOrDefinition).build();
    }

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec, FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);
        ValidationReport validationReport = this.validator.validateOnlyRequest(RestAssuredRequest.of(requestSpec), RestAssuredResponse.of(response));
        Boolean inputHasErrors = validationReport.hasErrors(); // Check if request has errors (e.g., its body does not conform to Swagger schema)

        if (faultyTestCaseForSure || inputHasErrors) {
            faultyTestCase = true;
            nFaulty++;
        } else {
            faultyTestCase = false;
            nNominal++;
        }

        return response;
    }

    public Integer getnFaulty() {
        return nFaulty;
    }

    public void setnFaulty(Integer nFaulty) {
        this.nFaulty = nFaulty;
    }

    public Integer getnNominal() {
        return nNominal;
    }

    public void setnNominal(Integer nNominal) {
        this.nNominal = nNominal;
    }

    public Boolean getFaultyTestCase() {
        return faultyTestCase;
    }

    public void setFaultyTestCase(Boolean faultyTestCase) {
        this.faultyTestCase = faultyTestCase;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // Lowest priority of all filters, so it runs last before sending the request and first after sending it
    }
}
