package es.us.isa.restest.coverage;

import static es.us.isa.restest.coverage.CriterionType.*;
import static es.us.isa.restest.util.IDGenerator.generateId;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import static es.us.isa.restest.util.FileManager.*;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

public class CoverageMeterTest {

    private CoverageMeter covMeter;
    private List<TestCase> testSuite;
    private TestCase testCase1;
    private TestCase testCase2;
    private TestCase testCase3;
    private List<TestResult> testResults;
    private TestResult testResult1;
    private TestResult testResult2;
    private TestResult testResult3;

    @Before
    public void setUp() {
        String oasPath = "src/test/resources/specifications/petstore.json";
        OpenAPISpecification oas = new OpenAPISpecification(oasPath);
        CoverageGatherer covGath = new CoverageGatherer(oas);
        covMeter = new CoverageMeter(covGath);

        testSuite = new ArrayList<>();
        testCase1 = new TestCase(generateId(), false,"addPet", "/pet", HttpMethod.POST);
        testCase2 = new TestCase(generateId(), false, "findPetsByStatus", "/pet/findByStatus", HttpMethod.GET);
        testCase2.addQueryParameter("status", "available");
        testCase3 = new TestCase(generateId(), false, "uploadFile", "/pet/{petId}/uploadImage", HttpMethod.POST);
        testCase3.addQueryParameter("destinationFormat", "JPG");
        testCase3.addQueryParameter("convertToJPG", "false");

        testSuite.add(testCase1);
        testSuite.add(testCase2);
        testSuite.add(testCase3);

        covMeter.setTestSuite(testSuite);

        testResults = new ArrayList<>();
        testResult1 = new TestResult(testCase1.getId(), "201", "[]", "application/json");
        testResult2 = new TestResult(testCase2.getId(), "200", "[{\"name\": \"b\", \"id\": \"d\"}, {\"a\": \"b\", \"c\": \"d\"}]", "application/json");
        testResult3 = new TestResult(testCase3.getId(), "200", "{\"a\": \"b\", \"c\": \"d\"}", "application/json");

        testResults.add(testResult1);
        testResults.add(testResult2);
        testResults.add(testResult3);

        covMeter.setTestResults(testResults, testSuite);
    }

    @Test
    public void coverageMeterTest() {
        System.out.println("covMeter.getTotalCoverage(): " + covMeter.getTotalCoverage());
        System.out.println("covMeter.getInputCoverage(): " + covMeter.getInputCoverage());
        System.out.println("covMeter.getOutputCoverage(): " + covMeter.getOutputCoverage());
        System.out.println("covMeter.getCriterionTypeCoverage(PATH): " + covMeter.getCriterionTypeCoverage(PATH));
        System.out.println("covMeter.getCriterionTypeCoverage(OPERATION): " + covMeter.getCriterionTypeCoverage(OPERATION));
        System.out.println("covMeter.getCriterionTypeCoverage(PARAMETER): " + covMeter.getCriterionTypeCoverage(PARAMETER));
        System.out.println("covMeter.getCriterionTypeCoverage(PARAMETER_VALUE): " + covMeter.getCriterionTypeCoverage(PARAMETER_VALUE));
        System.out.println("covMeter.getCriterionTypeCoverage(INPUT_CONTENT_TYPE): " + covMeter.getCriterionTypeCoverage(INPUT_CONTENT_TYPE));
        System.out.println("covMeter.getCriterionTypeCoverage(STATUS_CODE_CLASS): " + covMeter.getCriterionTypeCoverage(STATUS_CODE_CLASS));
        System.out.println("covMeter.getCriterionTypeCoverage(STATUS_CODE): " + covMeter.getCriterionTypeCoverage(STATUS_CODE));
        System.out.println("covMeter.getCriterionTypeCoverage(OUTPUT_CONTENT_TYPE): " + covMeter.getCriterionTypeCoverage(OUTPUT_CONTENT_TYPE));
        System.out.println("covMeter.getCriterionTypeCoverage(RESPONSE_BODY_PROPERTIES): " + covMeter.getCriterionTypeCoverage(RESPONSE_BODY_PROPERTIES));
        System.out.println("covMeter.getCriterionCoverage(OPERATION, /pet): " + covMeter.getCriterionCoverage(OPERATION, "/pet"));
        System.out.println("covMeter.getCriterionCoverage(PARAMETER, /pet/findByStatus->GET): " + covMeter.getCriterionCoverage(PARAMETER, "/pet/findByStatus->GET"));
        System.out.println("covMeter.getCriterionCoverage(PARAMETER, /pet/{petId}/uploadImage->POST): " + covMeter.getCriterionCoverage(PARAMETER, "/pet/{petId}/uploadImage->POST"));
        System.out.println("covMeter.getCriterionCoverage(PARAMETER_VALUE, /pet/{petId}/uploadImage->POST->destinationFormat): " + covMeter.getCriterionCoverage(PARAMETER_VALUE, "/pet/{petId}/uploadImage->POST->destinationFormat"));
        System.out.println("covMeter.getCriterionCoverage(STATUS_CODE, /pet/findByStatus->GET): " + covMeter.getCriterionCoverage(STATUS_CODE, "/pet/findByStatus->GET"));
        System.out.println("covMeter.getCriterionCoverage(RESPONSE_BODY_PROPERTIES, /pet->POST->201): " + covMeter.getCriterionCoverage(RESPONSE_BODY_PROPERTIES, "/pet->POST->201"));
        System.out.println("covMeter.getCriterionCoverage(RESPONSE_BODY_PROPERTIES, /pet/findByStatus->GET->200): " + covMeter.getCriterionCoverage(RESPONSE_BODY_PROPERTIES, "/pet/findByStatus->GET->200"));
        System.out.println("covMeter.getCriterionCoverage(RESPONSE_BODY_PROPERTIES, /pet/{petId}/uploadImage->POST->200): " + covMeter.getCriterionCoverage(RESPONSE_BODY_PROPERTIES, "/pet/{petId}/uploadImage->POST->200"));
    }

