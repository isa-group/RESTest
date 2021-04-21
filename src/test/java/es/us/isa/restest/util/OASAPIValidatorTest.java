package es.us.isa.restest.util;

import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import static es.us.isa.restest.util.OASAPIValidator.getValidator;

public class OASAPIValidatorTest {

    @Test
    public void testGitHubOAS() {
        getValidator(new OpenAPISpecification("src/test/resources/GitHub/swagger_forTestSuite.yaml"));

        System.out.println("As long as this is printed, this test cases passes (no exceptions thrown).");
    }
}
