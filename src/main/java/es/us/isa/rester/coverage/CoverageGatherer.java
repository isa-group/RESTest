package es.us.isa.rester.coverage;

import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.coverage.CoverageCriterion;
import static es.us.isa.rester.coverage.CriterionType.*;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.Parameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Given a Swagger specification, obtain coverage level needed to reach 100% for
 * each criterion (both input and output criteria)
 * 
 * @author Alberto Martin-Lopez
 */
public class CoverageGatherer {

    private OpenAPISpecification spec;                  // OpenAPI specification to deduce coverage levels from
    private List<CriterionType> coverageCriterionTypes; // Types of criteria to be covered
    private List<CoverageCriterion> coverageCriteria;   // Coverage criteria to keep track of

    public CoverageGatherer(OpenAPISpecification spec) {
        this.spec = spec;
        this.coverageCriterionTypes = new ArrayList<>();
        this.coverageCriteria = new ArrayList<>();
        setDefaultCoverageCriterionTypes();
        createCoverageCriteria();
    }

    public CoverageGatherer(OpenAPISpecification spec, List<CriterionType> coverageCriterionTypes) {
        this.spec = spec;
        this.coverageCriterionTypes = coverageCriterionTypes;
        this.coverageCriteria = new ArrayList<>();
        createCoverageCriteria();
    }

    public OpenAPISpecification getSpec() {
        return this.spec;
    }

    public void setSpec(OpenAPISpecification spec) {
        this.spec = spec;
    }

    public List<CriterionType> getCoverageCriterionTypes() {
        return this.coverageCriterionTypes;
    }

    public void setCoverageCriterionTypes(List<CriterionType> coverageCriterionTypes) {
        this.coverageCriterionTypes = coverageCriterionTypes;
    }

    public List<CoverageCriterion> getCoverageCriteria() {
        return this.coverageCriteria;
    }

    public void setCoverageCriteria(List<CoverageCriterion> coverageCriteria) {
        this.coverageCriteria = coverageCriteria;
    }

    /**
     * Create coverage criteria depending on the criterion types chosen. Usually, there will be more
     * than one criterion per criterion type.
     */
    private void createCoverageCriteria() {
        for (CriterionType criterionType : coverageCriterionTypes) {
            switch (criterionType) {
            case PATH:
                coverageCriteria.add(getPathCoverageCriteria());
                break;
            case OPERATION:
                coverageCriteria.addAll(getOperationCoverageCriteria());
                break;
            case PARAMETER:
                coverageCriteria.addAll(getParameterCoverageCriteria());
                break;
            case PARAMETER_VALUE:
                coverageCriteria.addAll(getParameterValueCoverageCriteria());
                break;
            case PARAMETER_CONDITION:

                break;
            case OPERATIONS_FLOW:

                break;
            case INPUT_CONTENT_TYPE:

                break;
            case AUTHENTICATION:

                break;
            case STATUS_CODE:

                break;
            case STATUS_CODE_CLASS:

                break;
            case RESPONSE_BODY_PROPERTIES:

                break;
            case OUTPUT_CONTENT_TYPE:

                break;
            default:
                throw new IllegalArgumentException("Unknow coverage criterion type: " + criterionType.toString());
            }
        }
    }

    /**
     * @return One criterion whose allElements field corresponds to all paths
     * obtained from the Swagger specification.
     */
    private CoverageCriterion getPathCoverageCriteria() {
        CoverageCriterion pathsCriterion = new CoverageCriterion(PATH);
        pathsCriterion.setAllElements(new ArrayList<>(spec.getSpecification().getPaths().keySet()));
        return pathsCriterion;
    }

    /**
     * @return A list of CoverageCriterion, one per path defined in the OAS.
     * Every criterion contains a number of elements equal to the operations
     * defined for that path (minimum 1, maximum 4).
     */
    private List<CoverageCriterion> getOperationCoverageCriteria() {
        List<CoverageCriterion> operationsCriteria = new ArrayList<>(); // list of criteria to be returned
        List<String> operationsList = new ArrayList<>();                // list of operations per criterion
        CoverageCriterion operationsCriterion = null;                   // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                    // path containing operations to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();
            operationsList.clear();
            operationsCriterion = new CoverageCriterion(OPERATION); // create operation criterion for this path

            for (Operation operation : currentPathEntry.getValue().getOperations()) {
                operationsList.add(operation.getOperationId()); // collect operations for this path
            }
            operationsCriterion.setAllElements(new ArrayList<>(operationsList)); // add all operations to be tested to the criterion created
            operationsCriterion.setRootPath(currentPathEntry.getKey()); // set rootPath to the API path (it is unique)
            operationsCriteria.add(operationsCriterion);
        }

