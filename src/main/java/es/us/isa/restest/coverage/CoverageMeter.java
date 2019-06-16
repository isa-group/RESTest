package es.us.isa.restest.coverage;

import static es.us.isa.restest.coverage.CriterionType.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import es.us.isa.restest.testcases.TestCase;
import static es.us.isa.restest.coverage.CriterionType.*;
import es.us.isa.restest.testcases.TestResult;
import static es.us.isa.restest.util.CSVManager.*;
import static es.us.isa.restest.util.FileManager.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Class for the measurement of test coverage
 * 
 * @author Alberto Martin-Lopez
 */
public class CoverageMeter {

    CoverageGatherer coverageGatherer;  // coverage gatherer already containing all criteria to be covered
    Collection<TestCase> testSuite;     // full set of abstract test cases addressing the API
    Collection<TestResult> testResults; // test outputs generated after running the test suite against the API

    public CoverageMeter(CoverageGatherer coverageGatherer) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = null;
        this.testResults = null;
    }

    public CoverageMeter(CoverageGatherer coverageGatherer, Collection<TestCase> testSuite) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = testSuite;
        setCoveredInputElements(); // after setting testSuite, update covered input elements from all criteria
    }

    public CoverageMeter(CoverageGatherer coverageGatherer, Collection<TestCase> testSuite, Collection<TestResult> testResults) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = testSuite;
        setCoveredInputElements(); // after setting testSuite, update covered input elements from all criteria
        this.testResults = testResults;
        setCoveredOutputElements(); // after setting testResults, update covered output elements from all criteria
    }

    public CoverageGatherer getCoverageGatherer() {
        return this.coverageGatherer;
    }

    public void setCoverageGatherer(CoverageGatherer coverageGatherer) {
        this.coverageGatherer = coverageGatherer;
    }

    public Collection<TestCase> getTestSuite() {
        return this.testSuite;
    }

    public void setTestSuite(Collection<TestCase> testSuite) {
        this.testSuite = testSuite;
        setCoveredInputElements(); // after setting testSuite, update covered input elements from all criteria
    }

    public Collection<TestResult> getTestResults() {
        return this.testResults;
    }

    public void setTestResults(Collection<TestResult> testResults) {
        this.testResults = testResults;
        setCoveredOutputElements(); // after setting testResults, update covered output elements from all criteria
    }

    public long getAllTotalElements() {
        return getAllElements(null);
    }

    public long getAllInputElements() {
        return getAllElements("input");
    }

    public long getAllOutputElements() {
        return getAllElements("output");
    }

    public long getCoveredTotalElements() {
        return getCoveredElements(null);
    }

    public long getCoveredInputElements() {
        return getCoveredElements("input");
    }

    public long getCoveredOutputElements() {
        return getCoveredElements("output");
    }

    /**
     * Get all elements to cover from all coverage criteria in the API
     *
     * @param criterionType Type of criteria to consider: "input", "output" or null for all
     * @return Number of elements collected among all coverage criteria
     */
    private long getAllElements(String criterionType) {
        return coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> CriterionType.getTypes(criterionType).contains(c.getType()))
                .mapToLong(c -> c.getElementsCount())
                .sum();
    }

    /**
     * Get covered elements from all coverage criteria in the API
     * 
     * @param criterionType Type of criteria to consider: "input", "output" or null for all
     * @return Number of covered elements collected among all coverage criteria
     */
    private long getCoveredElements(String criterionType) {
        return coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> CriterionType.getTypes(criterionType).contains(c.getType()))
                .mapToLong(c -> c.getCoveredElementsCount())
                .sum();
    }

    /**
     * Get total coverage (input and output) considering all criteria
     * 
     * @return Coverage percentage
     */
    public float getTotalCoverage() {
        if (getAllTotalElements() == 0) {
            return 100;
        }
        return 100 * (float) getCoveredTotalElements() / (float) getAllTotalElements();
    }

    /**
     * Get input coverage considering all input criteria
     * 
     * @return Coverage percentage
     */
    public float getInputCoverage() {
        if (getAllInputElements() == 0) {
            return 100;
        }
        return 100 * (float) getCoveredInputElements() / (float) getAllInputElements();
    }

    /**
     * Get output coverage considering all output criteria
     * 
     * @return Coverage percentage
     */
    public float getOutputCoverage() {
        if (getAllOutputElements() == 0) {
            return 100;
        }
        return 100 * (float) getCoveredOutputElements() / (float) getAllOutputElements();
    }

    /**
     * Get coverage of all criteria of a given type
     * 
     * @param type Type of criterion to check coverage (e.g. PATH, STATUS_CODE, etc.)
     * @return Coverage percentage
     */
    public float getCriterionTypeCoverage(CriterionType type) {
        long allElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type)
                .mapToLong(c -> c.getElementsCount())
                .sum();

        if (allElements == 0) {
            return 100;
        }

        long coveredElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type)
                .mapToLong(c -> c.getCoveredElementsCount())
                .sum();
        
        return 100 * (float) coveredElements / (float) allElements;
    }

    /**
     * Get coverage of a single criterion, identified by its type and rootPath
     * 
     * @param type Type of criterion (e.g. PATH, STATUS_CODE, etc.)
     * @param rootPath path that uniquely identifies the criterion (e.g. "/pet->getPetById->id")
     * @return Coverage percentage
     */
    public float getCriterionCoverage(CriterionType type, String rootPath) {
        CoverageCriterion criterion = coverageGatherer.getCoverageCriteria().stream() // find criterion
                .filter(c -> c.getType() == type && c.getRootPath().equals(rootPath))
                .findFirst()
                .orElse(null);

        if (criterion != null) {
            return criterion.getCoverage();
        }

        return 100; // if the criterion doesn't exist, return 100% coverage by default
    }

    /**
     * Set 'coveredElements' field of every input CoverageCriterion
     */
    private void setCoveredInputElements() {
        // Traverse all test cases and, for each one, modify the coverage criteria it affects, by adding new covered elements
        for (TestCase testCase: testSuite) {
            updateCriterion(PATH, "", testCase.getPath());
            updateCriterion(OPERATION, testCase.getPath(), testCase.getMethod().toString());
            for (Entry<String, String> parameter: testCase.getHeaderParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey());
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue());
            }
            for (Entry<String, String> parameter: testCase.getPathParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey());
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue());
            }
            for (Entry<String, String> parameter: testCase.getQueryParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey());
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue());
            }
            updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), "body");
            updateCriterion(AUTHENTICATION, testCase.getPath() + "->" + testCase.getMethod().toString(), testCase.getAuthentication());
            updateCriterion(INPUT_CONTENT_TYPE, testCase.getPath() + "->" + testCase.getMethod().toString(), testCase.getInputFormat());

        }
    }

    private void setCoveredOutputElements() {
        // Traverse all test results and, for each one, modify the coverage criteria it affects, by adding new covered elements
        for (TestResult testResult: testResults) {
            String statusCodeClass = testResult.getStatusCode().charAt(0) == '4' ? "4XX" : testResult.getStatusCode().charAt(0) == '2' ? "2XX" : null;
            if (statusCodeClass != null)
                updateCriterion(STATUS_CODE_CLASS, findTestCase(testResult.getId()).getPath() + "->" + findTestCase(testResult.getId()).getMethod().toString(), statusCodeClass);
            updateCriterion(STATUS_CODE, findTestCase(testResult.getId()).getPath() + "->" + findTestCase(testResult.getId()).getMethod().toString(), testResult.getStatusCode());
            updateCriterion(OUTPUT_CONTENT_TYPE, findTestCase(testResult.getId()).getPath() + "->" + findTestCase(testResult.getId()).getMethod().toString(), testResult.getOutputFormat());

            // Response body properties criteria
            Iterator<Entry<String,JsonNode>> responseIterator = getBodyProperties(testResult.getResponseBody());
            while (responseIterator != null && responseIterator.hasNext()) {
                String responseProperty = responseIterator.next().getKey();
                updateCriterion(RESPONSE_BODY_PROPERTIES, findTestCase(testResult.getId()).getPath() + "->" + findTestCase(testResult.getId()).getMethod().toString() + "->" + testResult.getStatusCode(), responseProperty);
            }
        }
    }

    /**
     * Find a specific coverage criterion and cover the element passed in.
     * @param type Type of coverage criterion to look for (PATH, STATUS_CODE, etc.)
     * @param rootPath Path to the criterion, e.g. "/pets->GET->type". Together with {@code type},
     *                 uniquely identifies a coverage criterion
     * @param element Element to cover among all the elements present in the coverage criterion, e.g.
     *                {@code "sold"} for a parameter value
     */
    private void updateCriterion(CriterionType type, String rootPath, String element) {
        // Find unique criterion by type and rootPath
        CoverageCriterion criterion = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type && c.getRootPath().equals(rootPath))
                .findFirst()
                .orElse(null);

        if (criterion != null) { // if the criterion exists
            criterion.coverElement(element); // add element to the already covered elements of the criterion
        }
    }

    /**
     * Given a test case ID (or test result ID), return the test case
     * @param id ID of the test case
     * @return Test case matching the ID passed in
     */
    private TestCase findTestCase(String id) {
        return testSuite.stream()
                .filter(tc -> tc.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Export coverage data to external file (CSV). You can export input, output or both
     * types of coverage data.
     *
     * @param path Path where to save the output file
     * @param criterionType Type of criteria to consider: "input", "output" or null for all
     * @param overwrite Whether to overwrite existing file or not
     */
    public void exportCoverageToCSV(String path, String criterionType, boolean overwrite) {
        if (overwrite)
            createFileWithHeader(path, "criterionType,rootPath,element,isCovered");
        coverageGatherer.getCoverageCriteria().stream()
            .filter(criterion -> CriterionType.getTypes(criterionType).contains(criterion.getType()))
            .forEach(criterion -> criterion.getElements()
                .forEach((element, isCovered) -> {
                    writeRow(path, criterion.getType().toString() + "," + criterion.getRootPath() + "," + element + "," + isCovered);
                })
            );
    }

    public static void exportCoverageOfTestCaseToCSV(String path, TestCase tc) {
        if (!checkIfExists(path)) // If the file doesn't exist, create it (only once)
            createFileWithHeader(path, "testCaseId,criterionType,rootPath,element");

        String row;

        // Path criterion
        row = tc.getId() + ",PATH,," + tc.getPath();
        writeRow(path, row);

        // Operation criterion
        row = tc.getId() + ",OPERATION," + tc.getPath() + "," + tc.getMethod().toString();
        writeRow(path, row);

        // Parameters and parameter values criteria
        for (Map.Entry<String, String> h: tc.getHeaderParameters().entrySet()) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + "," + h.getKey();
            writeRow(path, row);
            row = tc.getId() + ",PARAMETER_VALUE," + tc.getPath() + "->" + tc.getMethod().toString() + "->" + h.getKey() + "," + h.getValue();
            writeRow(path, row);
        }
        for (Map.Entry<String, String> p: tc.getPathParameters().entrySet()) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + "," + p.getKey();
            writeRow(path, row);
            row = tc.getId() + ",PARAMETER_VALUE," + tc.getPath() + "->" + tc.getMethod().toString() + "->" + p.getKey() + "," + p.getValue();
            writeRow(path, row);
        }
        for (Map.Entry<String, String> q: tc.getQueryParameters().entrySet()) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + "," + q.getKey();
            writeRow(path, row);
            row = tc.getId() + ",PARAMETER_VALUE," + tc.getPath() + "->" + tc.getMethod().toString() + "->" + q.getKey() + "," + q.getValue();
            writeRow(path, row);
        }
        // For the body parameter, we do not consider parameter values, only the parameter itself
        if (tc.getBodyParameter() != null) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + ",body";
            writeRow(path, row);
        }

        // Input content-type criterion
        row = tc.getId() + ",INPUT_CONTENT_TYPE," + tc.getPath() + "->" + tc.getMethod().toString() + "," + tc.getInputFormat();
        writeRow(path, row);
    }

    public static void exportCoverageOfTestResultToCSV(String path, TestResult tr) {
        if (!checkIfExists(path)) // If the file doesn't exist, create it (only once)
            createFileWithHeader(path, "testResultId,criterionType,element");

        String row;

        // Status code class criterion
        String statusCodeClass = tr.getStatusCode().charAt(0) == '4' ? "4XX" : tr.getStatusCode().charAt(0) == '2' ? "2XX" : null;
        row = tr.getId() + ",STATUS_CODE_CLASS," + statusCodeClass;
        writeRow(path, row);

        // Status code criterion
        row = tr.getId() + ",STATUS_CODE," + tr.getStatusCode();
        writeRow(path, row);

        // Output content-type criterion
        row = tr.getId() + ",OUTPUT_CONTENT_TYPE," + tr.getOutputFormat();
        writeRow(path, row);

        // Response body properties criteria
        Iterator<Entry<String,JsonNode>> responseIterator = getBodyProperties(tr.getResponseBody());
        while (responseIterator != null && responseIterator.hasNext()) {
            String responseProperty = responseIterator.next().getKey();
            row = tr.getId() + ",RESPONSE_BODY_PROPERTIES," + responseProperty;
            writeRow(path, row);
        }
    }

    private static Iterator<Entry<String,JsonNode>> getBodyProperties(String body) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonResponse = objectMapper.readTree(body);
            Iterator<Entry<String,JsonNode>> responseIterator = null;
            if (jsonResponse instanceof ObjectNode) {
                responseIterator = jsonResponse.fields();
            } else if (jsonResponse instanceof ArrayNode && jsonResponse.get(0) != null) {
                responseIterator = jsonResponse.get(0).fields();
            }
            return responseIterator;
        } catch (IOException e) {
            System.out.println("Unable to get body properties, body is not formatted in JSON");
            return null;
        }
    }
}
