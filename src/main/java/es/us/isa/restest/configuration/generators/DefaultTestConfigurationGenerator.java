package es.us.isa.restest.configuration.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonProcessingException;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.Auth;
import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.HeaderParam;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.QueryParam;
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

/**
 * Class to manage the generation of test configuration files from an OpenAPI specification.
 * A testConf file is used to generate better test cases, for example by specifying the necessary
 * authentication data (like an API key) or by providing values to a specific parameter.
 * Automatically generated testConfs are pretty simple, the developer should make an effort to
 * augment it.
 */
public class DefaultTestConfigurationGenerator {

	private OpenAPISpecification spec;

	public DefaultTestConfigurationGenerator(OpenAPISpecification spec) {
		this.spec = spec;
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
		
		List<TestPath> confPaths = new ArrayList<TestPath>();
		
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
		
		List<Operation> testOperations = new ArrayList<Operation>();
		
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
			List<GenParameter> genParams = new ArrayList<>();
			GenParameter genParam1 = new GenParameter();
			GenParameter genParam2 = new GenParameter();
			GenParameter genParam3 = new GenParameter();
			List<String> genParam1Values = new ArrayList<>();
			List<String> genParam2Values = new ArrayList<>();
			List<String> genParam3Values = new ArrayList<>();

			// If it's a path or query parameter, get type to set a useful generator
			if (param.getIn() == "query" || param.getIn() == "path" || param.getIn() == "header" || param.getIn() == "formData") {
				String paramType = ((AbstractSerializableParameter) param).getType();
				List<String> paramEnumValues = ((AbstractSerializableParameter) param).getEnum();

				// If the param type is array, get its item type
				if (paramType == "array") {
					paramType = ((AbstractSerializableParameter) param).getItems().getType();
				}

				// If the param is enum, set generator to input value iterator with the values defined in the enum
				if (paramEnumValues != null) {
					paramType = "enum";
				}

				switch (paramType) {
					case "string":
						gen.setType("RandomEnglishWord");     // English words generator
						genParam1.setName("maxWords");        // maxWords generator parameter
						genParam1Values.add("1");
						genParam1.setValues(genParam1Values);
						genParams.add(genParam1);
						gen.setGenParameters(genParams);
						break;
					case "number":
					case "integer":
						gen.setType("RandomNumber");          // Random number generator
						genParam1.setName("type");            // Type parameter
						genParam1Values.add("integer");       // Integer works for integers and floats
						genParam1.setValues(genParam1Values);
						genParams.add(genParam1);
						genParam2.setName("min");             // Min parameter
						genParam2Values.add("1");
						genParam2.setValues(genParam2Values);
						genParams.add(genParam2);
						genParam3.setName("max");             // Max parameter
						genParam3Values.add("100");
						genParam3.setValues(genParam3Values);
						genParams.add(genParam3);
						gen.setGenParameters(genParams);
						break;
					case "boolean":
						gen.setType("RandomBoolean");         // Random number generator
						gen.setGenParameters(genParams);
						break;
					case "enum":
						gen.setType("RandomInputValue");
						genParam1.setName("values");
						genParam1.setValues(paramEnumValues);
						genParams.add(genParam1);
						gen.setGenParameters(genParams);
						break;
					case "file":
						gen.setType("RandomInputValue");
						genParam1.setName("values");
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
			else if (param.getIn() == "body") {
				String bodyParam = null;
				ObjectMapper objectMapper = new ObjectMapper();

				// Try to get an example body from the Swagger specification
				// Look for 'examples' field in the parameter object
				if (((BodyParameter) param).getExamples() != null) {
					try {
						bodyParam = objectMapper.writeValueAsString(((BodyParameter) param).getExamples().entrySet().iterator().next().getValue());
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				}

				// Look for 'example' field in the schema object
				else if (((BodyParameter) param).getSchema().getExample() != null) {
					try {
						bodyParam = objectMapper.writeValueAsString(((BodyParameter) param).getSchema().getExample());
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				}

				// Look for 'example' field in the schema object among Swagger definitions
				else if (((BodyParameter) param).getSchema().getReference() != null) {
					String bodyReference = ((BodyParameter) param).getSchema().getReference();
					try {
						bodyParam = objectMapper.writeValueAsString(spec.getSpecification().getDefinitions().get(bodyReference.replaceAll("#/definitions/", "")).getExample());
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				}

				if (bodyParam != null && !bodyParam.equals("null")) {
//					gen.setType("RandomInputValue");
//					genParam1.setName("values");
//					genParam1Values.add(bodyParam);
//					genParam1.setValues(genParam1Values);
//					genParams.add(genParam1);
//					gen.setGenParameters(genParams);

					// Replaced RandomInputValue with ObjectPerturbator:
					gen.setType("ObjectPerturbator");
					genParam1.setName("stringObject");
					genParam1Values.add(bodyParam);
					genParam1.setValues(genParam1Values);
					genParams.add(genParam1);
					gen.setGenParameters(genParams);
				} else {
					setDefaultGenerator(gen);
				}
			}
			else {
				setDefaultGenerator(gen);
			}

			testParam.setGenerator(gen);
			testParameters.add(testParam);
		}
		
		return testParameters;
	}

	// Default generator when no smarter one can be found for a given parameter
	private void setDefaultGenerator(Generator gen) {
		List<GenParameter> genParams = new ArrayList<>();
		GenParameter genParam1 = new GenParameter();
		List<String> genParam1Values = new ArrayList<>();

		gen.setType("RandomInputValue");
		genParam1.setName("values");
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
		auth.setHeaderParams(new ArrayList<HeaderParam>());
		auth.setQueryParams(new ArrayList<QueryParam>());
		return auth;
	}
	
}
