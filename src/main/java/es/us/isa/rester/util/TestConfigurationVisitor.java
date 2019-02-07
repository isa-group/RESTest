package es.us.isa.rester.util;

import java.util.Iterator;
import java.util.List;

import es.us.isa.rester.configuration.pojos.GenParameter;
import es.us.isa.rester.configuration.pojos.Operation;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.configuration.pojos.TestParameter;
import es.us.isa.rester.configuration.pojos.TestPath;

public class TestConfigurationVisitor {

	
	/**
	 * Returns the test configuration object for a given operation
	 * @param conf General test configuration object
	 * @param path	Path under test
	 * @param method Method under test
	 * @return Operation
	 */
	public static Operation getOperation(TestConfigurationObject conf, String path, String method) {
		TestPath testPath = getTestPath(conf, path);
		Operation testOperation = getTestOperation(testPath, method);
		
		return testOperation;
	}
	
	/**
	 * Returns the test configuration object for a given operation
	 * @param path path
	 * @param method HTTP method
	 * @return Operation test configuration object
	 */
	public static Operation getTestOperation(TestPath path, String method) {
		boolean found = false;
		Operation top = null;
		
		Iterator<Operation> it = path.getOperations().iterator();
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
	public static TestPath getTestPath(TestConfigurationObject conf, String path) {
		boolean found = false;
		TestPath tp = null;
		
		Iterator<TestPath> it = conf.getTestConfiguration().getTestPaths().iterator();
		while (it.hasNext() && !found) {
			TestPath testPath = it.next();
			if (testPath.getTestPath().equalsIgnoreCase(path)) {
				tp = testPath;
				found = true;
			}
		}
		return tp;
	}
	
	
	/**
	 * Returns the test configuration parameter with name=paramName of the operation with id=operationId
	 * @param conf
	 * @param operationId
	 * @param paramName
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
		
		Iterator<TestPath> itPath = conf.getTestConfiguration().getTestPaths().iterator();
		while (itPath.hasNext() && !found) {
			TestPath path = itPath.next();
			Iterator<Operation> itOp = path.getOperations().iterator();
			while (itOp.hasNext() && !found) {
				Operation op = itOp.next();
				if (op.getOperationId().equalsIgnoreCase(operationId)) {
					operation=op;
					found=true;
				}
			}
		}
		return operation;
	}
	
	/** Search a generator's configuration parameter in a list of parameters (or null if it does not exist)
	 * @param paramName Parameter's name
	 * @param genParameters List of generator's parameters
	 * @return
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
	 * @return
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

}
