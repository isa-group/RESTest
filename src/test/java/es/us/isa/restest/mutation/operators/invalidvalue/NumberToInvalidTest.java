package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class NumberToInvalidTest {

    @Test
    public void mutationAppliedTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Memes/swagger.yaml");
        TestCase tc = new TestCase("dfgsdfg", true, "<>", "/gallery", PathItem.HttpMethod.PUT);

        TestCase oldTc = SerializationUtils.clone(tc);
        ParameterFeatures paramToMutate = new ParameterFeatures(spec.getSpecification().getPaths().get("/gallery").getPut().getParameters().stream().filter(p -> p.getName().equals("rarity")).findFirst().get());

        assertTrue("The test case should be mutated", NumberToInvalid.mutate(tc, paramToMutate).matches("Changed value of number parameter rarity from '.*' to .* '.*'"));
        assertFalse("The value should not be a number", StringUtils.isNumeric(tc.getParameterValue(paramToMutate)));
        assertNotEquals("The two test cases should be different", tc, oldTc);
    }
}
