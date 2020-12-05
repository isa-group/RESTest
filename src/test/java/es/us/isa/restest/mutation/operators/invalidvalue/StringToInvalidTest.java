package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class StringToInvalidTest {

    @Test
    public void mutationAppliedTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger_forTestSuite.yaml");
        TestCase tc = new TestCase("dfgsdfg", true, "getComment", "/comments/{id}", PathItem.HttpMethod.GET);

        TestCase oldTc = SerializationUtils.clone(tc);
        ParameterFeatures paramToMutate = new ParameterFeatures(spec.getSpecification().getPaths().get("/comments/{id}").getGet().getParameters().stream().filter(p -> p.getName().equals("id")).findFirst().get());

        assertEquals("The test case should be mutated", "Violated 'max_length' constraint of string parameter id", StringToInvalid.mutate(tc, paramToMutate));
        assertTrue("The string value should be longer than 4 characters", tc.getParameterValue(paramToMutate).length() > 4);
        assertNotEquals("The two test cases should be different", tc, oldTc);
    }
}
