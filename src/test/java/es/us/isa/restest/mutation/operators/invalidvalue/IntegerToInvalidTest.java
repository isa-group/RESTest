package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class IntegerToInvalidTest {

    @Test
    public void mutationAppliedTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Travel/swagger.yaml");
        TestCase tc = new TestCase("dfgsdfg", true, "getTripsFromUser", "/trips/user", PathItem.HttpMethod.GET);

        TestCase oldTc = SerializationUtils.clone(tc);
        ParameterFeatures paramToMutate = new ParameterFeatures(spec.getSpecification().getPaths().get("/trips/user").getGet().getParameters().stream().filter(p -> p.getName().equals("maxPriceAirbnb")).findFirst().get());

        assertTrue("The test case should be mutated", IntegerToInvalid.mutate(tc, paramToMutate).matches("Changed value of integer parameter maxPriceAirbnb from '.*' to .* '.*'"));
        try {
            Integer.parseInt(tc.getParameterValue(paramToMutate));
            fail("The value should not be an integer");
        } catch (NumberFormatException ignored) {}
        assertNotEquals("The two test cases should be different", tc, oldTc);
    }

    @Test
    public void integerMinMaxMutationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/AmadeusHotel/swagger.yaml");
        TestCase tc = new TestCase("dfgsdfg", true, "getMultiHotelOffers", "/shopping/hotel-offers", PathItem.HttpMethod.GET);

        ParameterFeatures paramToMutate = new ParameterFeatures(spec.getSpecification().getPaths().get("/shopping/hotel-offers").getGet().getParameters().stream().filter(p -> p.getName().equals("page[limit]")).findFirst().get());

        TestCase tcToMutate = null;
        for (int i=0; i<10; i++) {
            tcToMutate = SerializationUtils.clone(tc);
            IntegerToInvalid.mutate(tcToMutate, paramToMutate);
            assertNotEquals("The two test cases should be different", tc, tcToMutate);
        }
    }
}
