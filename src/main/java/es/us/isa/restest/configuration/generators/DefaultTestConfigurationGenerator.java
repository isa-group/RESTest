package es.us.isa.restest.configuration.generators;

import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonProcessingException;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.Auth;
import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfiguration;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TestConfiguration objects are key in RESTest. They include all the
 * information required to test an API (data dictionaries, authentication data,
 * etc), complementing the information provided by the API specification. This
 * class manage the generation of a default test configuration file from an
 * OpenAPI specification. Default test configuration files are currently pretty
 * simply and should be manually updated before testing (ex. adding specific
 * data dictionaries).
 */

public class DefaultTestConfigurationGenerator {

	public static final String RANDOM_REG_EXP = "RandomRegExp";
	public static final String RANDOM_DATE = "RandomDate";
	public static final String RANDOM_ENGLISH_WORD = "RandomEnglishWord";
	public static final String RANDOM_NUMBER = "RandomNumber";
	public static final String RANDOM_BOOLEAN = "RandomBoolean";
	public static final String RANDOM_INPUT_VALUE = "RandomInputValue";
	public static final String OBJECT_PERTURBATOR = "ObjectPerturbator";

	public static final String GEN_PARAM_VALUES = "values";
	public static final String GEN_PARAM_STRING_OBJECTS = "stringObjects";
	public static final String GEN_PARAM_REG_EXP = "regExp";
	public static final String GEN_PARAM_MAX_WORDS = "maxWords";
	public static final String GEN_PARAM_FORMAT = "format";
	public static final String GEN_PARAM_MIN_LENGTH = "minLength";
	public static final String GEN_PARAM_MAX_LENGTH = "maxLength";
	public static final String GEN_PARAM_TYPE = "type";
	public static final String GEN_PARAM_MIN = "min";
	public static final String GEN_PARAM_MAX = "max";

	public static final String MEDIA_TYPE_APPLICATION_JSON = "application/json";
	public static final String MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
	public static final String MEDIA_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";

	// ARTE
	public static final String SEMANTIC_PARAMETER = "SemanticParameter";
	public static final String PREDICATES = "predicates";
	public static final String NUMBER_OF_TRIES_TO_GENERATE_REGEX = "numberOfTriesToGenerateRegex";

	private static final Logger logger = LogManager.getLogger(DefaultTestConfigurationGenerator.class);

	private OpenAPISpecification spec;

	public DefaultTestConfigurationGenerator(OpenAPISpecification spec) {
		this.spec = spec;
	}

	/**
	 * Generate a default test configuration file for a given Open API specification
	 * 
	 * @param destination Path of the output test configuration file
	 * @return a test configuration object
	 */
	public TestConfigurationObject generate(String destination) {
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		return this.generate(destination, Collections.singletonList(filter));
	}

	/**
	 * Generate a default test configuration file for a given Open API specification
	 * 
	 * @param destination Path of the output test configuration file
	 * @param filters     Set the paths and HTTP methods to be included in the test
	 *                    configuration file, i.e. those that will be tested
	 * @return a test configuration object
	 */
	public TestConfigurationObject generate(String destination, Collection<TestConfigurationFilter> filters) {

		TestConfigurationObject conf = new TestConfigurationObject();

		// Authentication configuration (not required by default)
		conf.setAuth(generateDefaultAuthentication());
		// TODO: Read authentication settings from specification
		// (https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securitySchemeObject)

		// Operations
		TestConfiguration testConf = new TestConfiguration();
		testConf.setOperations(generateOperations(filters));

		conf.setTestConfiguration(testConf);

		// Write configuration to file
		TestConfigurationIO.toFile(conf, destination);

		return conf;
	}

	// Generate the test configuration data for operations
	private List<Operation> generateOperations(Collection<TestConfigurationFilter> filters) {

		List<Operation> operations = new ArrayList<>();

		for (TestConfigurationFilter filter : filters) {
			Paths paths = spec.getSpecification().getPaths();
			for (Entry<String, PathItem> path : paths.entrySet())
				if (filter.getPath() == null || path.getKey().equalsIgnoreCase(filter.getPath()))
					operations.addAll(generateOperationsOfPath(path, filter.getMethods())); // For every filter, add its
																							// path to testConf
		}
		return operations;
	}

