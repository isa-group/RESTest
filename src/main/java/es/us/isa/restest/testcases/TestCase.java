package es.us.isa.restest.testcases;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.atlassian.oai.validator.SwaggerRequestResponseValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.models.HttpMethod;
import io.swagger.models.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.util.CSVManager.*;
import static es.us.isa.restest.util.FileManager.*;
import static java.net.URLEncoder.encode;

/** Domain-independent test case
 * 
 * @author Sergio Segura
 *
 */
public class TestCase implements Serializable {
	
	private String id;										// Test unique identifier
	private Boolean faulty;									// True if the expected response is a 4XX status code
	private Boolean fulfillsDependencies;					// True if it does not violate any inter-parameter dependencies
	private String faultyReason;							// "none", "individual_parameter_constraint", "invalid_request_body" or "inter_parameter_dependency"
	private String operationId;								// Id of the operation (ex. getAlbums)
	private HttpMethod method;								// HTTP method
	private String path;									// Request path
	private String inputFormat;								// Input format
	private String outputFormat;							// Output format
	private Map<String, String> headerParameters;			// Header parameters
	private Map<String, String> pathParameters;				// Path parameters
	private Map<String, String> queryParameters;			// Input parameters and values
	private Map<String, String> formParameters;				// Form-data parameters
	private String bodyParameter;							// Body parameter
	private String authentication;							// Name of the authentication scheme used in the request (e.g. 'BasicAuth'), null if none
	private Map<String, Response> expectedOutputs;			// Possible outputs
	private Response expectedSuccessfulOutput; 				// Expected output in case the request is successful (helpful for stats computation)
	
	public TestCase(String id, Boolean faulty, String operationId, String path, HttpMethod method) {
		this.id = id;
		this.faulty = faulty;
		this.fulfillsDependencies = false; // By default, a test case does not satisfy inter-parameter dependencies
		this.operationId = operationId;
		this.path = path;
		this.method = method;
		this.inputFormat = "application/json";
		this.outputFormat = "application/json";
		this.headerParameters = new HashMap<String,String>();
		this.queryParameters = new HashMap<String,String>();
		this.pathParameters = new HashMap<String,String>();
		this.formParameters = new HashMap<String,String>();
		this.authentication = null;
	}
	
	public TestCase(TestCase testCase) {
		this(testCase.id, testCase.faulty, testCase.operationId, testCase.path, testCase.method);
		this.faultyReason = testCase.faultyReason;
		this.fulfillsDependencies = testCase.fulfillsDependencies;
		this.bodyParameter = testCase.bodyParameter;
		this.pathParameters = testCase.pathParameters;
		this.queryParameters = testCase.queryParameters;
		this.headerParameters = testCase.headerParameters;
		this.formParameters = testCase.formParameters;
	}

	public Response getExpectedSuccessfulOutput() {
		return expectedSuccessfulOutput;
	}

