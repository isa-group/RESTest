package es.us.isa.restest.testcases;

import io.swagger.v3.oas.models.PathItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestCaseTest {

    @Test
    public void getFlatRepresentationQueryHeaderBodyTest() {
        TestCase tc1 = new TestCase("abc", true, "createBook", "/books", PathItem.HttpMethod.POST);
        tc1.setFaultyReason("inter_parameter_dependency");
        tc1.setBodyParameter("example body");
        tc1.addQueryParameter("q2", "val2");
        tc1.addHeaderParameter("h1", "valF1");
        tc1.addQueryParameter("q1", "val1");
        tc1.addHeaderParameter("h2", "valF2");

        TestCase tc2 = new TestCase("def", false, "createBook", "/books", PathItem.HttpMethod.POST);
        tc2.setFaultyReason("none");
        tc2.setBodyParameter("example body");
        tc2.addQueryParameter("q1", "val1");
        tc2.addHeaderParameter("h2", "valF2");
        tc2.addQueryParameter("q2", "val2");
        tc2.addHeaderParameter("h1", "valF1");

        assertEquals("The flat representation of the test case is wrong", "POST/booksapplication/jsonq1val1q2val2h1valF1h2valF2example body", tc1.getFlatRepresentation());
        assertEquals("The flat representation of the test case is wrong", "POST/booksapplication/jsonq1val1q2val2h1valF1h2valF2example body", tc2.getFlatRepresentation());
        assertEquals("The two test cases should have the same flat representation", tc1.getFlatRepresentation(), tc2.getFlatRepresentation());
    }

    @Test
    public void getFlatRepresentationPathFormTest() {
        TestCase tc1 = new TestCase("abc", true, "updatePage", "/books/{bookId}/{page}", PathItem.HttpMethod.PUT);
        tc1.setFaultyReason("inter_parameter_dependency");
        tc1.addFormParameter("q2", "val2");
        tc1.addPathParameter("bookId", "valF1");
        tc1.addFormParameter("q1", "val1");
        tc1.addPathParameter("page", "valF2");

        TestCase tc2 = new TestCase("def", false, "updatePage", "/books/{bookId}/{page}", PathItem.HttpMethod.PUT);
        tc2.setFaultyReason("none");
        tc2.addFormParameter("q1", "val1");
        tc2.addPathParameter("page", "valF2");
        tc2.addFormParameter("q2", "val2");
        tc2.addPathParameter("bookId", "valF1");

        assertEquals("The flat representation of the test case is wrong", "PUT/books/valF1/valF2application/x-www-form-urlencodedq1val1q2val2", tc1.getFlatRepresentation());
        assertEquals("The flat representation of the test case is wrong", "PUT/books/valF1/valF2application/x-www-form-urlencodedq1val1q2val2", tc2.getFlatRepresentation());
        assertEquals("The two test cases should have the same flat representation", tc1.getFlatRepresentation(), tc2.getFlatRepresentation());
    }
}
