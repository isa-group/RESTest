package es.us.isa.restest.util;

import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import static es.us.isa.restest.util.SpecificationVisitor.hasDependencies;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpecificationVisitorTest {

    @Test
    public void hasDependenciesTest() {
        OpenAPISpecification oas = new OpenAPISpecification("src/test/resources/Travel/swagger.yaml");
        assertTrue("The operation GET /trips/user should have dependencies", hasDependencies(oas.getSpecification().getPath("/trips/user").getGet()));
        assertFalse("The operation GET /trips shouldn't have dependencies", hasDependencies(oas.getSpecification().getPath("/trips").getGet()));
        assertFalse("The operation DELETE /trips/user shouldn't have dependencies", hasDependencies(oas.getSpecification().getPath("/trips/user").getDelete()));
    }
}
