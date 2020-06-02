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
import es.us.isa.restest.configuration.pojos.TestPath;
import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.models.HttpMethod;
import io.swagger.models.Path;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class to manage the generation of test configuration files from an OpenAPI specification.
 * A testConf file is used to generate better test cases, for example by specifying the necessary
 * authentication data (like an API key) or by providing values to a specific parameter.
 * Automatically generated testConfs are pretty simple, the developer should make an effort to
 * augment it.
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
	public static final String GEN_PARAM_STRING_OBJECT = "stringObject";
	public static final String GEN_PARAM_REG_EXP = "regExp";
	public static final String GEN_PARAM_MAX_WORDS = "maxWords";
	public static final String GEN_PARAM_FORMAT = "format";
	public static final String GEN_PARAM_MIN_LENGTH = "minLength";
	public static final String GEN_PARAM_MAX_LENGTH = "maxLength";
	public static final String GEN_PARAM_TYPE = "type";
	public static final String GEN_PARAM_MIN = "min";
	public static final String GEN_PARAM_MAX = "max";

	private static final Logger logger = LogManager.getLogger(DefaultTestConfigurationGenerator.class);

	private OpenAPISpecification spec;

	public DefaultTestConfigurationGenerator(OpenAPISpecification spec) {
		this.spec = spec;
	}

	/**
	 * Generate a default test configuration file for a given Open API specification
	 * @param destination Path of the output test configuration file
	 * @return
	 */
	public TestConfigurationObject generate (String destination) {
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath(null);
		filter.addAllMethods();
		return this.generate(destination, Collections.singletonList(filter));
	}

	/**
	 * Generate a default test configuration file for a given Open API specification
	 * @param destination Path of the output test configuration file
	 * @param filters Set the paths and HTTP methods to be included in the test configuration file,
	 *                i.e. those that will be tested
	 * @return
	 */
	public TestConfigurationObject generate (String destination, Collection<TestConfigurationFilter> filters) {
		
		TestConfigurationObject conf = new TestConfigurationObject();
		
		// Authentication configuration (not required by default)
		conf.setAuth(generateDefaultAuthentication());
		// TODO: Read authentication settings from specification (https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securitySchemeObject)
		
		// Paths
		TestConfiguration testConf = new TestConfiguration();
		testConf.setTestPaths(generatePaths(filters));
			
		conf.setTestConfiguration(testConf);
		
		// Write configuration to file
		TestConfigurationIO.toFile(conf, destination);
		
		return conf;
	}

	// Generate the test configuration data for paths
	private List<TestPath> generatePaths(Collection<TestConfigurationFilter> filters) {
		
		List<TestPath> confPaths = new ArrayList<>();

		for (TestConfigurationFilter filter: filters) {
			Map<String,Path> paths = spec.getSpecification().getPaths();
			for(Entry<String,Path> path: paths.entrySet())
				if (filter.getPath()==null || path.getKey().equalsIgnoreCase(filter.getPath()))
					confPaths.add(generatePath(path,filter.getMethods())); // For every filter, add its path to testConf
		}
		return confPaths;
	}

	// Generate the test configuration data for a specific input path
	private TestPath generatePath(Entry<String, Path> path, Collection<HttpMethod> methods) {
		
		TestPath confPath = new TestPath();
		confPath.setTestPath(path.getKey());
		
		List<Operation> testOperations = new ArrayList<>();
		
		for (Entry<HttpMethod, io.swagger.models.Operation> operationEntry : path.getValue().getOperationMap().entrySet())
			if (methods.contains(operationEntry.getKey())) // Generate only filtered methods
				testOperations.add(generateOperation(operationEntry));
		
		confPath.setOperations(testOperations);
		
		return confPath;
	}

	// Generate test configuration data for a GET operation
	private Operation generateOperation(Entry<HttpMethod,io.swagger.models.Operation> operationEntry) {
		Operation testOperation = new Operation();
		
		// Set operation id (if defined)
		if (operationEntry.getValue().getOperationId()!=null)
			testOperation.setOperationId(operationEntry.getValue().getOperationId());
		else
			testOperation.setOperationId("<SET OPERATION ID>");
		
		// Set HTTP method
		testOperation.setMethod(operationEntry.getKey().name().toLowerCase());
		
		// Set parameters
		testOperation.setTestParameters(generateTestParameters(operationEntry.getValue().getParameters()));

		// Set expected output
		testOperation.setExpectedResponse("200");
		
		return testOperation;
	}

	// Generate test configuration data for input parameters
	private List<TestParameter> generateTestParameters(List<Parameter> parameters) {
		
		List<TestParameter> testParameters = new ArrayList<>();
		for(Parameter param: parameters) {
			TestParameter testParam = new TestParameter();
			testParam.setName(param.getName());
			
			// Set default weight for optional parameters
			if (!param.getRequired())
				testParam.setWeight(0.5f);

			// Set generator for the parameter
			Generator gen = new Generator();
			gen.setGenParameters(new ArrayList<>());
			List<GenParameter> genParams = new ArrayList<>();
			GenParameter genParam1 = new GenParameter();
			List<String> genParam1Values = new ArrayList<>();

			// If it's a path or query parameter, get type to set a useful generator
			if (param.getIn().equals("query") || param.getIn().equals("path") || param.getIn().equals("header") || param.getIn().equals("formData")) {
				String paramType = ((AbstractSerializableParameter) param).getType();
				List<String> paramEnumValues = ((AbstractSerializableParameter) param).getEnum();

				// If the param type is array, get its item type
				if (paramType.equals("array")) {
					paramType = ((AbstractSerializableParameter) param).getItems().getType();
				}

				// If the param is enum, set generator to input value iterator with the values defined in the enum
				if (paramEnumValues != null) {
					paramType = "enum";
				}

				switch (paramType) {
					case "string":
						generateStringGenerator(gen, ((AbstractSerializableParameter) param).getFormat(), param.getPattern(), ((AbstractSerializableParameter) param).getMinLength(), ((AbstractSerializableParameter) param).getMaxLength());
						break;
					case "number":
					case "integer":
						generateNumberGenerator(gen, ((AbstractSerializableParameter) param).getFormat(), ((AbstractSerializableParameter) param).getMinimum(), ((AbstractSerializableParameter) param).getMaximum(), ((AbstractSerializableParameter) param).isExclusiveMinimum(), ((AbstractSerializableParameter) param).isExclusiveMaximum());
						break;
					case "boolean":
						gen.setType(RANDOM_BOOLEAN);         // Random number generator
						gen.setGenParameters(genParams);
						break;
					case "enum":
						gen.setType(RANDOM_INPUT_VALUE);
						genParam1.setName(GEN_PARAM_VALUES);
						genParam1.setValues(paramEnumValues);
						genParams.add(genParam1);
						gen.setGenParameters(genParams);
						break;
					case "file":
						gen.setType(RANDOM_INPUT_VALUE);
						genParam1.setName(GEN_PARAM_VALUES);
						genParam1Values.add("path/to/file");
						genParam1Values.add("path/to/another/file");
						genParam1.setValues(genParam1Values);
						genParams.add(genParam1);
						gen.setGenParameters(genParams);
						break;
					default:
						throw new IllegalArgumentException("The parameter type " + paramType + " is not allowed in query or path");
				}
			}
			// TODO: set smarter generators for body parameters (and maybe others like headers or form-data)
			else if (param.getIn().equals("body")) {
				generateBodyGenerator(gen, param);
			}
			else {
				setDefaultGenerator(gen);
			}

			testParam.setGenerator(gen);
			testParameters.add(testParam);
		}
		
		return testParameters;
	}

	private void generateStringGenerator(Generator gen, String format, String pattern, Integer minLength, Integer maxLength) {
		GenParameter genParam1 = new GenParameter();
		if(pattern != null) {
			gen.setType(RANDOM_REG_EXP);
			genParam1.setName(GEN_PARAM_REG_EXP);
			genParam1.setValues(Collections.singletonList(pattern));
			gen.getGenParameters().add(genParam1);
		} else if(format == null) {
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
					genParam1.setValues(Collections.singletonList("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"));
					gen.getGenParameters().add(genParam1);
					break;
				case "uri":
				case "url":
					gen.setType(RANDOM_REG_EXP);
					genParam1.setName(GEN_PARAM_REG_EXP);
					genParam1.setValues(Collections.singletonList("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"));
					gen.getGenParameters().add(genParam1);
					break;
				default:
					gen.setType(RANDOM_ENGLISH_WORD);
					genParam1.setName(GEN_PARAM_MAX_WORDS);
					genParam1.setValues(Collections.singletonList("1"));
					gen.getGenParameters().add(genParam1);
			}
		}

		if(!gen.getType().equals(RANDOM_ENGLISH_WORD) && minLength != null) {
			GenParameter minLengthGenParam = new GenParameter();
			minLengthGenParam.setName(GEN_PARAM_MIN_LENGTH);
			minLengthGenParam.setValues(Collections.singletonList(minLength.toString()));
			gen.getGenParameters().add(minLengthGenParam);
		}

		if(!gen.getType().equals(RANDOM_ENGLISH_WORD) && maxLength != null) {
			GenParameter maxLengthGenParam = new GenParameter();
			maxLengthGenParam.setName(GEN_PARAM_MAX_LENGTH);
			maxLengthGenParam.setValues(Collections.singletonList(maxLength.toString()));
			gen.getGenParameters().add(maxLengthGenParam);
		}

	}

	private void generateNumberGenerator(Generator gen, String format, BigDecimal minimum, BigDecimal maximum, Boolean exclusiveMinimum, Boolean exclusiveMaximum) {
		GenParameter type = new GenParameter();
		GenParameter min = new GenParameter();
		GenParameter max = new GenParameter();

		gen.setType(RANDOM_NUMBER);

		type.setName(GEN_PARAM_TYPE);
		type.setValues(Collections.singletonList((format == null? "integer" : format)));
		gen.getGenParameters().add(type);

		min.setName(GEN_PARAM_MIN);
		if(minimum != null && exclusiveMinimum != null && exclusiveMinimum) {
			min.setValues(Collections.singletonList(minimum.add(new BigDecimal(1)).toString()));
		} else if(minimum != null) {
			min.setValues(Collections.singletonList(minimum.toString()));
		} else {
			min.setValues(Collections.singletonList("1"));
		}
		gen.getGenParameters().add(min);


		max.setName(GEN_PARAM_MAX);
		if(maximum != null && exclusiveMaximum != null && exclusiveMaximum) {
			max.setValues(Collections.singletonList(maximum.add(new BigDecimal(-1)).toString()));
		} else if(maximum != null) {
			max.setValues(Collections.singletonList(maximum.toString()));
		} else {
			max.setValues(Collections.singletonList("100"));
		}
		gen.getGenParameters().add(max);
	}

	private void generateBodyGenerator(Generator gen, Parameter param) {
		GenParameter stringObject = new GenParameter();

		String bodyParam = null;
		ObjectMapper objectMapper = new ObjectMapper();

		// Try to get an example body from the Swagger specification
		// Look for 'examples' field in the parameter object
		if (((BodyParameter) param).getExamples() != null) {
			try {
				bodyParam = objectMapper.writeValueAsString(((BodyParameter) param).getExamples().entrySet().iterator().next().getValue());
			} catch (JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		// Look for 'example' field in the schema object
		else if (((BodyParameter) param).getSchema().getExample() != null) {
			try {
				bodyParam = objectMapper.writeValueAsString(((BodyParameter) param).getSchema().getExample());
			} catch (JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		// Look for 'example' field in the schema object among Swagger definitions
		else if (((BodyParameter) param).getSchema().getReference() != null) {
			String bodyReference = ((BodyParameter) param).getSchema().getReference();
			try {
				bodyParam = objectMapper.writeValueAsString(spec.getSpecification().getDefinitions().get(bodyReference.replaceAll("#/definitions/", "")).getExample());
			} catch (JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
		}

		if (bodyParam != null && !bodyParam.equals("null")) {
			gen.setType(OBJECT_PERTURBATOR);
			stringObject.setName(GEN_PARAM_STRING_OBJECT);
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
		auth.setHeaderParams(new ArrayList<>());
		auth.setQueryParams(new ArrayList<>());
		return auth;
	}
	
}
