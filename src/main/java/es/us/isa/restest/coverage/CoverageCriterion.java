package es.us.isa.restest.coverage;

import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.coverage.CriterionType;

/**
 * Class that represents a specific coverage criterion, e.g. paths,
 * parameters, etc. Keeps track of the coverage level
 * 
 * @author Alberto Martin-Lopez
 */
public class CoverageCriterion {

    private CriterionType type;             // Type of coverage criterion: paths, operations, content-type, etc.
    private List<Object> allElements;       // All elements to cover to reach 100% coverage
    private List<Object> coveredElements;   // Elements already covered

    /**
     * The following property's purpose is to locate the criterion inside the API resources hierarchy. There could be several parameter values
     * criteria having all the same parameter name. Therefore, it is necessary to uniquely identify each criterion to check if they are covered
     * when analysing the abstract test cases. This property makes sense for all criteria except for paths, since there is only one of this
     * kind. The longest allowed path contains 3 elements: "{path}->{operationId}->{parameterName}" OR "{path}->{operationId}->{statusCode}"
     */
    private String rootPath;

    public CoverageCriterion(CriterionType type) {
        this.type = type;
        this.allElements = new ArrayList<>();
        this.coveredElements = new ArrayList<>();
        this.rootPath = "";
    }

    public CriterionType getType() {
        return type;
    }

    public void setType(CriterionType type) {
        this.type = type;
    }

    public List<Object> getAllElements() {
        return allElements;
    }

    public void setAllElements(List<Object> allElements) {
        this.allElements = allElements;
    }

    public List<Object> getCoveredElements() {
        return coveredElements;
    }

    public void setCoveredElements(List<Object> coveredElements) {
        this.coveredElements = coveredElements;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Add a new element to the list of already covered elements.
     * @param newlyCoveredElements the element to add
     */
    public void addCoveredElement(Object newlyCoveredElement) {
        coveredElements.add(newlyCoveredElement);
    }

    /**
     * Get coverage of this criterion as a percentage by dividing the
     * number of covered elements by the number of total elements.
     * @return coverage percentage
     */
    public float getCoverage() {
        if (allElements.size() == 0) {
            return 100;
        }
        return 100 * (float)coveredElements.size() / (float)allElements.size();
    }
}