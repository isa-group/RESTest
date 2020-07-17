package es.us.isa.restest.testcases.executors;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import io.swagger.v3.oas.models.PathItem;
import org.junit.Test;

import static org.junit.Assert.*;

public class RestAssuredExecutorTest {

    @Test
    public void executeOneValidTestWithoutOraclesTest() {
        OpenAPISpecification openAPISpecification = new OpenAPISpecification("src/test/resources/Travel/swagger_betty.yaml");
        RestAssuredExecutor executor = new RestAssuredExecutor(openAPISpecification);

        TestCase validTestCase = new TestCase("asdf", false, "getTrips", "/trips", PathItem.HttpMethod.GET);
        validTestCase.setFulfillsDependencies(true);
        validTestCase.setEnableOracles(false);
        validTestCase.addQueryParameter("limit", "4");
        validTestCase.addQueryParameter("offset", "2");

        TestResult validTestResult = executor.executeTest(validTestCase);

        assertEquals("The response status code should be 200", "200", validTestResult.getStatusCode());
        assertEquals("The response content type should be JSON", "application/json", validTestResult.getOutputFormat());
        assertNull("The last test case status should be null (no oracles)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 1", 1, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 0", 0, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 0", 0, executor.getFailedTests());
    }

    @Test
    public void executeOneValidTestWithOraclesTest() {
        OpenAPISpecification openAPISpecification = new OpenAPISpecification("src/test/resources/Travel/swagger_betty.yaml");
        RestAssuredExecutor executor = new RestAssuredExecutor(openAPISpecification);

        TestCase validTestCase = new TestCase("asdf", false, "getTrips", "/trips", PathItem.HttpMethod.GET);
        validTestCase.setFulfillsDependencies(true);
        validTestCase.addQueryParameter("limit", "4");
        validTestCase.addQueryParameter("offset", "2");

        TestResult validTestResult = executor.executeTest(validTestCase);

        assertEquals("The response status code should be 200", "200", validTestResult.getStatusCode());
        assertEquals("The response content type should be JSON", "application/json", validTestResult.getOutputFormat());
        assertTrue("The last test case status should be passed (true)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 1", 1, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 1", 1, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 0", 0, executor.getFailedTests());
    }

    @Test
    public void executeOneFaultyTestWithoutOraclesTest() {
        OpenAPISpecification openAPISpecification = new OpenAPISpecification("src/test/resources/Travel/swagger_betty.yaml");
        RestAssuredExecutor executor = new RestAssuredExecutor(openAPISpecification);

        TestCase faultyTestCase = new TestCase("asdf", true, "getTrips", "/trips", PathItem.HttpMethod.GET);
        faultyTestCase.setFulfillsDependencies(true);
        faultyTestCase.setEnableOracles(false);
        faultyTestCase.addQueryParameter("limit", "hola");

        TestResult faultyTestResult = executor.executeTest(faultyTestCase);

        assertEquals("The response status code should be 400", "400", faultyTestResult.getStatusCode());
        assertTrue("The response content type should be html-like", faultyTestResult.getOutputFormat().contains("text/html"));
        assertNull("The last test case status should be null (no oracles)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 1", 1, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 0", 0, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 0", 0, executor.getFailedTests());
    }

    @Test
    public void executeOneFaultyTestWithOraclesTest() {
        OpenAPISpecification openAPISpecification = new OpenAPISpecification("src/test/resources/Travel/swagger_betty.yaml");
        RestAssuredExecutor executor = new RestAssuredExecutor(openAPISpecification);

        TestCase faultyTestCase = new TestCase("asdf", true, "getTrips", "/trips", PathItem.HttpMethod.GET);
        faultyTestCase.setFulfillsDependencies(true);
        faultyTestCase.addQueryParameter("limit", "hola");

        TestResult faultyTestResult = executor.executeTest(faultyTestCase);

        assertEquals("The response status code should be 400", "400", faultyTestResult.getStatusCode());
        assertTrue("The response content type should be html-like", faultyTestResult.getOutputFormat().contains("text/html"));
        assertTrue("The last test case status should be passed (true)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 1", 1, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 1", 1, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 0", 0, executor.getFailedTests());
    }

