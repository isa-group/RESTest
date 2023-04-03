package es.us.isa.restest.specification;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.OpenAPIParameter;
import io.swagger.v3.oas.models.Operation;
import org.hamcrest.MatcherAssert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static es.us.isa.restest.specification.OpenAPISpecificationVisitor.*;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.*;

public class SpecificationVisitorTest {

    static OpenAPISpecification travelApiOas;
    static OpenAPISpecification stripeApiOas;

    @BeforeClass
    public static void getSpec() {
        travelApiOas = new OpenAPISpecification("src/test/resources/Travel/swagger.yaml");
        stripeApiOas = new OpenAPISpecification("src/test/resources/Stripe/swagger.yaml");
    }

    @Test
    public void shouldFindParameter() {
        Operation op = travelApiOas.getSpecification().getPaths().get("/trips").getGet();
        String paramName = "offset";
        String paramType = "query";
        OpenAPIParameter param = findParameterFeatures(op, paramName, paramType);

        assertEquals("The parameter's name should be 'offset', but its name is" + param.getName(), "offset", param.getName());
        assertEquals("'offset' should be a query parameter, but it is a " + param.getIn() + " parameter", "query", param.getIn());
        assertEquals("'offset' should be an integer. Parameter type: " + param.getType(), "integer", param.getType());
        assertFalse("'offset' shouldn't be required", param.getRequired());
    }

    @Test
    public void shouldGetRequiredParametersGetOperation() {
        Operation op = travelApiOas.getSpecification().getPaths().get("/trips/user").getGet();
        List<OpenAPIParameter> required = getRequiredParametersFeatures(op);

        MatcherAssert.assertThat(required.stream().map(OpenAPIParameter::getName).collect(Collectors.toList()), contains("username", "password"));
    }

    @Test
    public void shouldGetRequiredParametersPostOperation() {
        Operation op = travelApiOas.getSpecification().getPaths().get("/users").getPost();
        List<OpenAPIParameter> required = getRequiredParametersFeatures(op);

        MatcherAssert.assertThat(required.stream().map(OpenAPIParameter::getName).collect(Collectors.toList()), contains("body"));
    }

    @Test
    public void shouldGetParametersSubjectToInvalidValueChangeTravelAPI() {
        Operation op = travelApiOas.getSpecification().getPaths().get("/trips/user").getGet();
        List<OpenAPIParameter> parameters = getParametersFeaturesSubjectToInvalidValueChange(op);

        MatcherAssert.assertThat(parameters.stream().map(OpenAPIParameter::getName).collect(Collectors.toList()), contains("isAdmin", "maxPriceAirbnb", "includeTripsWithUnsetAirbnbPrice", "sort"));
    }

    @Test
    public void shouldGetParametersSubjectToInvalidValueChangeYelpAPI() {

    }

    @Test
    public void hasDependenciesTest() {
        assertTrue("The operation GET /trips/user should have dependencies", hasDependencies(travelApiOas.getSpecification().getPaths().get("/trips/user").getGet()));
        assertFalse("The operation GET /trips shouldn't have dependencies", hasDependencies(travelApiOas.getSpecification().getPaths().get("/trips").getGet()));
        assertFalse("The operation DELETE /trips/user shouldn't have dependencies", hasDependencies(travelApiOas.getSpecification().getPaths().get("/trips/user").getDelete()));
    }
}