	public void setExpectedSuccessfulOutput(Response expectedSuccessfulOutput) {
		this.expectedSuccessfulOutput = expectedSuccessfulOutput;
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

	public Map<String, String> getFormParameters() { return formParameters; }

	public void setFormParameters(Map<String, String> formParameters) { this.formParameters = formParameters; }

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

	public String getInputFormat() {
		return inputFormat;
	}

	public void setInputFormat(String inputFormat) {
		this.inputFormat = inputFormat;
	}

	public String getOutputFormat() {
		return outputFormat;
	}

	public void setOutputFormat(String outputFormat) {
		this.outputFormat = outputFormat;
	}

	public String getAuthentication() {
		return authentication;
	}

	public void setAuthentication(String authentication) {
		this.authentication = authentication;
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

	public void addFormParameter(String name, String value) { formParameters.put(name, value); }

	public void addFormParameters(Map<String,String> params) { formParameters.putAll(params); }

	public void removeQueryParameter(String name) {
		queryParameters.remove(name);
	}

	public void removePathParameter(String name) {
		pathParameters.remove(name);
	}

	public void removeHeaderParameter(String name) {
		headerParameters.remove(name);
	}

	public void removeFormParameter(String name) {
		formParameters.remove(name);
	}

	public Map<String, String> getPathParameters() {
		return pathParameters;
	}

	public void setPathParameters(Map<String, String> pathParameters) {
		this.pathParameters = pathParameters;
	}

	public String getBodyParameter() {
		return bodyParameter;
	}

	public void setBodyParameter(String bodyParameter) {
		this.bodyParameter = bodyParameter;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Boolean getFaulty() {
		return faulty;
	}

	public void setFaulty(Boolean faulty) {
		this.faulty = faulty;
	}

	public Boolean getFulfillsDependencies() {
		return fulfillsDependencies;
	}

	public void setFulfillsDependencies(Boolean fulfillsDependencies) {
		this.fulfillsDependencies = fulfillsDependencies;
	}

	public String getFaultyReason() {
		return faultyReason;
	}

	public void setFaultyReason(String faultyReason) {
		this.faultyReason = faultyReason;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TestCase testCase = (TestCase) o;
		return Objects.equals(id, testCase.id) &&
				Objects.equals(faulty, testCase.faulty) &&
				Objects.equals(fulfillsDependencies, testCase.fulfillsDependencies) &&
				Objects.equals(faultyReason, testCase.faultyReason) &&
				Objects.equals(operationId, testCase.operationId) &&
				method == testCase.method &&
				Objects.equals(path, testCase.path) &&
				Objects.equals(inputFormat, testCase.inputFormat) &&
				Objects.equals(outputFormat, testCase.outputFormat) &&
				Objects.equals(headerParameters, testCase.headerParameters) &&
				Objects.equals(pathParameters, testCase.pathParameters) &&
				Objects.equals(queryParameters, testCase.queryParameters) &&
				Objects.equals(formParameters, testCase.formParameters) &&
				Objects.equals(bodyParameter, testCase.bodyParameter) &&
				Objects.equals(authentication, testCase.authentication) &&
				Objects.equals(expectedOutputs, testCase.expectedOutputs) &&
				Objects.equals(expectedSuccessfulOutput, testCase.expectedSuccessfulOutput);
	}

	public void exportToCSV(String filePath) {
		if (!checkIfExists(filePath)) // If the file doesn't exist, create it (only once)
			createFileWithHeader(filePath, "testCaseId,faulty,faultyReason,fulfillsDependencies,operationId,path,httpMethod,inputContentType,outputContentType," +
					"headerParameters,pathParameters,queryParameters,formParameters,bodyParameter,authentication,expectedOutputs," +
					"expectedSuccessfulOutput");

		// Generate row
		String rowBeginning = id + "," + faulty + "," + faultyReason + "," + fulfillsDependencies + "," + operationId + "," + path + "," + method.toString() + "," + inputFormat + "," + outputFormat + ",";
		StringBuilder rowEnding = new StringBuilder();
		try {
			for (Map.Entry<String, String> h: headerParameters.entrySet()) {
				rowEnding.append(encode(h.getKey(), StandardCharsets.UTF_8.toString())).append("=").append(encode(h.getValue(), StandardCharsets.UTF_8.toString())).append(";");
			}
			rowEnding.append(",");
			for (Map.Entry<String, String> p: pathParameters.entrySet()) {
				rowEnding.append(encode(p.getKey(), StandardCharsets.UTF_8.toString())).append("=").append(encode(p.getValue(), StandardCharsets.UTF_8.toString())).append(";");
			}
			rowEnding.append(",");
			for (Map.Entry<String, String> q: queryParameters.entrySet()) {
				rowEnding.append(encode(q.getKey(), StandardCharsets.UTF_8.toString())).append("=").append(encode(q.getValue(), StandardCharsets.UTF_8.toString())).append(";");
			}
			rowEnding.append(",");
			for (Map.Entry<String, String> f: formParameters.entrySet()) {
				rowEnding.append(encode(f.getKey(), StandardCharsets.UTF_8.toString())).append("=").append(encode(f.getValue(), StandardCharsets.UTF_8.toString())).append(";");
			}
		} catch (UnsupportedEncodingException e) {
			rowEnding = new StringBuilder(",,,");
			LogManager.getLogger(TestCase.class.getName()).warn("Parameters of test case could not be encoded. Stack trace:");
			e.printStackTrace();
		}
		rowEnding.append(",").append(bodyParameter == null ? "" : bodyParameter).append(",,,");

		writeRow(filePath, rowBeginning + rowEnding);
	}

	/**
	 * Returns true if the test case is faulty, false otherwise
	 * @param tc
	 * @param validator
	 * @return
	 */
	public static Boolean checkFaulty(TestCase tc, SwaggerRequestResponseValidator validator) {
		final String[] path = {tc.getPath()};
		tc.getPathParameters().forEach((k, v) -> {
			path[0] = path[0].replace("{" + k + "}", v);
		});
		SimpleRequest request = new SimpleRequest(tc.getMethod().toString(), path[0], tc.getHeaderParameters(), tc.getQueryParameters(), tc.getBodyParameter());
		return validator.validateOnlyRequest(request).hasErrors();
	}
}
