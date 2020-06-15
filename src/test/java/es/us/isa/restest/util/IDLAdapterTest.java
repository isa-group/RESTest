package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

import static es.us.isa.restest.util.IDLAdapter.restest2idlTestCase;

public class IDLAdapterTest {

    @Test
    public void restest2idlTestCaseTest() {
        TestCase tc = new TestCase("sdfsdf", false, "dfgfsdf", "/users/{id}", HttpMethod.GET);
        tc.addPathParameter("id", "c1");
        tc.addQueryParameter("isAdmin", "true");
        tc.addQueryParameter("fields", "name,surname,employees");

        Map<String, String> request = restest2idlTestCase(tc);

        assertEquals("The request should contain the parameter 'id' with value 'c1'", "c1", request.get("id"));
        assertEquals("The request should contain the parameter 'isAdmin' with value 'true'", "true", request.get("isAdmin"));
        assertEquals("The request should contain the parameter 'fields' with value 'name,surname,employees'", "name,surname,employees", request.get("fields"));
    }
}
