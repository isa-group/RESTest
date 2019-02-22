package es.us.isa.rester.coverage;

import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.coverage.CoverageCriterion;
import static es.us.isa.rester.coverage.CriterionType.*;

import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;

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
                //TODO
                break;
            case OPERATIONS_FLOW:
                //TODO
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
                coverageCriteria.addAll(getResponseBodyPropertiesCoverageCriteria());
                break;
            case OUTPUT_CONTENT_TYPE:
                coverageCriteria.addAll(getContentTypeCoverageCriteria(OUTPUT_CONTENT_TYPE));
                break;
            default:
                throw new IllegalArgumentException("Unknown coverage criterion type: " + criterionType.toString());
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

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();
            List<String> operationsList = new ArrayList<>(); // list of operations per criterion
            CoverageCriterion operationsCriterion = new CoverageCriterion(OPERATION); // create operation criterion for this path

            for (Entry<HttpMethod, Operation> operation : currentPathEntry.getValue().getOperationMap().entrySet()) {
                operationsList.add(operation.getKey().toString()); // collect operations for this path
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

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
                List<String> parametersList = new ArrayList<>(); // list of parameters per criterion
                CoverageCriterion parametersCriterion = new CoverageCriterion(PARAMETER); // create parameter criterion for this operation

                for (Parameter parameter : currentOperationEntry.getValue().getParameters()) {
                    parametersList.add(parameter.getName()); // collect parameters for this operation
                }
                parametersCriterion.setAllElements(new ArrayList<>(parametersList)); // add all parameters to be tested to the criterion created
                parametersCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()); // put together API path and operationID to create a unique rootPath
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
        List<CoverageCriterion> parameterValuesCriteria = new ArrayList<>(); // list of criteria to be returned

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();

                // iterate over the parameters of that operation
                Iterator<Parameter> parametersIterator = currentOperationEntry.getValue().getParameters().iterator();
                while (parametersIterator.hasNext()) {
                    Parameter currentParameter = parametersIterator.next();

                    if (currentParameter.getIn() == "query" || currentParameter.getIn() == "header") { // this criterion only applies for header and query parameters
                        String paramType = ((AbstractSerializableParameter) currentParameter).getType();
                        List<String> paramEnumValues = ((AbstractSerializableParameter) currentParameter).getEnum();

                        if (paramType == "boolean" || paramEnumValues != null) { // only if the parameter has enum values or is a boolean
                            List<Object> parameterValuesList = new ArrayList<>(); // list of parameter values per criterion
                            CoverageCriterion parameterValuesCriterion = new CoverageCriterion(PARAMETER_VALUE); // create parameter value criterion for this parameter

                            if (paramType == "boolean") {
                                parameterValuesList.addAll(Arrays.asList("true", "false")); // add both boolean values to test
                            } else {
                                parameterValuesList.addAll(paramEnumValues); // add all enum values to test
                            }

                            parameterValuesCriterion.setAllElements(new ArrayList<>(parameterValuesList)); // add all parameter values to be tested to the criterion created
                            parameterValuesCriterion.setRootPath(
                                currentPathEntry.getKey() + "->" +
                                currentOperationEntry.getKey().toString() + "->" +
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

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
                List<String> contentTypesList = new ArrayList<>(); // list of parameters per criterion
                CoverageCriterion contentTypesCriterion = new CoverageCriterion(type); // create content-type criterion for this operation

                // Set content-types to iterate over depending on the 'type' passed and whether or not the property is present in the OAS
                List<String> contentTypes = type == INPUT_CONTENT_TYPE && currentOperationEntry.getValue().getConsumes() != null ? currentOperationEntry.getValue().getConsumes() :
                                            type == OUTPUT_CONTENT_TYPE && currentOperationEntry.getValue().getProduces() != null ? currentOperationEntry.getValue().getProduces() : null;

                if (contentTypes != null) { // there could be no 'consumes' or 'produces' property, so check it before
                    for (String contentType : contentTypes) {
                        contentTypesList.add(contentType); // collect content-types for this operation
                    }
                    contentTypesCriterion.setAllElements(new ArrayList<>(contentTypesList)); // add all content-types to be tested to the criterion created
                    contentTypesCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()); // put together API path and operationID to create a unique rootPath
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

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
                List<String> authenticationList = new ArrayList<>(); // list of authentications per criterion
                CoverageCriterion authenticationCriterion = new CoverageCriterion(AUTHENTICATION); // create authentication criterion for this operation

                if (currentOperationEntry.getValue().getSecurity() != null) { // there could be no 'security' property, so check it before
                    for (Map<String, List<String>> authenticationScheme : currentOperationEntry.getValue().getSecurity()) {
                        authenticationList.add(authenticationScheme.keySet().iterator().next()); // collect authentications for this operation
                    }
                    authenticationCriterion.setAllElements(new ArrayList<>(authenticationList)); // add all authentications to be tested to the criterion created
                    authenticationCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()); // put together API path and operationID to create a unique rootPath
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

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
                List<String> statusCodesList = new ArrayList<>(); // list of statusCodes per criterion
                CoverageCriterion statusCodesCriterion = new CoverageCriterion(STATUS_CODE); // create statusCode criterion for this operation

                for (String statusCode : currentOperationEntry.getValue().getResponses().keySet()) {
                    statusCodesList.add(statusCode); // collect statusCodes for this operation
                }
                statusCodesCriterion.setAllElements(new ArrayList<>(statusCodesList)); // add all statusCodes to be tested to the criterion created
                statusCodesCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()); // put together API path and operationID to create a unique rootPath
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

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
                List<String> statusCodeClassesList = new ArrayList<>(); // list of statusCodeClasses per criterion
                CoverageCriterion statusCodeClassesCriterion = new CoverageCriterion(STATUS_CODE_CLASS); // create statusCodeClass criterion for this operation

                statusCodeClassesList.add("2XX"); // it is assumed that all API operation should have a successful response

                for (String statusCodeClass : currentOperationEntry.getValue().getResponses().keySet()) {
                    if (statusCodeClass.charAt(0) == '4') {
                        statusCodeClassesList.add("4XX"); // add the faulty response case too
                        break;
                    }
                }
                statusCodeClassesCriterion.setAllElements(new ArrayList<>(statusCodeClassesList)); // add all statusCodeClasses to be tested to the criterion created
                statusCodeClassesCriterion.setRootPath(currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()); // put together API path and operationID to create a unique rootPath
                statusCodeClassesCriteria.add(statusCodeClassesCriterion);
            }
        }
        
        return statusCodeClassesCriteria;
    }

    /**
     * @return A list of CoverageCriterion, one per operation per path. Every
     * criterion contains a number of elements equal to the properties defined
     * in the response body of that operation
     */
    private List<CoverageCriterion> getResponseBodyPropertiesCoverageCriteria() {
        List<CoverageCriterion> responseBodyPropertiesCriteria = new ArrayList<>(); // list of criteria to be returned

        // iterate over the paths
        Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, Path> currentPathEntry = pathsIterator.next();

            // iterate over the operations of that path
            Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
            while (operationsIterator.hasNext()) {
                Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
                
                // iterate over the responses of that operation
                Iterator<Entry<String, Response>> responsesIterator = currentOperationEntry.getValue().getResponses().entrySet().iterator();
                while (responsesIterator.hasNext()) {
                    Entry<String, Response> currentResponseEntry = responsesIterator.next();

                    Property responseSchema = currentResponseEntry.getValue().getSchema();
                    if (responseSchema != null) { // if the response actually returns a body
                        String currentResponseRef = null;
                        List<Object> responseBodyPropertiesList = new ArrayList<>(); // list of responseBodyProperties per criterion
                        
                        if (responseSchema.getType() == "array") {
                            if (((ArrayProperty)responseSchema).getItems().getType() == "ref") {
                                currentResponseRef = ((RefProperty)((ArrayProperty)responseSchema).getItems()).getSimpleRef();
                            }
                        } else if (responseSchema.getType() == "ref") {
                            currentResponseRef = ((RefProperty)responseSchema).getSimpleRef();
                        } else if (responseSchema.getType() == "object" && responseSchema instanceof ObjectProperty) {
                            responseBodyPropertiesList.addAll(new ArrayList<>(Arrays.asList(((ObjectProperty)responseSchema).getProperties().keySet().toArray()))); // add response body properties to the criterion
                        }

                        if (currentResponseRef != null) { // if the response body refers to a Swagger definition, get properties from that object
                            Model currentSwaggerModel = spec.getSpecification().getDefinitions().get(currentResponseRef); // get Swagger Definition associated to the Ref defined in the response
                            responseBodyPropertiesList.addAll(new ArrayList<>(Arrays.asList(currentSwaggerModel.getProperties().keySet().toArray()))); // add response body properties to the criterion
                        }
                        if (responseBodyPropertiesList.size() > 0) { // if the response body is an object containing some properties, create criterion
                            CoverageCriterion responseBodyPropertiesCriterion = new CoverageCriterion(RESPONSE_BODY_PROPERTIES); // create responseBodyProperties criterion for this response
                            responseBodyPropertiesCriterion.setAllElements(new ArrayList<>(responseBodyPropertiesList)); // add all response body properties to be tested to the criterion created
                            responseBodyPropertiesCriterion.setRootPath(
                                currentPathEntry.getKey() + "->" +
                                currentOperationEntry.getKey().toString() + "->" +
                                currentResponseEntry.getKey()
                            ); // put together API path, operationID and response code to create a unique rootPath
                            responseBodyPropertiesCriteria.add(responseBodyPropertiesCriterion);
                        }
                    }
                }
            }
        }
        
        return responseBodyPropertiesCriteria;
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