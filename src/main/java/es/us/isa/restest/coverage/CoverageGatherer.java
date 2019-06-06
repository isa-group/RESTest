package es.us.isa.restest.coverage;

import es.us.isa.restest.coverage.CoverageCriterion;
import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;

import static es.us.isa.restest.coverage.CriterionType.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
            coverageCriteria.addAll(getCoverageCriteria(criterionType));
        }
    }

    /**
     * Creates all coverage criteria of the type passed. The function iterates over multiple
     * elements of the API (paths, operations, etc.) depending on the criterion type. For instance,
     * for the operations criterion, it iterates over all paths; for the parameter values
     * criterion, it iterates over all paths, operations of those paths and parameters of those
     * operations.
     * 
     * @param type {@link CriterionType} to consider for the creation of {@link CoverageCriterion}s
     * @return A list containing all coverage criteria that could be created for that type
     */
    private List<CoverageCriterion> getCoverageCriteria(CriterionType type) {
        List<CoverageCriterion> criteria = new ArrayList<>(); // list of criteria to be returned
        
        if (type == PATH) {
            List<String> pathsList = new ArrayList<>(spec.getSpecification().getPaths().keySet()); // list of paths per criterion
            criteria.add(createCriterion(pathsList, PATH, ""));

        } else {
            // iterate over the paths
            Iterator<Entry<String, Path>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
            while (pathsIterator.hasNext()) {
                Entry<String, Path> currentPathEntry = pathsIterator.next();

                if (type == OPERATION) {
                    List<String> operationsList = new ArrayList<>(); // list of operations per criterion
                    for (Entry<HttpMethod, Operation> operation : currentPathEntry.getValue().getOperationMap()
                            .entrySet()) {
                        operationsList.add(operation.getKey().toString()); // collect operations for this path
                    }
                    criteria.add(createCriterion(operationsList, OPERATION, currentPathEntry.getKey()));

                } else {
                    // iterate over the operations of that path
                    Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().getOperationMap().entrySet().iterator();
                    while (operationsIterator.hasNext()) {
                        Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();

                        if (type == PARAMETER) {
                            List<String> parametersList = new ArrayList<>(); // list of parameters per criterion
                            for (Parameter parameter : currentOperationEntry.getValue().getParameters()) { // collect parameters for this operation
                                if (parameter instanceof BodyParameter) { // if the parameter is the body
                                    parametersList.add("body"); // instead of adding the body parameter name, add "body"
                                } else {
                                    parametersList.add(parameter.getName()); // add parameter name
                                }
                            }
                            criteria.add(createCriterion(parametersList, PARAMETER, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));

                        } else if (type == PARAMETER_VALUE) {
                            // iterate over the parameters of that operation
                            Iterator<Parameter> parametersIterator = currentOperationEntry.getValue().getParameters().iterator();
                            while (parametersIterator.hasNext()) {
                                Parameter currentParameter = parametersIterator.next();

                                if (currentParameter.getIn() == "query" || currentParameter.getIn() == "header") { // this criterion only applies for header and query parameters
                                    String paramType = ((AbstractSerializableParameter) currentParameter).getType();
                                    List<String> paramEnumValues = ((AbstractSerializableParameter) currentParameter).getEnum();
                                    if (paramType == "boolean" || paramEnumValues != null) { // only if the parameter has enum values or is a boolean
                                        List<String> parameterValuesList = new ArrayList<>(); // list of parameter values per criterion
                                        if (paramType == "boolean") {
                                            parameterValuesList.addAll(Arrays.asList("true", "false")); // add both boolean values to test
                                        } else {
                                            parameterValuesList.addAll(paramEnumValues); // add all enum values to test
                                        }
                                        criteria.add(createCriterion(parameterValuesList, PARAMETER_VALUE,
                                                currentPathEntry.getKey() + "->" +
                                                currentOperationEntry.getKey().toString() + "->" +
                                                currentParameter.getName()
                                        ));
                                    }
                                }
                            }
                            
                        } else if (type == INPUT_CONTENT_TYPE || type == OUTPUT_CONTENT_TYPE) {
                            List<String> contentTypesList = new ArrayList<>(); // list of content-types per criterion
                            // Set content-types to iterate over depending on the 'type' passed and whether or not the property is present in the OAS
                            List<String> contentTypes = type == INPUT_CONTENT_TYPE && currentOperationEntry.getValue().getConsumes() != null ? currentOperationEntry.getValue().getConsumes() :
                                                        type == OUTPUT_CONTENT_TYPE && currentOperationEntry.getValue().getProduces() != null ? currentOperationEntry.getValue().getProduces() : null;
                            if (contentTypes != null) { // there could be no 'consumes' or 'produces' property, so check it before
                                for (String contentType : contentTypes) {
                                    contentTypesList.add(contentType); // collect content-types for this operation
                                }
                                criteria.add(createCriterion(contentTypesList, type, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
                            }

                        } else if (type == AUTHENTICATION) {
//                            List<String> authenticationList = new ArrayList<>(); // list of authentications per criterion
//                            if (currentOperationEntry.getValue().getSecurity() != null) { // there could be no 'security' property, so check it before
//                                for (Map<String, List<String>> authenticationScheme : currentOperationEntry.getValue().getSecurity()) {
//                                    authenticationList.add(authenticationScheme.keySet().iterator().next()); // collect authentications for this operation
//                                }
//                                criteria.add(createCriterion(authenticationList, AUTHENTICATION, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
//                            }

                        } else if (type == STATUS_CODE_CLASS) {
                            List<String> statusCodeClassesList = new ArrayList<>(); // list of statusCodeClasses per criterion
                            statusCodeClassesList.add("2XX"); // it is assumed that all API operations should have a successful response
                            for (String statusCodeClass : currentOperationEntry.getValue().getResponses().keySet()) {
                                if (statusCodeClass.charAt(0) == '4') {
                                    statusCodeClassesList.add("4XX"); // add the faulty response case too
                                    break;
                                }
                            }
                            criteria.add(createCriterion(statusCodeClassesList, STATUS_CODE_CLASS, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
                        
                        } else if (type == STATUS_CODE) {
                            List<String> statusCodesList = new ArrayList<>(); // list of statusCodes per criterion
                            for (String statusCode : currentOperationEntry.getValue().getResponses().keySet()) {
                                statusCodesList.add(statusCode); // collect statusCodes for this operation
                            }
                            criteria.add(createCriterion(statusCodesList, STATUS_CODE, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
                        
                        } else if (type == RESPONSE_BODY_PROPERTIES) {
                            // iterate over the responses of that operation
                            Iterator<Entry<String, Response>> responsesIterator = currentOperationEntry.getValue().getResponses().entrySet().iterator();
                            while (responsesIterator.hasNext()) {
                                Entry<String, Response> currentResponseEntry = responsesIterator.next();

                                Property responseSchema = currentResponseEntry.getValue().getSchema();
                                if (responseSchema != null) { // if the response actually returns a body
                                    String currentResponseRef = null;
                                    List<String> responseBodyPropertiesList = new ArrayList<>(); // list of responseBodyProperties per criterion
                                    if (responseSchema.getType() == "array") {
                                        if (((ArrayProperty)responseSchema).getItems().getType() == "ref") {
                                            currentResponseRef = ((RefProperty)((ArrayProperty)responseSchema).getItems()).getSimpleRef();
                                        }
                                    } else if (responseSchema.getType() == "ref") {
                                        currentResponseRef = ((RefProperty)responseSchema).getSimpleRef();
                                    } else if (responseSchema.getType() == "object" && responseSchema instanceof ObjectProperty) {
                                        responseBodyPropertiesList.addAll(((ObjectProperty)responseSchema).getProperties().keySet()); // add response body properties to the criterion
                                    }
                                    if (currentResponseRef != null) { // if the response body refers to a Swagger definition, get properties from that object
                                        Model currentSwaggerModel = spec.getSpecification().getDefinitions().get(currentResponseRef); // get Swagger Definition associated to the Ref defined in the response
                                        responseBodyPropertiesList.addAll(currentSwaggerModel.getProperties().keySet()); // add response body properties to the criterion
                                    }
                                    if (responseBodyPropertiesList.size() > 0) { // if the response body is an object containing some properties, create criterion
                                        criteria.add(createCriterion(responseBodyPropertiesList, RESPONSE_BODY_PROPERTIES,
                                                currentPathEntry.getKey() + "->" +
                                                currentOperationEntry.getKey().toString() + "->" +
                                                currentResponseEntry.getKey()
                                        ));
                                    }
                                }
                            }
                        
                        } else if (type == PARAMETER_CONDITION) {
                            //TODO
                        
                        } else if (type == OPERATIONS_FLOW) {
                            //TODO
                        
                        } else {
                            throw new IllegalArgumentException("Unknown coverage criterion type: " + type.toString());
                        }

                    } // end of iteration of operations
                }

            } // end of iteration of paths
        }

        return criteria;
    }

    /**
     * Helper function to create a coverage criterion. Given a list of elements
     * as strings, it transforms it into a Map with Boolean values representing
     * whether that element has been covered or not. All are set to 'false' (no
     * elements covered at the beginning).
     * 
     * @param elementsList Elements (only strings) to be included in the criterion
     * @param type CriterionType of the criterion (PATH, OPERATION, etc.)
     * @param rootPath Root path of the criterion. This can vary, be composed of
     * a different number of elements depending on the criterion type
     * @return CoverageCriterion with all fields set (type, rootPath and elements)
     */
    private CoverageCriterion createCriterion(List<String> elementsList, CriterionType type, String rootPath) {
        CoverageCriterion criterion = new CoverageCriterion(type);
        Map<String, Boolean> elements = elementsList.stream()
                .collect(Collectors.toMap(
                    e -> e,
                    e -> Boolean.FALSE
                )); // create Map whose keys are the elements and whose values are 'false'
        criterion.setElements(elements);
        criterion.setRootPath(rootPath); // set rootPath for the criterion (this together with the TYPE conform a unique ID)
        return criterion;
    }

    /**
     * Dumb function to set all types of criteria to be covered
     */
    private void setDefaultCoverageCriterionTypes() {
        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
//        coverageCriterionTypes.add(PARAMETER_CONDITION);
//        coverageCriterionTypes.add(OPERATIONS_FLOW);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
//        coverageCriterionTypes.add(AUTHENTICATION);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        coverageCriterionTypes.add(RESPONSE_BODY_PROPERTIES);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);
    }
    
}