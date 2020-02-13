package es.us.isa.restest.mutation;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.HttpMethod;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Test;
import static org.junit.Assert.assertNotEquals;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;
import static org.junit.Assert.assertTrue;

public class TestCaseMutationTest {

    @Test
    public void testCaseMutationTest() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Comments/swagger.yaml");
        TestCase tc = new TestCase("dfgsdfg", true, "getComments", "/comments", HttpMethod.GET);
        tc.addQueryParameter("type", "Review");
        tc.addQueryParameter("limit", "2");

        TestCase oldTc = SerializationUtils.clone(tc);
        makeTestCaseFaulty(tc, spec.getSpecification().getPath("/comments").getGet());
        assertNotEquals(tc, oldTc);
    }
}
