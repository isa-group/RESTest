package es.us.isa.restest.coverage;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

import static es.us.isa.restest.coverage.CriterionType.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Given an OpenAPI specification, obtain coverage level needed to reach 100% for
 * each criterion (both input and output criteria)
 * 
 * @author Alberto Martin-Lopez
 */
public class CoverageGatherer {

    public static final String BOOLEAN_TYPE = "boolean";
    private OpenAPISpecification spec;                  // OpenAPI specification to deduce coverage levels from
    private List<CriterionType> coverageCriterionTypes; // Types of criteria to be covered
    private List<CoverageCriterion> coverageCriteria;   // Coverage criteria to keep track of
//    private int bodyPropertyDepthLevel = 0;

    public static final String MEDIA_TYPE_APPLICATION_JSON_REGEX = "^application/.*(\\\\+)?json.*$";
    public static final String MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    public static final String MEDIA_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";

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
            getOperationCoverageCriteria(type, criteria);
        }

        return criteria;
    }

    private void getOperationCoverageCriteria(CriterionType type, List<CoverageCriterion> criteria) {
        // iterate over the paths
        Iterator<Entry<String, PathItem>> pathsIterator = spec.getSpecification().getPaths().entrySet().iterator();
        while (pathsIterator.hasNext()) {
            Entry<String, PathItem> currentPathEntry = pathsIterator.next();

            if (type == OPERATION) {
                List<String> operationsList = new ArrayList<>(); // list of operations per criterion
                for (Entry<HttpMethod, Operation> operation : currentPathEntry.getValue().readOperationsMap()
                        .entrySet()) {
                    operationsList.add(operation.getKey().toString()); // collect operations for this path
                }
                criteria.add(createCriterion(operationsList, OPERATION, currentPathEntry.getKey()));

            } else {
                getAnotherCoverageCriteria(type, criteria, currentPathEntry);
            }

        } // end of iteration of paths
    }

    private void getAnotherCoverageCriteria(CriterionType type, List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry) {
        // iterate over the operations of that path
        Iterator<Entry<HttpMethod, Operation>> operationsIterator = currentPathEntry.getValue().readOperationsMap().entrySet().iterator();
        while (operationsIterator.hasNext()) {
            Entry<HttpMethod, Operation> currentOperationEntry = operationsIterator.next();
            RequestBody requestBody = currentOperationEntry.getValue().getRequestBody();

            if (type == PARAMETER) {
                getParameterCoverageCriteria(criteria, currentPathEntry, currentOperationEntry, requestBody);
            } else if (type == PARAMETER_VALUE) {
                getParameterValueCoverageCriteria(criteria, currentPathEntry, currentOperationEntry);
            } else if (type == INPUT_CONTENT_TYPE) {
                getInputContentTypeCoverageCriteria(criteria, currentPathEntry, currentOperationEntry);
            } else if (type == OUTPUT_CONTENT_TYPE) {
                getOutputContentTypeCoverageCriteria(criteria, currentPathEntry, currentOperationEntry);
            } else if (type == STATUS_CODE_CLASS) {
                getStatusCodeClassCoverageCriteria(criteria, currentPathEntry, currentOperationEntry);
            } else if (type == STATUS_CODE) {
                getStatusCodeCoverageCriteria(criteria, currentPathEntry, currentOperationEntry);
            } else if (type == RESPONSE_BODY_PROPERTIES) {
                getResponseBodyPropertiesCoverageCriteria(criteria, currentPathEntry, currentOperationEntry);
            } else if (type == AUTHENTICATION) {
                //TODO: Remove
//                            List<String> authenticationList = new ArrayList<>(); // list of authentications per criterion
//                            if (currentOperationEntry.getValue().getSecurity() != null) { // there could be no 'security' property, so check it before
//                                for (Map<String, List<String>> authenticationScheme : currentOperationEntry.getValue().getSecurity()) {
//                                    authenticationList.add(authenticationScheme.keySet().iterator().next()); // collect authentications for this operation
//                                }
//                                criteria.add(createCriterion(authenticationList, AUTHENTICATION, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
//                            }

            } else if (type == PARAMETER_CONDITION) {
                //TODO: Probably remove

            } else if (type == OPERATIONS_FLOW) {
                //TODO: In a distant future

            } else {
                throw new IllegalArgumentException("Unknown coverage criterion type: " + type.toString());
            }

        } // end of iteration of operations
    }

    private void getParameterCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry, RequestBody requestBody) {
        List<String> parametersList = new ArrayList<>(); // list of parameters per criterion

        if(currentOperationEntry.getValue().getParameters() != null) {
            for (Parameter parameter : currentOperationEntry.getValue().getParameters()) { // collect query, path and header parameters for this operation
                parametersList.add(parameter.getName()); // add parameter name
            }
        }

        if(requestBody != null && requestBody.getContent().keySet().stream().anyMatch(x -> x.matches(MEDIA_TYPE_APPLICATION_JSON_REGEX))) { //if request body is not null and accepts application/json
            parametersList.add("body"); //add body parameter
        } else if(requestBody != null && (requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) || (requestBody.getContent().containsKey(MEDIA_TYPE_MULTIPART_FORM_DATA)))) {

            MediaType mediaType = requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ?
                    requestBody.getContent().get(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) :
                    requestBody.getContent().get(MEDIA_TYPE_MULTIPART_FORM_DATA);

            for(Object entry : mediaType.getSchema().getProperties().entrySet()) {
                parametersList.add(((Entry<String, Schema>) entry).getKey());
            }
        }
        criteria.add(createCriterion(parametersList, PARAMETER, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
    }

    private void getParameterValueCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry) {
        // iterate over the parameters of that operation
        if(currentOperationEntry.getValue().getParameters() != null) {
            Iterator<Parameter> parametersIterator = currentOperationEntry.getValue().getParameters().iterator();

            while (parametersIterator.hasNext()) {
                Parameter currentParameter = parametersIterator.next();

                // this criterion only applies for header, query and path parameters
                if (currentParameter.getIn().equals("query") || currentParameter.getIn().equals("header") || currentParameter.getIn().equals("path")) {
                    List<String> parameterValuesList = getSchemaValues(currentParameter.getSchema());
                    if (!parameterValuesList.isEmpty()) { // only if the parameter has enum values or is a boolean
                        criteria.add(createCriterion(parameterValuesList, PARAMETER_VALUE,
                                currentPathEntry.getKey() + "->" +
                                        currentOperationEntry.getKey().toString() + "->" +
                                        currentParameter.getName()
                        ));
                    }
                }
            }
        }

        RequestBody requestBody = currentOperationEntry.getValue().getRequestBody();
        if (requestBody != null && (requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) || (requestBody.getContent().containsKey(MEDIA_TYPE_MULTIPART_FORM_DATA)))) {
            getFormDataParameterValues(criteria, currentPathEntry, currentOperationEntry, requestBody);
        }
    }

    private List<String> getSchemaValues(Schema schema) {
        List<String> paramValues = new ArrayList<>(getIndividualSchemaValues(schema));

        if (schema instanceof ComposedSchema) {
            ComposedSchema paramSchema = (ComposedSchema)schema;
            List<Schema> paramSchemas = paramSchema.getAnyOf() != null ? paramSchema.getAnyOf() : paramSchema.getOneOf();
            if (paramSchemas != null)
                paramSchemas.forEach(ps -> paramValues.addAll(getSchemaValues(ps)));
        }

        return paramValues;
    }

    private List<String> getIndividualSchemaValues(Schema schema) {
        String paramType = schema.getType();
        List<String> paramEnumValues = schema.getEnum();

        if (BOOLEAN_TYPE.equals(paramType))
            return Arrays.asList("true", "false");
        else if (paramEnumValues != null)
            return paramEnumValues;

        return new ArrayList<>();

    }

    private void getFormDataParameterValues(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry, RequestBody requestBody) {
        MediaType mediaType = requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ?
                requestBody.getContent().get(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) :
                requestBody.getContent().get(MEDIA_TYPE_MULTIPART_FORM_DATA);

        for (Object entry : mediaType.getSchema().getProperties().entrySet()) {
            Schema parameterSchema = ((Entry<String, Schema>) entry).getValue();
            List<String> parameterValuesList = getSchemaValues(parameterSchema);
            if (!parameterValuesList.isEmpty()) { // only if the parameter has enum values or is a boolean
                criteria.add(createCriterion(parameterValuesList, PARAMETER_VALUE,
                        currentPathEntry.getKey() + "->" +
                                currentOperationEntry.getKey().toString() + "->" +
                                parameterSchema.getName()
                ));
            }
        }
    }

    private void getInputContentTypeCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry) {
        RequestBody requestBody = currentOperationEntry.getValue().getRequestBody();
        List<String> contentTypes = requestBody != null ? new ArrayList<>(requestBody.getContent().keySet()) : null;
        if (contentTypes != null) { // there could be no 'requestBody' property, so check it before
            criteria.add(createCriterion(new ArrayList<>(contentTypes), INPUT_CONTENT_TYPE, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
        }
    }

    private void getOutputContentTypeCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry) {
        ApiResponse response = null;
        for(String statusCode : currentOperationEntry.getValue().getResponses().keySet()) {
            if(statusCode.startsWith("2")) {
                response = currentOperationEntry.getValue().getResponses().get(statusCode);
                break;
            }
        }

        List<String> contentTypes = response != null && response.getContent() != null ? new ArrayList<>(response.getContent().keySet()) : null;
        if (contentTypes != null && !contentTypes.isEmpty()) { // there could be no 'apiResponse' or 'content' property, so check it before
            criteria.add(createCriterion(new ArrayList<>(contentTypes), OUTPUT_CONTENT_TYPE, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
        }
    }

    private void getStatusCodeClassCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry) {
        List<String> statusCodeClassesList = new ArrayList<>(); // list of statusCodeClasses per criterion
        statusCodeClassesList.add("2XX"); // it is assumed that all API operations should have a successful response
        for (String statusCodeClass : currentOperationEntry.getValue().getResponses().keySet()) {
            if (statusCodeClass.charAt(0) == '4') {
                statusCodeClassesList.add("4XX"); // add the faulty response case too
                break;
            }
        }
        criteria.add(createCriterion(statusCodeClassesList, STATUS_CODE_CLASS, currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()));
    }

    private void getStatusCodeCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry) {
        criteria.add(createCriterion(
                new ArrayList<>(currentOperationEntry.getValue().getResponses().keySet()), // list of status codes for that operation
                STATUS_CODE,
                currentPathEntry.getKey() + "->" + currentOperationEntry.getKey().toString()
        ));
    }

    private void getResponseBodyPropertiesCoverageCriteria(List<CoverageCriterion> criteria, Entry<String, PathItem> currentPathEntry, Entry<HttpMethod, Operation> currentOperationEntry) {
        // iterate over the responses of that operation
        for (Entry<String, ApiResponse> currentResponseEntry : currentOperationEntry.getValue().getResponses().entrySet()) {

            // iterate over the media type responses of that ApiResponse
            if(currentResponseEntry.getValue().getContent() != null) {
                for (Entry<String, MediaType> currentMediaTypeEntry : currentResponseEntry.getValue().getContent().entrySet()) {
                    Schema mediaTypeSchema = currentMediaTypeEntry.getValue().getSchema();

                    if (mediaTypeSchema != null && currentMediaTypeEntry.getKey().matches(MEDIA_TYPE_APPLICATION_JSON_REGEX)) { // if the response actually returns a body
                        addResponseBodyPropertiesCriterion(mediaTypeSchema, criteria,
                                currentPathEntry.getKey() + "->" +
                                        currentOperationEntry.getKey().toString() + "->" +
                                        currentResponseEntry.getKey() + "->" // note the final arrow, since new elements will be added to the rootPath
                        );
                        break;
                    }
                }
            }

        }
    }

    /**
     * Given an OpenAPI property (either a root response property or a sub-property), if it contains some
     * sub-properties (i.e. it is an object or an array of objects), add a new RESPONSE_BODY_PROPERTIES
     * criterion to the list of criteria passed in as an argument, updating the baseRootPath according
     * to the depth level of the sub-property. This function is to be called recursively, so as to cover
     * all sub-properties of an object.
     *
     * @param mediaTypeSchema OpenAPI property to check if it contains sub-properties to cover
     * @param criteria List of coverage criteria where to include the RESPONSE_BODY_PROPERTIES criteria
     * @param baseRootPath Initial rootPath: "{path}->{httpMethod}->{statusCode}->". Example of
     *                     baseRootPath after 2 iterations: "{path}->{httpMethod}->{statusCode}->{prop1[{prop2"
     */
    private void addResponseBodyPropertiesCriterion(Schema mediaTypeSchema, List<CoverageCriterion> criteria, String baseRootPath) {
        String rootPathSuffix = "";
        String currentResponseRef = null;
        Map<String, Schema> openApiProperties = null;

        if(mediaTypeSchema instanceof ComposedSchema) {
//            addResponseBodyPropertiesCriterion(((ComposedSchema) mediaTypeSchema).getAnyOf().get(0), criteria, baseRootPath);
            // TODO: Handle anyOf, oneOf and allOf
            // TODO: Handle better when type == null, which seems to be with allOf
        } else {
            if (mediaTypeSchema.get$ref() != null) { // the response is an object and its schema is defined in the OpenAPI 'ref' tag
                currentResponseRef = mediaTypeSchema.get$ref();
                rootPathSuffix += "{"; // update rootPathSuffix
            } else if (mediaTypeSchema instanceof ArraySchema && "array".equals(mediaTypeSchema.getType())) { // the response is an array
                if (((ArraySchema)mediaTypeSchema).getItems().get$ref() != null) { // each item of the array has the schema of the OpenAPI 'ref' tag
                    currentResponseRef = ((ArraySchema)mediaTypeSchema).getItems().get$ref();
                    rootPathSuffix += "[{"; // update rootPathSuffix to reflect depth level inside the response body
                }
            } else if (mediaTypeSchema.getProperties() != null && "object".equals(mediaTypeSchema.getType())) { // the response is an object and its schema is defined right after
                openApiProperties = mediaTypeSchema.getProperties();
                rootPathSuffix += "{"; // update rootPathSuffix
            }
            if (currentResponseRef != null) { // if the response body refers to a OpenAPI definition, get properties from that object
                currentResponseRef = currentResponseRef.replace("#/components/schemas/", "");

                if (currentResponseRef.matches("/properties/.*")) {
                    openApiProperties = spec.getSpecification().getComponents().getSchemas().get(currentResponseRef.replaceAll("/properties/.*", "")).getProperties();
                    Matcher matcher = Pattern.compile("/properties/(.*)").matcher(currentResponseRef);
                    for(int i=1; matcher.group(i) != null; i++) {
                        openApiProperties = openApiProperties.get(matcher.group(i)).getProperties();
                    }
                } else {
                    Schema propertiesSchema = spec.getSpecification().getComponents().getSchemas().get(currentResponseRef);
                    if (propertiesSchema != null) openApiProperties = propertiesSchema.getProperties();
                }
            }

            if (openApiProperties != null) { // if there are properties to cover in this iteration, add new criterion
                baseRootPath += rootPathSuffix; // update rootPath with the suffix, since a new criterion will be added
                criteria.add(createCriterion(new ArrayList<>(openApiProperties.keySet()), RESPONSE_BODY_PROPERTIES, baseRootPath));
                for (Entry<String, Schema> openApiProperty: openApiProperties.entrySet()) { // Recursively add criteria for each property
                    addResponseBodyPropertiesCriterion(openApiProperty.getValue(), criteria, baseRootPath+openApiProperty.getKey()); // update rootPath with the name of the property
                }
            }
        }
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