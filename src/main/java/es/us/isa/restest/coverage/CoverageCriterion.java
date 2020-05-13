package es.us.isa.restest.coverage;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Class that represents a specific coverage criterion, e.g. paths,
 * parameters, etc. Keeps track of the coverage level
 * 
 * @author Alberto Martin-Lopez
 */
public class CoverageCriterion {

    private CriterionType type;             // Type of coverage criterion: paths, operations, content-type, etc.
    private Map<String, Boolean> elements;  // Keys are the elements to cover and values represent whether they have already been covered or not

    /**
     * The following property's purpose is to locate the criterion inside the API resources hierarchy. There could be several parameter values
     * criteria having all the same parameter name. Therefore, it is necessary to uniquely identify each criterion to check if they are covered
     * when analysing the abstract test cases. This property makes sense for all criteria except for paths, since there is only one of this
     * kind. The longest allowed rootPath contains 3 elements: "{path}->{operationId}->{parameterName}" (parameter value criterion) OR
     * "{path}->{operationId}->{statusCode}" (response body properties criterion)
     */
    private String rootPath;

    public CoverageCriterion(CriterionType type) {
        this.type = type;
        this.elements = new HashMap<>();
        this.rootPath = "";
    }

    public CriterionType getType() {
        return type;
    }

    public void setType(CriterionType type) {
        this.type = type;
    }

    public Map<String, Boolean> getElements() {
        return elements;
    }

    public void setElements(Map<String, Boolean> elements) {
        this.elements = elements;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Helper function to return elements already covered.
     * 
     * @return Map of covered elements, i.e. all those whose value is true
     */
    public Map<String, Boolean> getCoveredElements() {
        Map<String, Boolean> coveredElements = new HashMap<>();
        for (Entry<String, Boolean> element: elements.entrySet()) {
            if (element.getValue()) {
                coveredElements.put(element.getKey(), element.getValue());
            }
        }
        return coveredElements;
    }

    /**
     * @return Number of elements in this criterion
     */
    public long getElementsCount() {
        return elements.size();
    }

    /**
     * @return Number of elements already covered in this criterion
     */
    public long getCoveredElementsCount() {
        return elements.entrySet().stream()
                .filter(e -> e.getValue())
                .count();
    }

    /**
     * Set an element as covered (set value to 'true')
     * @param newlyCoveredElement the element to cover
     */
    public void coverElement(String newlyCoveredElement) {
        if (elements.get(newlyCoveredElement) != null) { // check that the element exists
            elements.put(newlyCoveredElement, true);
        }
    }

    /**
     * Get coverage of this criterion as a percentage by dividing the
     * number of covered elements by the number of total elements.
     * @return coverage percentage
     */
    public float getCoverage() {
        if (elements.size() == 0) {
            return 100;
        }

        return 100 * (float)getCoveredElementsCount() / (float)getElementsCount();
    }
}