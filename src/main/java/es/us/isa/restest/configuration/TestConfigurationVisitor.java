package es.us.isa.restest.configuration;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;


/**
 * This class provides direct access to different part of test configuration objects.
 */
public class TestConfigurationVisitor {

	private TestConfigurationVisitor() {}

	/**
	 * Returns the test configuration object for a given operation
	 * @param conf General test configuration object
	 * @param path	Path under test
	 * @param method Method under test
	 * @return Operation
	 */
	public static Operation getOperation(TestConfigurationObject conf, String path, String method) {
		List<Operation> operationsOfPath = getOperationsOfTestPath(conf, path);
		
		return getTestOperation(operationsOfPath, method);
	}
	
	/**
	 * Returns the test configuration object for a given operation
	 * @param operationsOfPath operations of a path
	 * @param method HTTP method
	 * @return Operation test configuration object
	 */
	public static Operation getTestOperation(List<Operation> operationsOfPath, String method) {
		boolean found = false;
		Operation top = null;
		
		Iterator<Operation> it = operationsOfPath.iterator();
		while (it.hasNext() && !found) {
			Operation operation = it.next();
			if (operation.getMethod().equalsIgnoreCase(method)) {
				top = operation;
				found = true;
			}
		}
		
		return top;
	}
	

	/**
	 * Returns the test configuration object for a path
	 * @param conf General test configuration object
	 * @param path path
	 * @return Path test configuration object
	 */
	public static List<Operation> getOperationsOfTestPath(TestConfigurationObject conf, String path) {
		List<Operation> ops = conf.getTestConfiguration().getOperations().stream()
				.filter(x -> x.getTestPath().equalsIgnoreCase(path))
				.collect(Collectors.toList());

		if(ops.isEmpty()) {
			throw new IllegalArgumentException("Path <" + path + "> does not exist in test configuration file");
		}

		return ops;
	}
	
	
	/**
	 * Returns the test configuration parameter with name=paramName of the operation with id=operationId
	 * @param conf General test configuration object
	 * @param operationId Operation identifier of the operation's parameter being searched
	 * @param paramName Name of the parameter being searched
	 * @return Test configuration parameter
	 */
	public static TestParameter getTestParameter(TestConfigurationObject conf, String operationId, String paramName) {
		Operation operation = getTestOperation(conf, operationId);
		
		if (operation==null)
			return null;
		
		return searchTestParameter(paramName,operation.getTestParameters());
	}
	
	/**
	 * Returns the operation with id=operationId
	 * @param conf Test configuration object
	 * @param operationId Operation identifier of the operation being searched
	 * @return Operation
	 */
	public static Operation getTestOperation(TestConfigurationObject conf, String operationId) {
		Operation operation=null;
		boolean found =false;
		
		Iterator<Operation> itOp = conf.getTestConfiguration().getOperations().iterator();
		while (itOp.hasNext() && !found) {
			Operation op = itOp.next();
			if (op.getOperationId().equalsIgnoreCase(operationId)) {
				operation=op;
				found=true;
			}
		}

		return operation;
	}
	
	/** Search a generator's configuration parameter in a list of parameters (or null if it does not exist)
	 * @param paramName Parameter's name
	 * @param genParameters List of generator's parameters
	 * @return Generator's configuration parameter
	 */
	public static GenParameter searchGenParameter(String paramName, List<GenParameter> genParameters) {
		GenParameter parameter = null;
		boolean found = false;
		
		Iterator<GenParameter> it =genParameters.iterator();
		while (it.hasNext() && !found) {
			GenParameter param = it.next();
			if (param.getName().equalsIgnoreCase(paramName)) {
				parameter = param;
				found = true;
			}
		}
		
		
		return parameter;
	}
	
	
	/** Search a test parameter in a list of parameters (or null if it does not exist)
	 * @param paramName Parameter's name
	 * @param testParameters List of test configuration parameters
	 * @return Test configuration parameter
	 */
	public static TestParameter searchTestParameter(String paramName, List<TestParameter> testParameters) {
		TestParameter parameter = null;
		boolean found = false;
		
		Iterator<TestParameter> it =testParameters.iterator();
		while (it.hasNext() && !found) {
			TestParameter param = it.next();
			if (param.getName().equalsIgnoreCase(paramName)) {
				parameter = param;
				found = true;
			}
		}
		
		
		return parameter;
	}

	/**
	 * Analyzes the whole testConf to look for either ParameterGenerator or BodyGenerator.
	 * If there's any of those, returns true, otherwise returns false.
	 */
	public static boolean hasStatefulGenerators(TestConfigurationObject conf) {
		return conf.getTestConfiguration().getOperations().stream().anyMatch(TestConfigurationVisitor::hasStatefulGenerators);
	}

	public static boolean hasStatefulGenerators(Operation operation) {
		try {
			return operation.getTestParameters().stream().anyMatch(p ->
					p.getGenerators().stream().anyMatch(g ->
							g.getType().equals("BodyGenerator") || g.getType().equals("ParameterGenerator")
					)
			);
		} catch (NullPointerException e) { // Parameters could be "null"
			return false;
		}
	}

	/**
	 * Analyzes the whole testConf to look for RandomInputValue generators using
	 * ARTE parameters (i.e., predicates or numberOfTriesToGenerateRegex). If there's
	 * any of those, returns true, otherwise returns false.
	 */
	public static boolean isArteEnabled(TestConfigurationObject conf) {
		return conf.getTestConfiguration().getOperations().stream().anyMatch(TestConfigurationVisitor::isArteEnabled);
	}

	public static boolean isArteEnabled(Operation operation) {
		try {
			return operation.getTestParameters().stream().anyMatch(p ->
					p.getGenerators().stream().filter(g -> g.getType().equals("RandomInputValue")).anyMatch(g ->
							g.getGenParameters().stream().anyMatch(gp ->
									gp.getName().equals("predicates") || gp.getName().equals("numberOfTriesToGenerateRegex")
							)
					)
			);
		} catch (NullPointerException e) { // Parameters or genParameters could be "null"
			return false;
		}
	}

}
