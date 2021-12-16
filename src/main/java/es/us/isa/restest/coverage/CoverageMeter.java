package es.us.isa.restest.coverage;

import static es.us.isa.restest.coverage.CriterionType.*;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.TestResult;
import static es.us.isa.restest.util.CSVManager.*;
import static es.us.isa.restest.util.FileManager.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Class for the measurement of test coverage
 *
 * @author Alberto Martin-Lopez
 */
public class CoverageMeter {

    private static final Logger log = LogManager.getLogger(CoverageMeter.class);
    private CoverageGatherer coverageGatherer;  // coverage gatherer already containing all criteria to be covered
    private Collection<TestCase> testSuite;     // full set of abstract test cases addressing the API
    private Collection<TestResult> testResults; // test outputs generated after running the test suite against the API

    public CoverageMeter(CoverageGatherer coverageGatherer) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = new ArrayList<>();
        this.testResults = new ArrayList<>();
    }

    public CoverageMeter(CoverageGatherer coverageGatherer, Collection<TestCase> testSuite) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = testSuite;
        setCoveredInputElements(testSuite); // after setting testSuite, update covered input elements from all criteria
    }

    public CoverageMeter(CoverageGatherer coverageGatherer, Collection<TestCase> testSuite, Collection<TestResult> testResults) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = testSuite;
        setCoveredInputElements(testSuite); // after setting testSuite, update covered input elements from all criteria
        this.testResults = testResults;
        setCoveredOutputElements(testResults, testSuite); // after setting testResults, update covered output elements from all criteria
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

    public void addTestSuite(Collection<TestCase> testSuite) {
        this.testSuite.addAll(testSuite);
        setCoveredInputElements(testSuite);
    }

    public void setTestSuite(Collection<TestCase> testSuite) {
        this.testSuite = testSuite;
        setCoveredInputElements(testSuite); // after setting testSuite, update covered input elements from all criteria
    }

    public void resetCoverage() {
        this.coverageGatherer = new CoverageGatherer(this.coverageGatherer.getSpec());
    }

    public Collection<TestResult> getTestResults() {
        return this.testResults;
    }

    public void addTestResults(Collection<TestResult> testResults, Collection<TestCase> testSuite) {
        this.testResults.addAll(testResults);
        setCoveredOutputElements(testResults, testSuite);
    }

    public void setTestResults(Collection<TestResult> testResults, Collection<TestCase> testSuite) {
        this.testResults = testResults;
        setCoveredOutputElements(testResults, testSuite); // after setting testResults, update covered output elements from all criteria
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
                .mapToLong(CoverageCriterion::getElementsCount)
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
                .mapToLong(CoverageCriterion::getCoveredElementsCount)
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
                .mapToLong(CoverageCriterion::getElementsCount)
                .sum();

        if (allElements == 0) {
            return 100;
        }

        long coveredElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type)
                .mapToLong(CoverageCriterion::getCoveredElementsCount)
                .sum();

        return 100 * (float) coveredElements / (float) allElements;
    }

    /**
     * Get coverage of a single criterion, identified by its type and rootPath
     *
     * @param type Type of criterion (e.g. PATH, STATUS_CODE, etc.)
     * @param rootPath path that uniquely identifies the criterion (e.g. "/pet-&gt;getPetById-&gt;id")
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
     * Based on {@code this} CoverageMeter object, returns a modified CoverageMeter
     * whose input coverage counts only those elements whose response was successful.
     *
     * @return A modified CoverageMeter object
     */
    public CoverageMeter getAPosteriorCoverageMeter() {
        return getAPosteriorCoverageMeter(Integer.MAX_VALUE);
    }

    public CoverageMeter getAPosteriorCoverageMeter(int maxTestSuiteSize) {
        CoverageMeter aPosterioriCoverageMeter = new CoverageMeter(new CoverageGatherer(coverageGatherer.getSpec()));

        if(testResults != null) {
            List<TestCase> orderedTestSuite = new ArrayList<>(testSuite).stream()
                    .sorted(Comparator.comparing(TestCase::getId))
                    .collect(Collectors.toList());
            List<TestResult> orderedTestResults = new ArrayList<>(testResults).stream()
                    .sorted(Comparator.comparing(TestResult::getId))
                    .collect(Collectors.toList());

            aPosterioriCoverageMeter.testSuite = testSuite;
            aPosterioriCoverageMeter.testResults = testResults;
            int totalSize = testSuite.size();

            for (int i=0; i*maxTestSuiteSize < totalSize; i++) {
                int upperLimit = Math.min((i + 1) * maxTestSuiteSize, totalSize);
                Collection<TestCase> testSuiteFragment = orderedTestSuite.subList(i*maxTestSuiteSize, upperLimit);
                Collection<TestResult> testResultsFragment = orderedTestResults.subList(i*maxTestSuiteSize, upperLimit);

                List<String> invalidResponseResultsIds = new ArrayList<>(testResultsFragment).stream()
                        .filter(testResult -> Integer.parseInt(testResult.getStatusCode()) >= 400)
                        .map(TestResult::getId)
                        .collect(Collectors.toList());

                aPosterioriCoverageMeter.setCoveredOutputElements(testResultsFragment, testSuiteFragment);
                testSuiteFragment = testSuiteFragment.stream()
                        .filter(testCase -> !invalidResponseResultsIds.contains(testCase.getId()))
                        .collect(Collectors.toList());
                aPosterioriCoverageMeter.setCoveredInputElements(testSuiteFragment);

                if (maxTestSuiteSize != Integer.MAX_VALUE)
                    log.info("Creating a posteriori coverage meter. Progress: {}/{}", upperLimit, totalSize);
            }
        }

        return aPosterioriCoverageMeter;
    }

    /**
     * Set 'coveredElements' field of every input CoverageCriterion
     * @param testSuite
     */
    private void setCoveredInputElements(Collection<TestCase> testSuite) {
        // Traverse all test cases and, for each one, modify the coverage criteria it affects, by adding new covered elements
        for (TestCase testCase: testSuite) {
            updateCriterion(PATH, "", testCase.getPath(), coverageGatherer);
            updateCriterion(OPERATION, testCase.getPath(), testCase.getMethod().toString(), coverageGatherer);
            for (Entry<String, String> parameter: testCase.getHeaderParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey(), coverageGatherer);
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue(), coverageGatherer);
            }
            for (Entry<String, String> parameter: testCase.getPathParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey(), coverageGatherer);
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue(), coverageGatherer);
            }
            for (Entry<String, String> parameter: testCase.getQueryParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey(), coverageGatherer);
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue(), coverageGatherer);
            }
            for(Entry<String, String> parameter : testCase.getFormParameters().entrySet()) {
                updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), parameter.getKey(), coverageGatherer);
                updateCriterion(PARAMETER_VALUE, testCase.getPath() + "->" + testCase.getMethod().toString() + "->" + parameter.getKey(), parameter.getValue(), coverageGatherer);
            }
            updateCriterion(PARAMETER, testCase.getPath() + "->" + testCase.getMethod().toString(), "body", coverageGatherer);