    @Test
    public void exportCoverageTest() {
        // Delete files
        deleteFile("src/test/resources/csvData/coverage-results.csv");
        deleteFile("src/test/resources/csvData/test-cases.csv");
        deleteFile("src/test/resources/csvData/test-cases-coverage.csv");
        deleteFile("src/test/resources/csvData/test-results.csv");
        deleteFile("src/test/resources/csvData/test-results-coverage.csv");

        // Test export to CSV
        covMeter.exportCoverageToCSV("src/test/resources/csvData/coverage-results.csv", null, true);
        assertTrue(checkIfExists("src/test/resources/csvData/coverage-results.csv"));

        // Export test case 1 to CSV
        testCase1.exportToCSV("src/test/resources/csvData/test-cases.csv");
        assertTrue(checkIfExists("src/test/resources/csvData/test-cases.csv"));

        // Export coverage of test case 2 to CSV
        CoverageMeter.exportCoverageOfTestCaseToCSV("src/test/resources/csvData/test-cases-coverage.csv", testCase2);
        assertTrue(checkIfExists("src/test/resources/csvData/test-cases-coverage.csv"));

        // Export test result 2 to CSV
        testResult2.exportToCSV("src/test/resources/csvData/test-results.csv");
        assertTrue(checkIfExists("src/test/resources/csvData/test-results.csv"));

        // Export coverage of test result 3 to CSV
        CoverageMeter.exportCoverageOfTestResultToCSV("src/test/resources/csvData/test-results-coverage.csv", testResult3);
        assertTrue(checkIfExists("src/test/resources/csvData/test-results-coverage.csv"));
    }

    @Test
    public void coverageResultsTest() {
        deleteFile("src/test/resources/csvData/test-results-coverage.csv");

        CoverageResults coverageResults = new CoverageResults(covMeter.getTotalCoverage(), covMeter.getInputCoverage(), covMeter.getOutputCoverage());
        coverageResults.setCoverageOfCoverageCriteriaFromCoverageMeter(covMeter);
        coverageResults.setCoverageOfCriterionTypeFromCoverageMeter(covMeter);

        try {
            coverageResults.exportCoverageReportToJSON("src/test/resources/csvData/test-coverage.json");
            assertTrue(checkIfExists("src/test/resources/csvData/test-coverage.json"));
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void coverageResultsCSVTest() {
        deleteFile("src/test/resources/csvData/test-results-coverage.csv");

        CoverageResults coverageResults = new CoverageResults(covMeter.getTotalCoverage(), covMeter.getInputCoverage(), covMeter.getOutputCoverage());
        coverageResults.setCoverageOfCoverageCriteriaFromCoverageMeter(covMeter);
        coverageResults.setCoverageOfCriterionTypeFromCoverageMeter(covMeter);

        coverageResults.exportCoverageReportToCSV("src/test/resources/csvData/test-coverage.csv");
        assertTrue(checkIfExists("src/test/resources/csvData/test-coverage.csv"));
    }

    @Test
    public void addTestSuiteTest() {
        float oldCoverage = covMeter.getTotalCoverage();
        List<TestCase> newTestSuite = new ArrayList<>();
        TestCase tc4 = new TestCase(generateId(), false, "getOrderById", "/store/order/{orderId}", HttpMethod.GET);
        newTestSuite.add(tc4);

        covMeter.addTestSuite(newTestSuite);

        assertTrue("The new coverage should be higher than the old one", covMeter.getTotalCoverage() > oldCoverage);
    }

    @Test
    public void resetTestSuiteTest() {
        float oldCoverage = covMeter.getTotalCoverage();
        List<TestCase> newTestSuite = new ArrayList<>();
        TestCase tc4 = new TestCase(generateId(), false, "getOrderById", "/store/order/{orderId}", HttpMethod.GET);
        newTestSuite.add(tc4);

        covMeter.resetCoverage();
        covMeter.setTestSuite(newTestSuite);

        assertTrue("The new coverage should be lower than the old one", covMeter.getTotalCoverage() < oldCoverage);
    }
}