	// Generate the test configuration data for the operations of a specific input
	// path
	private List<Operation> generateOperationsOfPath(Entry<String, PathItem> path, Collection<HttpMethod> methods) {

		List<Operation> pathOperations = new ArrayList<>();

		for (Entry<HttpMethod, io.swagger.v3.oas.models.Operation> operationEntry : path.getValue().readOperationsMap()
				.entrySet())
			if (methods.contains(operationEntry.getKey())) // Generate only filtered methods
				pathOperations.add(generateOperation(operationEntry, path.getKey()));

		return pathOperations;
	}

	// Generate test configuration data for a GET operation
	private Operation generateOperation(Entry<HttpMethod, io.swagger.v3.oas.models.Operation> operationEntry,
			String path) {
		Operation testOperation = new Operation();
		testOperation.setTestPath(path);

		// Set operation id (if defined)
		if (operationEntry.getValue().getOperationId() != null)
			testOperation.setOperationId(operationEntry.getValue().getOperationId());
		else
			testOperation.setOperationId("<SET OPERATION ID>");

		// Set HTTP method
		testOperation.setMethod(operationEntry.getKey().name().toLowerCase());

		// Set parameters
		if (operationEntry.getValue().getParameters() != null) {
			testOperation.setTestParameters(generateTestParameters(operationEntry.getValue().getParameters()));
		}

		// Set request body parameters
		if (operationEntry.getValue().getRequestBody() != null && testOperation.getTestParameters() == null) {
			testOperation
					.setTestParameters(generateRequestBodyTestParameters(operationEntry.getValue().getRequestBody()));
		} else if (operationEntry.getValue().getRequestBody() != null) {
			testOperation.getTestParameters()
					.addAll(generateRequestBodyTestParameters(operationEntry.getValue().getRequestBody()));
		}

		// Set expected output
		testOperation.setExpectedResponse("200");

		return testOperation;
	}

	// Generate test configuration data for input parameters
	private List<TestParameter> generateTestParameters(List<Parameter> parameters) {
		List<TestParameter> testParameters = new ArrayList<>();
		for (Parameter param : parameters) {
			Schema schema = param.getSchema();

			// If it's a path or query parameter, get type to set a useful generator
			if ((param.getIn().equals("query") || param.getIn().equals("path") || param.getIn().equals("header"))
					&& "object".equals(schema.getType())) {
				testParameters.addAll(generateObjectParameters(param.getSchema(), param.getName(), param.getIn(),
						param.getRequired(), param.getStyle(), null, param.getExplode()));
			} else {

				TestParameter testParam = new TestParameter();
				testParam.setName(param.getName());
				testParam.setIn(param.getIn());

				// Set default weight for optional parameters
				if (param.getRequired() == null || !param.getRequired())
					testParam.setWeight(0.5f);

				// Set generator for the parameter
				Generator gen = new Generator();
				gen.setGenParameters(new ArrayList<>());

				if (param.getIn().equals("query") || param.getIn().equals("path") || param.getIn().equals("header")) {
					generateGenerator(gen, schema);

				} else {
					setDefaultGenerator(gen);
				}

				List<Generator> gens = new ArrayList<>();
				gens.add(gen);
				testParam.setGenerators(gens);
				testParameters.add(testParam);
			}
		}

		return testParameters;
	}

	private List<TestParameter> generateObjectParameters(Schema schema, String objectName, String in, Boolean required,
			Parameter.StyleEnum styleParam, Encoding.StyleEnum styleFormData, Boolean explode) {
		List<TestParameter> propertyParams = new ArrayList<>();

		if (schema.getProperties() != null) {
			for (Object o : schema.getProperties().entrySet()) {
				Map.Entry<String, Schema> property = (Map.Entry<String, Schema>) o;

				TestParameter propertyParam = new TestParameter();
				if ((styleParam != null && styleParam.equals(Parameter.StyleEnum.DEEPOBJECT))
						|| (styleFormData != null && styleFormData.equals(Encoding.StyleEnum.DEEP_OBJECT))) {
					propertyParam.setName(objectName + "[" + property.getKey() + "]");
				} else {
					propertyParam.setName(property.getKey());
				}

				if (required == null || !required || schema.getRequired() == null
						|| !schema.getRequired().contains(property.getKey()))
					propertyParam.setWeight(0.5f);

				propertyParam.setIn(in);

				Generator propertyGen = new Generator();
				propertyGen.setGenParameters(new ArrayList<>());

				generateGenerator(propertyGen, property.getValue());
				List<Generator> gens = new ArrayList<>();
				gens.add(propertyGen);
				propertyParam.setGenerators(gens);
				propertyParams.add(propertyParam);
			}
		}

		return propertyParams;
	}

