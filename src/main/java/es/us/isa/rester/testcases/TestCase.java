package es.us.isa.rester.testcases;

import java.util.HashMap;
import java.util.Map;
import io.swagger.models.HttpMethod;
import io.swagger.models.Response;

/** Domain-independent test case
 * 
 * @author Sergio Segura
 *
 */
public class TestCase {
	
	private String operationId;								// Id of the operation (ex. getAlbums)
	private HttpMethod method;								// HTTP method
	private String path;									// Request path
	private String outputFormat;							// Output format
	private Map<String, String> headerParameters;			// Header parameters
	private Map<String, String> pathParameters;				// Path parameters
	private Map<String, String> queryParameters;			// Input parameters and values
	private Map<String, Response> expectedOutputs;			// Possible outputs
	private Response expectedSucessfulOutput; 				// Expected output in case the request is successful (helpful for stats computation)
	
	public TestCase(String operationId, String path, HttpMethod method) {
		this.operationId = operationId;
		this.path = path;
		this.method = method;
		this.outputFormat = "application/json";
		this.headerParameters = new HashMap<String,String>();
		this.queryParameters = new HashMap<String,String>();
		this.pathParameters = new HashMap<String,String>();
	}

	public Response getExpectedSucessfulOutput() {
		return expectedSucessfulOutput;
	}

	public void setExpectedSucessfulOutput(Response expectedSucessfulOutput) {
		this.expectedSucessfulOutput = expectedSucessfulOutput;
	}

	public HttpMethod getMethod() {
		return method;
	}

	public void setMethod(HttpMethod method) {
		this.method = method;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Map<String, String> getQueryParameters() {
		return queryParameters;
	}

	public void setQueryParameters(Map<String, String> inputParameters) {
		this.queryParameters = inputParameters;
	}

	public Map<String, Response> getExpectedOutputs() {
		return expectedOutputs;
	}

	public void setExpectedOutputs(Map<String, Response> expectedOutputs) {
		this.expectedOutputs = expectedOutputs;
	}

	public String getOperationId() {
		return operationId;
	}

	public void setOperationId(String operationId) {
		this.operationId = operationId;
	}

	public String getOutputFormat() {
		return outputFormat;
	}

	public void setOutputFormat(String outputFormat) {
		this.outputFormat = outputFormat;
	}

	public Map<String, String> getHeaderParameters() {
		return headerParameters;
	}

	public void setHeaderParameters(Map<String, String> headerParameters) {
		this.headerParameters = headerParameters;
	}
	
	public void addQueryParameter(String name, String value) {
		queryParameters.put(name, value);
	}
	
	public void addQueryParameters(Map<String,String> params) {
		queryParameters.putAll(params);
	}
	
	public void addPathParameter(String name, String value) {
		pathParameters.put(name, value);
	}
	
	public void addPathParameters(Map<String,String> params) {
		pathParameters.putAll(params);
	}
	
	public void addHeaderParameter(String name, String value) {
		headerParameters.put(name, value);
	}
	
	public void addHeaderParameters(Map<String,String> params) {
		headerParameters.putAll(params);
	}

	public Map<String, String> getPathParameters() {
		return pathParameters;
	}

	public void setPathParameters(Map<String, String> pathParameters) {
		this.pathParameters = pathParameters;
	}
}