    @Test
    public void executeOneFailingTestWithOraclesTest() {
        OpenAPISpecification openAPISpecification = new OpenAPISpecification("src/test/resources/Travel/swagger_betty.yaml");
        RestAssuredExecutor executor = new RestAssuredExecutor(openAPISpecification);

        TestCase failingTestCase = new TestCase("asdf", false, "getTripsFromUser", "/trips/user", PathItem.HttpMethod.GET);
        failingTestCase.setFulfillsDependencies(true);
        failingTestCase.addQueryParameter("maxPriceAirbnb", "10");
        failingTestCase.addQueryParameter("username", "Jimmy");
        failingTestCase.addQueryParameter("password", "aeiou3");

        TestResult failingTestResult = executor.executeTest(failingTestCase);

        assertEquals("The response status code should be 400", "400", failingTestResult.getStatusCode());
        assertTrue("The response content type should be html-like", failingTestResult.getOutputFormat().contains("text/html"));
        assertFalse("The last test case status should be failed (false)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 1", 1, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 0", 0, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 1", 1, executor.getFailedTests());
    }

    @Test
    public void executeMultipleTestsWithOraclesTest() {
        OpenAPISpecification openAPISpecification = new OpenAPISpecification("src/test/resources/Travel/swagger_betty.yaml");
        RestAssuredExecutor executor = new RestAssuredExecutor(openAPISpecification);

        TestCase validTestCase = new TestCase("asdf", false, "getTripsFromUser", "/trips/user", PathItem.HttpMethod.GET);
        validTestCase.setFulfillsDependencies(true);
        validTestCase.addQueryParameter("maxPriceAirbnb", "10");
        validTestCase.addQueryParameter("includeTripsWithUnsetAirbnbPrice", "true");
        validTestCase.addQueryParameter("username", "Jimmy");
        validTestCase.addQueryParameter("password", "aeiou3");

        TestResult validTestResult = executor.executeTest(validTestCase);

        assertEquals("The response status code should be 200", "200", validTestResult.getStatusCode());
        assertEquals("The response content type should be JSON", "application/json", validTestResult.getOutputFormat());
        assertTrue("The last test case status should be passed (true)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 1", 1, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 1", 1, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 0", 0, executor.getFailedTests());

        TestCase faultyTestCase = new TestCase("asdf2", true, "getTrips", "/trips", PathItem.HttpMethod.GET);
        faultyTestCase.setFulfillsDependencies(true);
        faultyTestCase.addQueryParameter("limit", "hola");

        TestResult faultyTestResult = executor.executeTest(faultyTestCase);

        assertEquals("The response status code should be 400", "400", faultyTestResult.getStatusCode());
        assertTrue("The response content type should be html-like", faultyTestResult.getOutputFormat().contains("text/html"));
        assertTrue("The last test case status should be passed (true)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 2", 2, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 2", 2, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 0", 0, executor.getFailedTests());

        TestCase failingTestCase = new TestCase("asdf3", false, "getTripsFromUser", "/trips/user", PathItem.HttpMethod.GET);
        failingTestCase.setFulfillsDependencies(true);
        failingTestCase.addQueryParameter("maxPriceAirbnb", "10");
        failingTestCase.addQueryParameter("username", "Jimmy");
        failingTestCase.addQueryParameter("password", "aeiou3");

        TestResult failingTestResult = executor.executeTest(failingTestCase);

        assertEquals("The response status code should be 400", "400", failingTestResult.getStatusCode());
        assertTrue("The response content type should be html-like", failingTestResult.getOutputFormat().contains("text/html"));
        assertFalse("The last test case status should be failed (false)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 3", 3, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 2", 2, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 1", 1, executor.getFailedTests());

        TestCase validTestCaseWithoutOracles = new TestCase("asdf4", false, "getTrips", "/trips", PathItem.HttpMethod.GET);
        validTestCaseWithoutOracles.setFulfillsDependencies(true);
        validTestCaseWithoutOracles.setEnableOracles(false);
        validTestCaseWithoutOracles.addQueryParameter("limit", "4");
        validTestCaseWithoutOracles.addQueryParameter("offset", "2");

        TestResult validTestResultWithoutOracles = executor.executeTest(validTestCaseWithoutOracles);

        assertEquals("The response status code should be 200", "200", validTestResultWithoutOracles.getStatusCode());
        assertEquals("The response content type should be JSON", "application/json", validTestResultWithoutOracles.getOutputFormat());
        assertNull("The last test case status should be null (no oracles)", executor.getLastTestPassed());
        assertEquals("The number of executed test cases should be 4", 4, executor.getTotalTests());
        assertEquals("The number of passed test cases should be 2", 2, executor.getPassedTests());
        assertEquals("The number of failed test cases should be 1", 1, executor.getFailedTests());
    }
}
