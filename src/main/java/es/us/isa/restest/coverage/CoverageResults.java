package es.us.isa.restest.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.util.PropertyManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static es.us.isa.restest.util.CSVManager.createCSVwithHeader;
import static es.us.isa.restest.util.CSVManager.writeCSVRow;
import static es.us.isa.restest.util.FileManager.checkIfExists;

/**
 * Class that represents the results of the coverage computation of a test. It details the total
 * coverage, the input coverage, the output coverage and the coverage of every coverage criterion, plus
 * the coverage of every criterion type and the Test Coverage Level (TCL).
 */
public class CoverageResults {

    private int coverageLevel;                      //The coverage level of the test. It is defined by the test coverage model
    private float totalCoverage;                    //The total coverage of the test
    private float inputCoverage;                    //The input coverage of the test
    private float outputCoverage;                   //The output coverage of the test
    private float pathCoverage;                     //The path (/pets, /pets/{id}...) coverage of the test
    private float operationCoverage;                //The operation (GET, POST...) coverage of the test
    private float inputContentTypeCoverage;         //The input content type (application/json...) coverage of the test
    private float outputContentTypeCoverage;        //The output content type (application/json...) coverage of the test
    private float parameterCoverage;                //The parameter (input parameters of an operation, e.g. lat, lon, order...) coverage of the test
    private float statusCodeClassCoverage;          //The status code class (2XX,...) coverage of the test
    private float parameterValueCoverage;           //The parameter value (values of an enum, booleans...) coverage of the test
    private float statusCodeCoverage;               //The status code (200, 400, 404,...) coverage of the test
    //    private float parameterConditionCoverage;
    private float responseBodyPropertiesCoverage;   //The response body properties (properties of the JSON response) coverage of the test
    //    private float operationsFlowCoverage;
    private List<CoverageCriterionResult> coverageOfCoverageCriteria;

    public CoverageResults(CoverageMeter coverageMeter) {
        this.totalCoverage = coverageMeter.getTotalCoverage();
        this.inputCoverage = coverageMeter.getInputCoverage();
        this.outputCoverage = coverageMeter.getOutputCoverage();
        this.coverageOfCoverageCriteria = new ArrayList<>();
    }

    public CoverageResults(float totalCoverage, float inputCoverage, float outputCoverage) {
        this.totalCoverage = totalCoverage;
        this.inputCoverage = inputCoverage;
        this.outputCoverage = outputCoverage;
        this.coverageOfCoverageCriteria = new ArrayList<>();
    }

    public int getCoverageLevel() {
        return coverageLevel;
    }

    public void setCoverageLevel(int coverageLevel) {
        this.coverageLevel = coverageLevel;
    }

    public float getPathCoverage() {
        return pathCoverage;
    }

    public void setPathCoverage(float pathCoverage) {
        this.pathCoverage = pathCoverage;
    }

    public float getOperationCoverage() {
        return operationCoverage;
    }

    public void setOperationCoverage(float operationCoverage) {
        this.operationCoverage = operationCoverage;
    }

    public float getInputContentTypeCoverage() {
        return inputContentTypeCoverage;
    }

    public void setInputContentTypeCoverage(float inputContentTypeCoverage) {
        this.inputContentTypeCoverage = inputContentTypeCoverage;
    }

    public float getOutputContentTypeCoverage() {
        return outputContentTypeCoverage;
    }

    public void setOutputContentTypeCoverage(float outputContentTypeCoverage) {
        this.outputContentTypeCoverage = outputContentTypeCoverage;
    }

    public float getParameterCoverage() {
        return parameterCoverage;
    }

    public void setParameterCoverage(float parameterCoverage) {
        this.parameterCoverage = parameterCoverage;
    }

    public float getStatusCodeClassCoverage() {
        return statusCodeClassCoverage;
    }

    public void setStatusCodeClassCoverage(float statusCodeClassCoverage) {
        this.statusCodeClassCoverage = statusCodeClassCoverage;
    }

    public float getParameterValueCoverage() {
        return parameterValueCoverage;
    }

    public void setParameterValueCoverage(float parameterValueCoverage) {
        this.parameterValueCoverage = parameterValueCoverage;
    }

    public float getStatusCodeCoverage() {
        return statusCodeCoverage;
    }

    public void setStatusCodeCoverage(float statusCodeCoverage) {
        this.statusCodeCoverage = statusCodeCoverage;
    }

    public float getResponseBodyPropertiesCoverage() {
        return responseBodyPropertiesCoverage;
    }

    public void setResponseBodyPropertiesCoverage(float responseBodyPropertiesCoverage) {
        this.responseBodyPropertiesCoverage = responseBodyPropertiesCoverage;
    }

    public float getInputCoverage() {
        return inputCoverage;
    }

    public void setInputCoverage(float inputCoverage) {
        this.inputCoverage = inputCoverage;
    }

    public float getOutputCoverage() {
        return outputCoverage;
    }

    public void setOutputCoverage(float outputCoverage) {
        this.outputCoverage = outputCoverage;
    }

    public List<CoverageCriterionResult> getCoverageOfCoverageCriteria() {
        return coverageOfCoverageCriteria;
    }

    public void setCoverageOfCoverageCriteria(List<CoverageCriterionResult> coverageOfCoverageCriteria) {
        this.coverageOfCoverageCriteria = coverageOfCoverageCriteria;
    }