//            updateCriterion(AUTHENTICATION, testCase.getPath() + "->" + testCase.getMethod().toString(), testCase.getAuthentication());
            updateCriterion(INPUT_CONTENT_TYPE, testCase.getPath() + "->" + testCase.getMethod().toString(), testCase.getInputFormat(), coverageGatherer);

        }
    }

    private void setCoveredOutputElements(Collection<TestResult> testResults, Collection<TestCase> testSuite) {
        // Traverse all test results and, for each one, modify the coverage criteria it affects, by adding new covered elements
        for (TestResult testResult: testResults) {

            String statusCodeClass = null;
            if(testResult.getStatusCode().charAt(0) == '4') {
                statusCodeClass = "4XX";
            } else if(testResult.getStatusCode().charAt(0) == '2') {
                statusCodeClass = "2XX";
            }

            if (statusCodeClass != null)
                updateCriterion(STATUS_CODE_CLASS, findTestCase(testResult.getId(), testSuite).getPath() + "->" + findTestCase(testResult.getId(), testSuite).getMethod().toString(), statusCodeClass, coverageGatherer);
            updateCriterion(STATUS_CODE, findTestCase(testResult.getId(), testSuite).getPath() + "->" + findTestCase(testResult.getId(), testSuite).getMethod().toString(), testResult.getStatusCode(), coverageGatherer);
            updateCriterion(OUTPUT_CONTENT_TYPE, findTestCase(testResult.getId(), testSuite).getPath() + "->" + findTestCase(testResult.getId(), testSuite).getMethod().toString(), outputContentTypeTranslator(testResult.getOutputFormat()), coverageGatherer);

            // Response body properties criteria
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonResponse = objectMapper.readTree(testResult.getResponseBody());
                String baseRootPath = findTestCase(testResult.getId(), testSuite).getPath() +
                        "->" + findTestCase(testResult.getId(), testSuite).getMethod().toString() +
                        "->" + testResult.getStatusCode() + "->"; // note the final arrow, since new elements will be added to the rootPath
                iterateOverJsonNode(jsonResponse, baseRootPath, coverageGatherer, null, null, null);
            } catch (IOException e) {
//                System.out.println("Error setting response body properties coverage criteria: response body is text/html; expected application/json.");
//                e.printStackTrace();
            }

        }
    }

    /**
     * This method checks if the outputFormat is application/json or application/xml; in any of those cases,
     * returns the output format that CoverageMeter is able to manage.
     */
    private String outputContentTypeTranslator(String outputFormat) {
        String translation;
        if(outputFormat.contains("application/json")) {
            translation = "application/json";
        } else if(outputFormat.contains("application/xml")) {
            translation = "application/xml";
        } else translation = outputFormat;
        return translation;
    }

    /**
     * This method can be used in two ways: a) to mark several RESPONSE_BODY_PROPERTIES as 'covered'
     * in the CoverageGatherer of the class, or b) to export that same data to CSV. Given a JSON node, if
     * it is a JSON object or an array of JSON objects, apply one of the two processes mentioned for
     * every RESPONSE_BODY_PROPERTIES criterion affected. This function is to be called recursively,
     * so as to cover all sub-properties of the root element.
     *
     * @param jsonNode JsonNode object that may contain some properties which will be marked as 'covered'
     *                 on the {@link CoverageGatherer} object, or exported to CSV. A JsonNode can be a
     *                 JSON object, an array or any other data type (such as an integer or a string)
     * @param baseRootPath Case a): Initial rootPath: "{path}->{httpMethod}->{statusCode}->". Example of
     *                     baseRootPath after 2 iterations: "{path}->{httpMethod}->{statusCode}->{prop1[{prop2".
     *                     Case b): Initial rootPath: "". Example of baseRootPath after 2 iterations:
     *                     "{prop1[{prop2"
     * @param covGath CoverageGatherer where to update the criteria. {@code null} for case a)
     * @param filePath Path to the CSV file where to export the data. {@code null} for case b)
     * @param testResultId ID of the test result that is being exported (must be added in the row of the CSV
     *                     file). {@code null} for case b)
     * @param coveredRootPaths List of rootPaths that have already been covered (added to the CSV file). This
     *                         is necessary because, for an array, the same element could be written multiple
     *                         times (e.g. thousands of duplicated lines for an array with thousands of
     *                         elements). {@code null} for case b)
     */
    private static void iterateOverJsonNode(JsonNode jsonNode, String baseRootPath, CoverageGatherer covGath, String filePath, String testResultId, List<String> coveredRootPaths) {
        Iterator<Entry<String,JsonNode>> objectPropertiesIterator = null;

        if (jsonNode instanceof ObjectNode) { // if the jsonNode is actually a JSON object
            baseRootPath += "{"; // update rootPath accordingly
            objectPropertiesIterator = jsonNode.fields(); // get all properties from the JSON object
            updateResponseBodyPropertiesCriteria(objectPropertiesIterator, baseRootPath, covGath, filePath, testResultId, coveredRootPaths);
        } else if (jsonNode instanceof ArrayNode && jsonNode.get(0) instanceof ObjectNode) { // if the jsonNode is an array of JSON objects
            baseRootPath += "[{"; // update rootPath
            for (int i = 0; i<jsonNode.size(); i++) { // for every element of the array
                JsonNode arrayItem = jsonNode.get(i);
                objectPropertiesIterator = arrayItem.fields(); // get all properties from the JSON object
                updateResponseBodyPropertiesCriteria(objectPropertiesIterator, baseRootPath, covGath, filePath, testResultId, coveredRootPaths);

            }
        }
    }

    /**
     * Given some object properties, iterate over all of them and update the affected RESPONSE_BODY_PROPERTIES
     * criteria accordingly. For each property, iterate over all of their sub-properties again (recursively)
     * to continue covering all sub-properties.
     *
     * @param objectPropertiesIterator Iterator of entries where the key is the name of the property and the
     *                                 value is the JsonNode property
     * @param rootPath Root path to locate the coverage criterion among all criteria of the
     *                 {@link CoverageGatherer} object. To be updated with the name of the property when
     *                 calling {@link #iterateOverJsonNode(JsonNode, String, CoverageGatherer, String, String, List) iterateOverJsonNode}
     * @param covGath CoverageGatherer where to update the criteria
     * @param filePath Path to the CSV file where to export the data
     * @param testResultId ID of the test result that is being exported (must added in the row of the CSV file).
     * @param coveredRootPaths List of rootPaths that have already been covered (added to the CSV file). This
     *                         is necessary because, for an array, the same element could be written multiple
     *                         times (e.g. thousands of duplicated lines for an array with thousands of
     *                         elements).
     */
    private static void updateResponseBodyPropertiesCriteria(Iterator<Entry<String,JsonNode>> objectPropertiesIterator, String rootPath, CoverageGatherer covGath, String filePath, String testResultId, List<String> coveredRootPaths) {
        while (objectPropertiesIterator != null && objectPropertiesIterator.hasNext()) { // iterate over all properties of the object
            Entry<String,JsonNode> responseProperty = objectPropertiesIterator.next();
            if (filePath == null) // if we want to update the CoverageGatherer object
                updateCriterion(RESPONSE_BODY_PROPERTIES, rootPath, responseProperty.getKey(), covGath); // set the property as 'covered'
            else { // if we want to export the results to CSV
                String updatedRootPath = rootPath + responseProperty.getKey();
                if (!coveredRootPaths.contains(updatedRootPath)) {
                    writeCSVRow(filePath, testResultId + ",RESPONSE_BODY_PROPERTIES," + rootPath + responseProperty.getKey());
                    coveredRootPaths.add(updatedRootPath); // add the rootPath to the list of covered ones, in order not to duplicate lines in the CSV file
                }
            }
            iterateOverJsonNode(responseProperty.getValue(), rootPath+responseProperty.getKey(), covGath, filePath, testResultId, coveredRootPaths); // iterate over the sub-properties of that property
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
    private static void updateCriterion(CriterionType type, String rootPath, String element, CoverageGatherer covGath) {
        // Find unique criterion by type and rootPath
        CoverageCriterion criterion = covGath.getCoverageCriteria().stream()
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
     * @param testSuite collection of test cases where to look for the ID
     * @return Test case matching the ID passed in
     */
    private TestCase findTestCase(String id, Collection<TestCase> testSuite) {
        return testSuite.stream()
                .filter(tc -> Objects.equals(tc.getId(), id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no test case with id <" + id + ">"));
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
            createCSVwithHeader(path, "criterionType,rootPath,element,isCovered");
        coverageGatherer.getCoverageCriteria().stream()
                .filter(criterion -> CriterionType.getTypes(criterionType).contains(criterion.getType()))
                .forEach(criterion -> criterion.getElements()
                        .forEach((element, isCovered) ->
                                writeCSVRow(path, criterion.getType().toString() + "," + criterion.getRootPath() + "," + element + "," + isCovered)
                        )
                );
    }

    public static void exportCoverageOfTestCaseToCSV(String path, TestCase tc) {
        if (!checkIfExists(path)) // If the file doesn't exist, create it (only once)
            createCSVwithHeader(path, "testCaseId,criterionType,rootPath,element");

        String row;

        // Path criterion
        row = tc.getId() + ",PATH,," + tc.getPath();
        writeCSVRow(path, row);

        // Operation criterion
        row = tc.getId() + ",OPERATION," + tc.getPath() + "," + tc.getMethod().toString();
        writeCSVRow(path, row);

        // Parameters and parameter values criteria
        for (Map.Entry<String, String> h: tc.getHeaderParameters().entrySet()) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + "," + h.getKey();
            writeCSVRow(path, row);
            row = tc.getId() + ",PARAMETER_VALUE," + tc.getPath() + "->" + tc.getMethod().toString() + "->" + h.getKey() + "," + h.getValue();
            writeCSVRow(path, row);
        }
        for (Map.Entry<String, String> p: tc.getPathParameters().entrySet()) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + "," + p.getKey();
            writeCSVRow(path, row);
            row = tc.getId() + ",PARAMETER_VALUE," + tc.getPath() + "->" + tc.getMethod().toString() + "->" + p.getKey() + "," + p.getValue();
            writeCSVRow(path, row);
        }
        for (Map.Entry<String, String> q: tc.getQueryParameters().entrySet()) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + "," + q.getKey();
            writeCSVRow(path, row);
            row = tc.getId() + ",PARAMETER_VALUE," + tc.getPath() + "->" + tc.getMethod().toString() + "->" + q.getKey() + "," + q.getValue();
            writeCSVRow(path, row);
        }
        // For the body parameter, we do not consider parameter values, only the parameter itself
        if (tc.getBodyParameter() != null) {
            row = tc.getId() + ",PARAMETER," + tc.getPath() + "->" + tc.getMethod().toString() + ",body";
            writeCSVRow(path, row);
        }

        // Input content-type criterion
        row = tc.getId() + ",INPUT_CONTENT_TYPE," + tc.getPath() + "->" + tc.getMethod().toString() + "," + tc.getInputFormat();
        writeCSVRow(path, row);
    }

    public static void exportCoverageOfTestResultToCSV(String path, TestResult tr) {
        if (!checkIfExists(path)) // If the file doesn't exist, create it (only once)
            createCSVwithHeader(path, "testResultId,criterionType,element");

        String row;

        // Status code class criterion
        String statusCodeClass = null;
        if(tr.getStatusCode().charAt(0) == '4') {
            statusCodeClass = "4XX";
        } else if(tr.getStatusCode().charAt(0) == '2') {
            statusCodeClass = "2XX";
        }

        row = tr.getId() + ",STATUS_CODE_CLASS," + statusCodeClass;
        writeCSVRow(path, row);

        // Status code criterion
        row = tr.getId() + ",STATUS_CODE," + tr.getStatusCode();
        writeCSVRow(path, row);

        // Output content-type criterion
        row = tr.getId() + ",OUTPUT_CONTENT_TYPE," + tr.getOutputFormat();
        writeCSVRow(path, row);

        // Response body properties criteria
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonResponse = objectMapper.readTree(tr.getResponseBody());
            iterateOverJsonNode(jsonResponse, "", null, path, tr.getId(), new ArrayList<>());
        } catch (IOException e) {
            log.error("Unable to get body properties, body is not formatted in JSON", e);
            log.error(e.getMessage());
        }
    }

}