	private void generateGenerator(Generator gen, Schema schema) {
		String paramType = schema.getType();
		List<String> paramEnumValues = schema.getEnum();

		// If it's a composed schema, we can't handle it. Set default generator
		if (paramType == null) {
			setDefaultGenerator(gen);
			return;
		}

		// If the param type is array, get its item type
		if ("array".equals(paramType)) {
			paramType = ((ArraySchema) schema).getItems().getType();
		}

		// If the param is enum, set generator to input value iterator with the values
		// defined in the enum
		if (paramEnumValues != null) {
			paramType = "enum";
		}

		List<GenParameter> genParams = new ArrayList<>();
		GenParameter genParam1 = new GenParameter();

		switch (paramType) {
		case "string":
			generateStringGenerator(gen, schema.getFormat(), schema.getPattern(), schema.getMinLength(),
					schema.getMaxLength());
			break;
		case "number":
		case "integer":
			generateNumberGenerator(gen, schema.getFormat(), schema.getMinimum(), schema.getMaximum(),
					schema.getExclusiveMinimum(), schema.getExclusiveMaximum());
			break;
		case "boolean":
			gen.setType(RANDOM_BOOLEAN); // Random number generator
			gen.setGenParameters(genParams);
			break;
		case "enum":
			gen.setType(RANDOM_INPUT_VALUE);
			genParam1.setName(GEN_PARAM_VALUES);
			genParam1.setValues(paramEnumValues);
			genParams.add(genParam1);
			gen.setGenParameters(genParams);
			break;
		default:
			throw new IllegalArgumentException("The parameter type " + paramType + " is not allowed in query or path");
		}
	}

	private void generateStringGenerator(Generator gen, String format, String pattern, Integer minLength,
			Integer maxLength) {
		GenParameter genParam1 = new GenParameter();
		if (pattern != null) {
			gen.setType(RANDOM_REG_EXP);
			genParam1.setName(GEN_PARAM_REG_EXP);
			genParam1.setValues(Collections.singletonList(pattern));
			gen.getGenParameters().add(genParam1);
		} else if (format == null) {
			gen.setType(RANDOM_ENGLISH_WORD);
			genParam1.setName(GEN_PARAM_MAX_WORDS);
			genParam1.setValues(Collections.singletonList("1"));
			gen.getGenParameters().add(genParam1);
		} else {
			switch (format) {
			case "date":
				gen.setType(RANDOM_DATE);
				genParam1.setName(GEN_PARAM_FORMAT);
				genParam1.setValues(Collections.singletonList("yyyy-MM-dd"));
				gen.getGenParameters().add(genParam1);
				break;
			case "date-time":
				gen.setType(RANDOM_DATE);
				genParam1.setName(GEN_PARAM_FORMAT);
				genParam1.setValues(Collections.singletonList("yyyy-MM-dd'T'HH:mm:ss'Z'"));
				gen.getGenParameters().add(genParam1);
				break;
			case "email":
				gen.setType(RANDOM_REG_EXP);
				genParam1.setName(GEN_PARAM_REG_EXP);
				genParam1.setValues(Collections.singletonList(
						"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"));
				gen.getGenParameters().add(genParam1);
				break;
			case "uri":
			case "url":
				gen.setType(RANDOM_REG_EXP);
				genParam1.setName(GEN_PARAM_REG_EXP);
				genParam1.setValues(Collections.singletonList(
						"https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"));
				gen.getGenParameters().add(genParam1);
				break;
			case "binary":
			case "byte":
				gen.setType(RANDOM_INPUT_VALUE);
				genParam1.setName(GEN_PARAM_VALUES);
				genParam1.setValues(Arrays.asList("path/to/file", "path/to/another/file"));
				gen.getGenParameters().add(genParam1);
				break;
			default:
				gen.setType(RANDOM_ENGLISH_WORD);
				genParam1.setName(GEN_PARAM_MAX_WORDS);
				genParam1.setValues(Collections.singletonList("1"));
				gen.getGenParameters().add(genParam1);
			}
		}

		if (!gen.getType().equals(RANDOM_ENGLISH_WORD) && minLength != null) {
			GenParameter minLengthGenParam = new GenParameter();
			minLengthGenParam.setName(GEN_PARAM_MIN_LENGTH);
			minLengthGenParam.setValues(Collections.singletonList(minLength.toString()));
			gen.getGenParameters().add(minLengthGenParam);
		}

		if (!gen.getType().equals(RANDOM_ENGLISH_WORD) && maxLength != null) {
			GenParameter maxLengthGenParam = new GenParameter();
			maxLengthGenParam.setName(GEN_PARAM_MAX_LENGTH);
			maxLengthGenParam.setValues(Collections.singletonList(maxLength.toString()));
			gen.getGenParameters().add(maxLengthGenParam);
		}

	}

