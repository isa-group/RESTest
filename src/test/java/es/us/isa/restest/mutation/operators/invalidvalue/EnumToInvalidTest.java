package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.mutation.TestCaseMutation;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class EnumToInvalidTest {

    @Test
    public void mutationAppliedTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        TestCase tc = new TestCase("dfgsdfg", true, "getComments", "/comments", PathItem.HttpMethod.GET);
        tc.addQueryParameter("type", "Review");
        tc.addQueryParameter("limit", "2");

        TestCase oldTc = SerializationUtils.clone(tc);
        ParameterFeatures paramToMutate = new ParameterFeatures(spec.getSpecification().getPaths().get("/comments").getGet().getParameters().stream().filter(p -> p.getName().equals("type")).findFirst().get());

        assertTrue("The test case should be mutated", EnumToInvalid.mutate(tc, paramToMutate).matches("Changed value of string \\(enum\\) parameter type from 'Review' to .* '.*'"));
        assertFalse("The value should not be one of the enum options", Arrays.asList("Review", "Request", "Complain", "All").contains(tc.getParameterValue(paramToMutate)));
        assertNotEquals("The two test cases should be different", tc, oldTc);
    }
}
