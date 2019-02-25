package es.us.isa.rester.coverage;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import static es.us.isa.rester.coverage.CriterionType.*;
import es.us.isa.rester.testcases.TestCase;

/**
 * Class for the measurement of test coverage
 * 
 * @author Alberto Martin-Lopez
 */
public class CoverageMeter {

    CoverageGatherer coverageGatherer;  // coverage gatherer already containing all criteria to be covered
    Collection<TestCase> testSuite;     // full set of abstract test cases addressing the API
    // Collection<TestResult> testResults; // test outputs generated after running the test suite against the API


    public CoverageMeter(CoverageGatherer coverageGatherer) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = null;
    }

    public CoverageMeter(CoverageGatherer coverageGatherer, Collection<TestCase> testSuite) {
        this.coverageGatherer = coverageGatherer;
        this.testSuite = testSuite;
        setCoveredInputElements(); // after setting testSuite, update covered input elements from all criteria
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

    public int getAllTotalElements() {
        return getAllElements(null);
    }

    public int getAllInputElements() {
        return getAllElements("input");
    }

    public int getAllOutputElements() {
        return getAllElements("output");
    }

    public int getCoveredTotalElements() {
        return getCoveredElements(null);
    }

    public int getCoveredInputElements() {
        return getCoveredElements("input");
    }

    public int getCoveredOutputElements() {
        return getCoveredElements("output");
    }

    /**
     * Get all elements to cover from all coverage criteria in the API
     * 
     * @param criterionType Type of criteria to consider: "input", "output" or null for all
     * @return Number of elements collected among all coverage criteria
     */
    private int getAllElements(String criterionType) {
        return coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> CriterionType.getTypes(criterionType).contains(c.getType()))
                .mapToInt(c -> c.getAllElements().size())
                .sum();
    }

    /**
     * Get covered elements from all coverage criteria in the API
     * 
     * @param criterionType Type of criteria to consider: "input", "output" or null for all
     * @return Number of covered elements collected among all coverage criteria
     */
    private int getCoveredElements(String criterionType) {
        return coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> CriterionType.getTypes(criterionType).contains(c.getType()))
                .mapToInt(c -> c.getCoveredElements().size())
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
        int allElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type)
                .mapToInt(c -> c.getAllElements().size())
                .sum();

        if (allElements == 0) {
            return 100;
        }

        int coveredElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type)
                .mapToInt(c -> c.getCoveredElements().size())
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
        int allElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type && c.getRootPath().equals(rootPath))
                .findFirst()
                .orElse(new CoverageCriterion(type)) // if no matching criterion is found, return an empty made-up one (100% coverage)
                .getAllElements().size();

        if (allElements == 0) {
            return 100;
        }

        int coveredElements = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type && c.getRootPath().equals(rootPath))
                .findFirst()
                .get()
                .getCoveredElements().size();
        
        return 100 * (float) coveredElements / (float) allElements;
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
            updateCriterion(AUTHENTICATION, testCase.getPath() + "->" + testCase.getMethod().toString(), testCase.getAuthentication());
            updateCriterion(INPUT_CONTENT_TYPE, testCase.getPath() + "->" + testCase.getMethod().toString(), testCase.getInputFormat());

        }
    }

    private void updateCriterion(CriterionType type, String rootPath, String element) {
        // Find unique criterion by type and rootPath
        CoverageCriterion criterion = coverageGatherer.getCoverageCriteria().stream()
                .filter(c -> c.getType() == type && c.getRootPath().equals(rootPath))
                .findFirst()
                .orElse(null);

        if (criterion != null && !criterion.getCoveredElements().contains(element)) {
            criterion.addCoveredElement(element); // add element to the already covered elements of the criterion
        }
    }
}