        return operationsCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per operation per path. Every
     * criterion contains a number of elements equal to the parameters defined
     * for that operation.
     */
    private List<CoverageCriterion> getParameterCoverageCriteria() {
        List<CoverageCriterion> parametersCriteria = new ArrayList<>(); // list of criteria to be returned
        List<String> parametersList = new ArrayList<>();                // list of parameters per criterion
        CoverageCriterion parametersCriterion = null;                   // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                    // path containing operations where to look for parameters
        Operation currentOperation = null;                              // operation containing parameters to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Operation> operationsIterator = currentPathEntry.getValue().getOperations().iterator();
            while (operationsIterator.hasNext()) {
                currentOperation = operationsIterator.next();
                parametersList.clear();
                parametersCriterion = new CoverageCriterion(PARAMETER); // create parameter criterion for this operation

                for (Parameter parameter : currentOperation.getParameters()) {
                    parametersList.add(parameter.getName()); // collect parameters for this operation
                }
                parametersCriterion.setAllElements(new ArrayList<>(parametersList)); // add all parameters to be tested to the criterion created
                parametersCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperation.getOperationId()); // put together API path and operationID to create a unique rootPath
                parametersCriteria.add(parametersCriterion);
            }
        }
        
        return parametersCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per parameter per operation per
     * path, but only in the cases where the parameter is an enum or a boolean.
     * Every criterion contains a number of elements equal to the possible values
     * the parameter can take.
     */
    private List<CoverageCriterion> getParameterValueCoverageCriteria() {
        List<CoverageCriterion> parameterValuesCriteria = new ArrayList<>();    // list of criteria to be returned
        List<Object> parameterValuesList = new ArrayList<>();                   // list of parameter values per criterion
        CoverageCriterion parameterValuesCriterion = null;                      // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                            // path containing operations where to look for parameters
        Operation currentOperation = null;                                      // operation containing parameters where to look for values
        Parameter currentParameter = null;                                      // parameter with a number of possible values to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Operation> operationsIterator = currentPathEntry.getValue().getOperations().iterator();
            while (operationsIterator.hasNext()) {
                currentOperation = operationsIterator.next();

                // iterate over the parameters of that operation
                Iterator<Parameter> parametersIterator = currentOperation.getParameters().iterator();
                while (parametersIterator.hasNext()) {
                    currentParameter = parametersIterator.next();

                    if (currentParameter.getIn() == "query" || currentParameter.getIn() == "header") { // this criterion only applies for header and query parameters
                        String paramType = ((AbstractSerializableParameter) currentParameter).getType();
                        List<String> paramEnumValues = ((AbstractSerializableParameter) currentParameter).getEnum();

                        if (paramType == "boolean" || paramEnumValues != null) { // only if the parameter has enum values or is a boolean
                            parameterValuesList.clear();
                            parameterValuesCriterion = new CoverageCriterion(PARAMETER_VALUE); // create parameter value criterion for this parameter

                            if (paramType == "boolean") {
                                parameterValuesList.addAll(Arrays.asList(new Boolean(true), new Boolean(false))); // add both boolean values to test
                            } else {
                                parameterValuesList.addAll(paramEnumValues); // add all enum values to test
                            }

                            parameterValuesCriterion.setAllElements(new ArrayList<>(parameterValuesList)); // add all parameter values to be tested to the criterion created
                            parameterValuesCriterion.setRootPath(
                                currentPathEntry.getKey() + "->" +
                                currentOperation.getOperationId() + "->" +
                                currentParameter.getName()
                            ); // put together API path, operationID and parameter name to create a unique rootPath
                            parameterValuesCriteria.add(parameterValuesCriterion);
                        }
                    }
                }
            }
        }
        
        return parameterValuesCriteria;
    }

    /**
     * Dumb function to set all types of criteria to be covered
     */
    private void setDefaultCoverageCriterionTypes() {
        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
        coverageCriterionTypes.add(PARAMETER_CONDITION);
        coverageCriterionTypes.add(OPERATIONS_FLOW);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(AUTHENTICATION);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        coverageCriterionTypes.add(RESPONSE_BODY_PROPERTIES);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);
    }
    
}