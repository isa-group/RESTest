package es.us.isa.rester.configuration.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import es.us.isa.rester.configuration.TestConfigurationIO;
import es.us.isa.rester.configuration.pojos.Auth;
import es.us.isa.rester.configuration.pojos.GenParameter;
import es.us.isa.rester.configuration.pojos.Generator;
import es.us.isa.rester.configuration.pojos.HeaderParam;
import es.us.isa.rester.configuration.pojos.Operation;
import es.us.isa.rester.configuration.pojos.QueryParam;
import es.us.isa.rester.configuration.pojos.TestConfiguration;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.configuration.pojos.TestParameter;
import es.us.isa.rester.configuration.pojos.TestPath;
import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.util.TestConfigurationFilter;
import io.swagger.models.HttpMethod;
import io.swagger.models.Path;
import io.swagger.models.parameters.Parameter;

public class DefaultTestConfigurationGenerator {

	
	/**
	 * Generate a default test configuration file for a given Open API specification
	 * @param spec Open API specification
	 * @param destination Path of the output test configuration file
	 * @param filters Set the paths and HTTP methods to be included in the test configuration file
	 * @return
	 */
	public TestConfigurationObject generate (OpenAPISpecification spec, String destination, Collection<TestConfigurationFilter> filters) {
		
		TestConfigurationObject conf = new TestConfigurationObject();
		
		// Authentication configuration (not required by default)
		conf.setAuth(generateDefaultAuthentication());
		// TODO: Read authentication settings from specification (https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#securitySchemeObject)
		
		// Paths
		TestConfiguration testConf = new TestConfiguration();
		testConf.setTestPaths(generatePaths(spec,filters));
			
		conf.setTestConfiguration(testConf);
		
		// Write configuration to file
		TestConfigurationIO.toFile(conf, destination);
		
		return conf;
	}
	
	// Generate the test configuration data for paths
	private List<TestPath> generatePaths(OpenAPISpecification spec, Collection<TestConfigurationFilter> filters) {
		
		List<TestPath> confPaths = new ArrayList<TestPath>();
		
		for (TestConfigurationFilter filter: filters) {
			Map<String,Path> paths = spec.getSpecification().getPaths();
			for(Entry<String,Path> path: paths.entrySet())
				if (filter.getPath()==null || path.getKey().equalsIgnoreCase(filter.getPath()))
					confPaths.add(generatePath(path,filter.getMethods()));
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
			testParam.setFilter(false);
			
			// Set default generator (String)
			Generator gen = new Generator();
			gen.setType("RandomInputValueIterator");
			
			List<GenParameter> genParams = new ArrayList<GenParameter>();
			
			GenParameter valuesParam = new GenParameter();
			valuesParam.setName("values");
			List<String> values = new ArrayList<String>();
			values.add("value 1");
			values.add("value 2");
			valuesParam.setValues(values);
		
			genParams.add(valuesParam);
			
			gen.setGenParameters(genParams);
			testParam.setGenerator(gen);
			
			testParameters.add(testParam);
		}
		
		return testParameters;
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
