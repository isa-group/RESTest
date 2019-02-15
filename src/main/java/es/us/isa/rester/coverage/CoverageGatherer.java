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
import java.util.Map;
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
                coverageCriteria.addAll(getPathCoverageCriteria());
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
                coverageCriteria.addAll(getContentTypeCoverageCriteria(INPUT_CONTENT_TYPE));
                break;
            case AUTHENTICATION:
                coverageCriteria.addAll(getAuthenticationCoverageCriteria());
                break;
            case STATUS_CODE:
                coverageCriteria.addAll(getStatusCodeCoverageCriteria());
                break;
            case STATUS_CODE_CLASS:
                coverageCriteria.addAll(getStatusCodeClassCoverageCriteria());
                break;
            case RESPONSE_BODY_PROPERTIES:

                break;
            case OUTPUT_CONTENT_TYPE:
                coverageCriteria.addAll(getContentTypeCoverageCriteria(OUTPUT_CONTENT_TYPE));
                break;
            default:
                throw new IllegalArgumentException("Unknow coverage criterion type: " + criterionType.toString());
            }
        }
    }

    /**
     * @return One criterion whose allElements field corresponds to all paths
     * obtained from the Swagger specification
     */
    private List<CoverageCriterion> getPathCoverageCriteria() {
        List<CoverageCriterion> pathsCriteria = new ArrayList<>(); // list of criteria to be returned

        CoverageCriterion pathsCriterion = new CoverageCriterion(PATH);
        pathsCriterion.setAllElements(new ArrayList<>(spec.getSpecification().getPaths().keySet()));
        pathsCriterion.setRootPath(""); // the paths criterion is the only one without a parent element (highest in the hierarchy)
        pathsCriteria.add(pathsCriterion);

        return pathsCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per path defined in the OAS.
     * Every criterion contains a number of elements equal to the operations
     * defined for that path (minimum 1, maximum 4)
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
     * for that operation
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
     * the parameter can take
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
     * @param type CriterionType, either INPUT_CONTENT_TYPE or OUTPUT_CONTENT_TYPE
     * @return A list of CoverageCriterion, one per operation per path. Every
     * criterion contains a number of elements equal to the content-types defined
     * (in the 'consumes' or 'produces' field) for that operation
     */
    private List<CoverageCriterion> getContentTypeCoverageCriteria(CriterionType type) {
        List<CoverageCriterion> contentTypesCriteria = new ArrayList<>();   // list of criteria to be returned
        List<String> contentTypesList = new ArrayList<>();                  // list of parameters per criterion
        CoverageCriterion contentTypesCriterion = null;                     // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                        // path containing operations where to look for content types
        Operation currentOperation = null;                                  // operation containing content-types to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Operation> operationsIterator = currentPathEntry.getValue().getOperations().iterator();
            while (operationsIterator.hasNext()) {
                currentOperation = operationsIterator.next();
                contentTypesList.clear();
                contentTypesCriterion = new CoverageCriterion(type); // create content-type criterion for this operation

                // Set content-types to iterate over depending on the 'type' passed and whether or not the property is present in the OAS
                List<String> contentTypes = type == INPUT_CONTENT_TYPE && currentOperation.getConsumes() != null ? currentOperation.getConsumes() :
                                            type == OUTPUT_CONTENT_TYPE && currentOperation.getProduces() != null ? currentOperation.getProduces() : null;

                if (contentTypes != null) { // there could be no 'consumes' or 'produces' property, so check it before
                    for (String contentType : contentTypes) {
                        contentTypesList.add(contentType); // collect content-types for this operation
                    }
                    contentTypesCriterion.setAllElements(new ArrayList<>(contentTypesList)); // add all content-types to be tested to the criterion created
                    contentTypesCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperation.getOperationId()); // put together API path and operationID to create a unique rootPath
                    contentTypesCriteria.add(contentTypesCriterion);
                }
            }
        }
        
        return contentTypesCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per operation per path. Every
     * criterion contains a number of elements equal to the authentication
     * schemes defined for that operation
     */
    private List<CoverageCriterion> getAuthenticationCoverageCriteria() {
        List<CoverageCriterion> authenticationCriteria = new ArrayList<>(); // list of criteria to be returned
        List<String> authenticationList = new ArrayList<>();                // list of authentications per criterion
        CoverageCriterion authenticationCriterion = null;                   // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                        // path containing operations where to look for authentications
        Operation currentOperation = null;                                  // operation containing authentications to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Operation> operationsIterator = currentPathEntry.getValue().getOperations().iterator();
            while (operationsIterator.hasNext()) {
                currentOperation = operationsIterator.next();
                authenticationList.clear();
                authenticationCriterion = new CoverageCriterion(AUTHENTICATION); // create authentication criterion for this operation

                if (currentOperation.getSecurity() != null) { // there could be no 'security' property, so check it before
                    for (Map<String, List<String>> authenticationScheme : currentOperation.getSecurity()) {
                        authenticationList.add(authenticationScheme.keySet().iterator().next()); // collect authentications for this operation
                    }
                    authenticationCriterion.setAllElements(new ArrayList<>(authenticationList)); // add all authentications to be tested to the criterion created
                    authenticationCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperation.getOperationId()); // put together API path and operationID to create a unique rootPath
                    authenticationCriteria.add(authenticationCriterion);
                }
            }
        }
        
        return authenticationCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per operation per path. Every
     * criterion contains a number of elements equal to the status codes defined
     * for that operation
     */
    private List<CoverageCriterion> getStatusCodeCoverageCriteria() {
        List<CoverageCriterion> statusCodesCriteria = new ArrayList<>();    // list of criteria to be returned
        List<String> statusCodesList = new ArrayList<>();                   // list of statusCodes per criterion
        CoverageCriterion statusCodesCriterion = null;                      // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                        // path containing operations where to look for statusCodes
        Operation currentOperation = null;                                  // operation containing statusCodes to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Operation> operationsIterator = currentPathEntry.getValue().getOperations().iterator();
            while (operationsIterator.hasNext()) {
                currentOperation = operationsIterator.next();
                statusCodesList.clear();
                statusCodesCriterion = new CoverageCriterion(STATUS_CODE); // create statusCode criterion for this operation

                for (String statusCode : currentOperation.getResponses().keySet()) {
                    statusCodesList.add(statusCode); // collect statusCodes for this operation
                }
                statusCodesCriterion.setAllElements(new ArrayList<>(statusCodesList)); // add all statusCodes to be tested to the criterion created
                statusCodesCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperation.getOperationId()); // put together API path and operationID to create a unique rootPath
                statusCodesCriteria.add(statusCodesCriterion);
            }
        }
        
        return statusCodesCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per operation per path. Every
     * criterion contains at most 2 elements, one per status code class (2XX
     * or 4XX)
     */
    private List<CoverageCriterion> getStatusCodeClassCoverageCriteria() {
        List<CoverageCriterion> statusCodeClassesCriteria = new ArrayList<>();  // list of criteria to be returned
        List<String> statusCodeClassesList = new ArrayList<>();                 // list of statusCodeClasses per criterion
        CoverageCriterion statusCodeClassesCriterion = null;                    // criterion to add to the list to be returned
        Entry<String, Path> currentPathEntry = null;                            // path containing operations where to look for statusCodeClasses
        Operation currentOperation = null;                                      // operation containing statusCodeClasses to add to the criterion

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Operation> operationsIterator = currentPathEntry.getValue().getOperations().iterator();
            while (operationsIterator.hasNext()) {
                currentOperation = operationsIterator.next();
                statusCodeClassesList.clear();
                statusCodeClassesCriterion = new CoverageCriterion(STATUS_CODE_CLASS); // create statusCodeClass criterion for this operation

                statusCodeClassesList.add("2XX"); // it is assumed that all API operation should have a successful response

                for (String statusCodeClass : currentOperation.getResponses().keySet()) {
                    if (statusCodeClass.charAt(0) == '4') {
                        statusCodeClassesList.add("4XX"); // add the faulty response case too
                        break;
                    }
                }
                statusCodeClassesCriterion.setAllElements(new ArrayList<>(statusCodeClassesList)); // add all statusCodeClasses to be tested to the criterion created
                statusCodeClassesCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperation.getOperationId()); // put together API path and operationID to create a unique rootPath
                statusCodeClassesCriteria.add(statusCodeClassesCriterion);
            }
        }
        
        return statusCodeClassesCriteria;
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