    public float getTotalCoverage() {
        return totalCoverage;
    }

    public void setTotalCoverage(float totalCoverage) {
        this.totalCoverage = totalCoverage;
    }

    /**
     * This method sets the map that defines the coverage of every coverage criteria from
     * a CoverageMeter.
     * @param covMeter the CoverageMeter with the coverage computation
     */

    public void setCoverageOfCoverageCriteriaFromCoverageMeter(CoverageMeter covMeter) {
        List<CoverageCriterion> criteria = covMeter.getCoverageGatherer().getCoverageCriteria();
        for(CoverageCriterion c : criteria) {
            String typeAndRootPath = c.getType().name()+ "/" + c.getRootPath();
            CoverageCriterionResult res = new CoverageCriterionResult(typeAndRootPath, covMeter.getCriterionCoverage(c.getType(), c.getRootPath()));
            coverageOfCoverageCriteria.add(res);
        }
    }

    /**
     * This method sets the coverage of every criterion type from a CoverageMeter. Once the method
     * has finished, it calls another method to set the test coverage level.
     * @param covMeter the CoverageMeter with the coverage computation
     */
    public void setCoverageOfCriterionTypeFromCoverageMeter(CoverageMeter covMeter) {
        List<CriterionType> types = CriterionType.getTypes(null);
        for(CriterionType ct : types) {
            float cov = covMeter.getCriterionTypeCoverage(ct);
            switch(ct) {
                case PATH:
                    this.pathCoverage = cov;
                    break;
                case OPERATION:
                    this.operationCoverage = cov;
                    break;
                case PARAMETER:
                    this.parameterCoverage = cov;
                    break;
                case PARAMETER_VALUE:
                    this.parameterValueCoverage = cov;
                    break;
//                case PARAMETER_CONDITION:
//                    this.parameterConditionCoverage = cov;
//                    break;
//                case OPERATIONS_FLOW:
//                    this.operationsFlowCoverage = cov;
//                    break;
                case INPUT_CONTENT_TYPE:
                    this.inputContentTypeCoverage = cov;
                    break;
                case STATUS_CODE:
                    this.statusCodeCoverage = cov;
                    break;
                case STATUS_CODE_CLASS:
                    this.statusCodeClassCoverage = cov;
                    break;
                case RESPONSE_BODY_PROPERTIES:
                    this.responseBodyPropertiesCoverage = cov;
                    break;
                case OUTPUT_CONTENT_TYPE:
                    this.outputContentTypeCoverage = cov;
                    break;
            }
        }
        setTestCoverageLevel();
    }

    /**
     * This method sets the test coverage level. The coverage level is defined by the Test Coverage Model
     * (TCM):
     *      - Level 0 represents a test suite where no coverage criterion is fully met. (Default level)
     *      - Level 1 requires paths to be fully covered.
     *      - Level 2 requires level 1 and every operation to be covered.
     *      - Level 3 requires level 2 and a 100% coverage of both input and output content-type criteria.
     *      - Level 4 requires level 3 and a 100% coverage of both parameters and status code classes criteria.
     *      - Level 5 requires level 4 and every parameter value and status code to be covered.
     *      - Level 6 requires level 5 and a 100% coverage of response body properties criteria.
     *      - Level 7: in a distant future.
     */
    private void setTestCoverageLevel() {
        int level = 0;

        if(pathCoverage == 100.) {
            level = 1;
        }

        if(level == 1 && operationCoverage == 100.) {
            level = 2;
        }

        if(level == 2 && inputContentTypeCoverage == 100. && outputContentTypeCoverage == 100.) {
            level = 3;
        }

        if(level == 3 && parameterCoverage == 100. && statusCodeClassCoverage == 100.) {
            level = 4;
        }

        if(level == 4 && parameterValueCoverage == 100. && statusCodeCoverage == 100.) {
            level = 5;
        }

        if(level == 5 && responseBodyPropertiesCoverage == 100.) {
            level = 6;
        }

        this.coverageLevel = level;
    }

    public void exportCoverageReportToJSON(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(new File(path), this);
    }

    public void exportCoverageReportToCSV(String path) {
        if (!checkIfExists(path))  { // If the file doesn't exist, create it (only once)
            StringBuilder header = new StringBuilder("coverageLevel,totalCoverage,inputCoverage,outputCoverage,pathCoverage,operationCoverage,inputContentTypeCoverage," +
                    "outputContentTypeCoverage,parameterCoverage,statusCodeClassCoverage,parameterValueCoverage,statusCodeCoverage,responseBodyPropertiesCoverage");
            for(CoverageCriterionResult cc : coverageOfCoverageCriteria) {
                header.append(",").append(cc.getCoverageCriterion());
            }
            createCSVwithHeader(path, header.toString());
        }

        StringBuilder row = new StringBuilder(coverageLevel + "," + totalCoverage + "," + inputCoverage + "," + outputCoverage + "," + pathCoverage + "," + operationCoverage + "," + inputContentTypeCoverage +
                "," + outputContentTypeCoverage + "," + parameterCoverage + "," + statusCodeClassCoverage + "," + parameterValueCoverage + "," + statusCodeCoverage + "," +
                responseBodyPropertiesCoverage);
        for(CoverageCriterionResult cc : coverageOfCoverageCriteria) {
            row.append(",").append(cc.getCoverage());
        }
        writeCSVRow(path, row.toString());
    }


}