	private void generateNumberGenerator(Generator gen, String format, BigDecimal minimum, BigDecimal maximum,
			Boolean exclusiveMinimum, Boolean exclusiveMaximum) {
		GenParameter type = new GenParameter();
		GenParameter min = new GenParameter();
		GenParameter max = new GenParameter();

		gen.setType(RANDOM_NUMBER);

		type.setName(GEN_PARAM_TYPE);
		type.setValues(Collections.singletonList((format == null ? "integer" : format)));
		gen.getGenParameters().add(type);

		min.setName(GEN_PARAM_MIN);
		if (minimum != null && exclusiveMinimum != null && exclusiveMinimum) {
			min.setValues(Collections.singletonList(minimum.add(new BigDecimal(1)).toString()));
		} else if (minimum != null) {
			min.setValues(Collections.singletonList(minimum.toString()));
		} else {
			min.setValues(Collections.singletonList("1"));
		}
		gen.getGenParameters().add(min);

		max.setName(GEN_PARAM_MAX);
		if (maximum != null && exclusiveMaximum != null && exclusiveMaximum) {
			max.setValues(Collections.singletonList(maximum.add(new BigDecimal(-1)).toString()));
		} else if (maximum != null) {
			max.setValues(Collections.singletonList(maximum.toString()));
		} else {
			max.setValues(Collections.singletonList("100"));
		}
		gen.getGenParameters().add(max);
	}

	private List<TestParameter> generateRequestBodyTestParameters(RequestBody requestBody) {
		// TODO: set smarter generators for body parameters (and maybe others like
		// headers or form-data)
		List<TestParameter> testParameters = new ArrayList<>();

		if (requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_JSON)) {

			TestParameter testParam = new TestParameter();
			testParam.setName("body");
			testParam.setIn("body");

			if (requestBody.getRequired() != null && !requestBody.getRequired()) {
				testParam.setWeight(0.5f);
			}

			Generator gen = new Generator();
			gen.setGenParameters(new ArrayList<>());
			generateBodyGenerator(gen, requestBody.getContent().get(MEDIA_TYPE_APPLICATION_JSON));
			List<Generator> gens = new ArrayList<>();
			gens.add(gen);
			testParam.setGenerators(gens);
			testParameters.add(testParam);

		} else if (requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED)
				|| requestBody.getContent().containsKey(MEDIA_TYPE_MULTIPART_FORM_DATA)) {

			MediaType mediaType = requestBody.getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED)
					? requestBody.getContent().get(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED)
					: requestBody.getContent().get(MEDIA_TYPE_MULTIPART_FORM_DATA);

			for (Object entry : mediaType.getSchema().getProperties().entrySet()) {
				Schema parameterSchema = ((Entry<String, Schema>) entry).getValue();
				String parameterName = ((Entry<String, Schema>) entry).getKey();

				if ("object".equals(parameterSchema.getType())) {
					Encoding encoding = mediaType.getEncoding() != null ? mediaType.getEncoding().get(parameterName)
							: null;

					if (encoding == null) {
						testParameters
								.addAll(generateObjectParameters(parameterSchema, parameterName, "formData",
										parameterSchema.getRequired() != null
												&& parameterSchema.getRequired().contains(parameterName),
										null, null, null));
					} else {
						testParameters.addAll(generateObjectParameters(parameterSchema, parameterName, "formData",
								parameterSchema.getRequired() != null
										&& parameterSchema.getRequired().contains(parameterName),
								null, encoding.getStyle(), encoding.getExplode()));
					}

				} else {

					TestParameter testParam = new TestParameter();

					Encoding encoding = null;
					if (mediaType.getEncoding() != null) {
						encoding = mediaType.getEncoding().get(parameterName);
					}

					if ("array".equals(parameterSchema.getType()) && encoding != null
							&& encoding.getStyle().equals(Encoding.StyleEnum.DEEP_OBJECT)) {
						testParam.setName(parameterName + "[]");
					} else {
						testParam.setName(parameterName);
					}

					testParam.setIn("formData");

					if (mediaType.getSchema().getRequired() == null
							|| !mediaType.getSchema().getRequired().contains(parameterSchema.getName())) {
						testParam.setWeight(0.5f);
					}

					Generator gen = new Generator();
					gen.setGenParameters(new ArrayList<>());
					generateGenerator(gen, parameterSchema);
					List<Generator> gens = new ArrayList<>();
					gens.add(gen);
					testParam.setGenerators(gens);
					testParameters.add(testParam);
				}
			}
		}

		return testParameters;
	}

	private void generateBodyGenerator(Generator gen, MediaType mediaType) {
		GenParameter stringObject = new GenParameter();

		String bodyParam = null;
		ObjectMapper objectMapper = new ObjectMapper();

		// Try to get an example body from the Swagger specification
		// Look for 'examples' field in the parameter object
		if (mediaType.getExamples() != null) {
			try {
				bodyParam = objectMapper
						.writeValueAsString(mediaType.getExamples().entrySet().iterator().next().getValue());
			} catch (JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		// Look for 'example' field in the schema object
		else if (mediaType.getSchema().getExample() != null) {
			try {
				bodyParam = objectMapper.writeValueAsString(mediaType.getSchema().getExample());
			} catch (JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		// Look for 'example' field in the schema object among Swagger definitions
		else if (mediaType.getSchema().get$ref() != null) {
			String bodyReference = mediaType.getSchema().get$ref();
			try {
				bodyParam = objectMapper.writeValueAsString(spec.getSpecification().getComponents().getSchemas()
						.get(bodyReference.replace("#/components/schemas/", "")).getExample());
			} catch (JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		if (bodyParam != null && !bodyParam.equals("null")) {
			gen.setType(OBJECT_PERTURBATOR);
			stringObject.setName(GEN_PARAM_STRING_OBJECTS);
			stringObject.setValues(Collections.singletonList(bodyParam));
			gen.getGenParameters().add(stringObject);
		} else {
			setDefaultGenerator(gen);
		}
	}

	// Default generator when no smarter one can be found for a given parameter
	private void setDefaultGenerator(Generator gen) {
		List<GenParameter> genParams = new ArrayList<>();
		GenParameter genParam1 = new GenParameter();
		List<String> genParam1Values = new ArrayList<>();

		gen.setType(RANDOM_INPUT_VALUE);
		genParam1.setName(GEN_PARAM_VALUES);
		genParam1Values.add("value 1");
		genParam1Values.add("value 2");
		genParam1.setValues(genParam1Values);
		genParams.add(genParam1);
		gen.setGenParameters(genParams);
	}

	// Default authentication setting (required = false)
	private Auth generateDefaultAuthentication() {
		Auth auth = new Auth();
		auth.setRequired(true);
		auth.setHeaderParams(new HashMap<>());
		auth.setQueryParams(new HashMap<>());
		return auth;
	}

}
