package es.us.isa.restest.testcases;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import es.us.isa.idlreasoner.analyzer.Analyzer;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.specification.ParameterFeatures;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.apache.logging.log4j.LogManager;

import static es.us.isa.restest.util.CSVManager.*;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.IDLAdapter.restest2idlTestCase;
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
	private Boolean enableOracles;							// True (default) if you want to assert the response. False if you only want to execute the request
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

	public TestCase(String id, Boolean faulty, String operationId, String path, HttpMethod method) {
		this.id = id;
		this.faulty = faulty;
		this.fulfillsDependencies = false; // By default, a test case does not satisfy inter-parameter dependencies
		this.enableOracles = true;
		this.operationId = operationId;
		this.path = path;
		this.method = method;
		this.inputFormat = "application/json";
		this.outputFormat = "application/json";
		this.headerParameters = new HashMap<>();
		this.queryParameters = new HashMap<>();
		this.pathParameters = new HashMap<>();
		this.formParameters = new HashMap<>();
	}
	
	public TestCase(TestCase testCase) {
		this(testCase.id, testCase.faulty, testCase.operationId, testCase.path, testCase.method);
		this.faultyReason = testCase.faultyReason;
		this.fulfillsDependencies = testCase.fulfillsDependencies;
		this.enableOracles = testCase.enableOracles;
		this.inputFormat = testCase.inputFormat;
		this.headerParameters.putAll(testCase.headerParameters);
		this.queryParameters.putAll(testCase.queryParameters);
		this.pathParameters.putAll(testCase.pathParameters);
		this.formParameters.putAll(testCase.formParameters);
		this.bodyParameter = testCase.bodyParameter;
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

	public void addParameter(ParameterFeatures parameter, String value) {
		addParameter(parameter.getIn(), parameter.getName(), value);
	}

	public void addParameter(TestParameter parameter, String value) {
		addParameter(parameter.getIn(), parameter.getName(), value);
	}

	private void addParameter(String in, String paramName, String paramValue) {
		switch (in) {
			case "header":
				addHeaderParameter(paramName, paramValue);
				break;
			case "query":
				addQueryParameter(paramName, paramValue);
				break;
			case "path":
				addPathParameter(paramName, paramValue);
				break;
			case "body":
				setBodyParameter(paramValue);
				break;
			case "formData":
				addFormParameter(paramName, paramValue);
				break;
			default:
				throw new IllegalArgumentException("Parameter type not supported: " + in);
		}
	}

	public void removeParameter(ParameterFeatures parameter) {
		removeParameter(parameter.getIn(), parameter.getName());
	}

	public void removeParameter(TestParameter parameter) {
		removeParameter(parameter.getIn(), parameter.getName());
	}

	private void removeParameter(String in, String paramName) {
		switch (in) {
			case "query":
				removeQueryParameter(paramName);
				break;
			case "header":
				removeHeaderParameter(paramName);
				break;
			case "path":
				removePathParameter(paramName);
				break;
			case "formData":
				removeFormParameter(paramName);
				break;
			case "body":
				setBodyParameter(null);
				break;
			default:
				throw new IllegalArgumentException("Parameter type '" + in + "' not supported.");
		}
	}

	public Map<String, String> getQueryParameters() {
		return queryParameters;
	}

	public void setQueryParameters(Map<String, String> inputParameters) {
		this.queryParameters = inputParameters;
	}

	public Map<String, String> getFormParameters() { return formParameters; }

	public void setFormParameters(Map<String, String> formParameters) {
		this.formParameters = formParameters;
		setFormDataContentType();
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

	public void addFormParameter(String name, String value) {
		formParameters.put(name, value);
		setFormDataContentType();
	}

	public void addFormParameters(Map<String,String> params) {
		formParameters.putAll(params);
		setFormDataContentType();
	}

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

	public Boolean getEnableOracles() {
		return enableOracles;
	}

	public void setEnableOracles(Boolean enableOracles) {
		this.enableOracles = enableOracles;
	}

	private void setFormDataContentType() {
		if (!inputFormat.equals("application/x-www-form-urlencoded") && formParameters.size() > 0)
			inputFormat = "application/x-www-form-urlencoded";
	}

	
	// Export the test case to CSV
	public void exportToCSV(String filePath) {
		if (!checkIfExists(filePath)) // If the file doesn't exist, create it (only once)
			createFileWithHeader(filePath, "testCaseId,faulty,faultyReason,fulfillsDependencies,operationId,path,httpMethod,inputContentType,outputContentType," +
					"headerParameters,pathParameters,queryParameters,formParameters,bodyParameter");

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
		rowEnding.append(",").append(bodyParameter == null ? "" : bodyParameter);

		writeRow(filePath, rowBeginning + rowEnding);
	}
	
	
	
	public List<String> getValidationErrors(OpenApiInteractionValidator validator) {
		String fullPath = this.getPath();
		for (Map.Entry<String, String> pathParam : this.getPathParameters().entrySet())
			fullPath = fullPath.replace("{" + pathParam.getKey() + "}", pathParam.getValue());

		SimpleRequest.Builder requestBuilder = new SimpleRequest.Builder(this.getMethod().toString(), fullPath)
				.withBody(this.getBodyParameter())
				.withContentType(this.getInputFormat());
		this.getQueryParameters().forEach(requestBuilder::withQueryParam);
		this.getHeaderParameters().forEach(requestBuilder::withHeader);

		if (this.getFormParameters().size() > 0) {
			StringBuilder formDataBody = new StringBuilder();
			try {
				for (Map.Entry<String, String> formParam : this.getFormParameters().entrySet()) {
					formDataBody.append(encode(formParam.getKey(), StandardCharsets.UTF_8.toString())).append("=").append(encode(formParam.getValue(), StandardCharsets.UTF_8.toString())).append("&");
				}
			} catch (UnsupportedEncodingException e) {
				LogManager.getLogger(TestCase.class.getName()).warn("Parameters of test case could not be encoded. Stack trace:");
				e.printStackTrace();
			}
			requestBuilder.withBody(formDataBody.toString());
			requestBuilder.withContentType("application/x-www-form-urlencoded");
		}

		return validator.validateRequest(requestBuilder.build()).getMessages().stream()
				.filter(m -> m.getLevel() != ValidationReport.Level.IGNORE)
				.map(m -> m.getKey() + ": " + m.getMessage()).collect(Collectors.toList());
	}
	

	/**
	 * Returns true if the test case is valid according to the specification, false otherwise.
	 * @param tc
	 * @param validator
	 * @return
	 */
	public Boolean isValid(OpenApiInteractionValidator validator) {
		
		return getValidationErrors(validator).isEmpty();
	}

	/**
	 * Returns true if the test case fulfills inter-parameter dependencies, false otherwise
	 * @param tc
	 * @param idlReasoner
	 * @return
	 */
	public static Boolean checkFulfillsDependencies(TestCase tc, Analyzer idlReasoner) {
		if (idlReasoner == null)
			return true;
		return idlReasoner.isValidRequest(restest2idlTestCase(tc));
	}

	public String toString() {
		return id;
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bodyParameter == null) ? 0 : bodyParameter.hashCode());
		result = prime * result + ((faulty == null) ? 0 : faulty.hashCode());
		result = prime * result + ((faultyReason == null) ? 0 : faultyReason.hashCode());
		result = prime * result + ((enableOracles == null) ? 0 : enableOracles.hashCode());
		result = prime * result + ((formParameters == null) ? 0 : formParameters.hashCode());
		result = prime * result + ((fulfillsDependencies == null) ? 0 : fulfillsDependencies.hashCode());
		result = prime * result + ((headerParameters == null) ? 0 : headerParameters.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((inputFormat == null) ? 0 : inputFormat.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		result = prime * result + ((operationId == null) ? 0 : operationId.hashCode());
		result = prime * result + ((outputFormat == null) ? 0 : outputFormat.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((pathParameters == null) ? 0 : pathParameters.hashCode());
		result = prime * result + ((queryParameters == null) ? 0 : queryParameters.hashCode());
		return result;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof TestCase)) {
			return false;
		}
		TestCase other = (TestCase) obj;
		if (bodyParameter == null) {
			if (other.bodyParameter != null) {
				return false;
			}
		} else if (!bodyParameter.equals(other.bodyParameter)) {
			return false;
		}
		if (faulty == null) {
			if (other.faulty != null) {
				return false;
			}
		} else if (!faulty.equals(other.faulty)) {
			return false;
		}
		if (faultyReason == null) {
			if (other.faultyReason != null) {
				return false;
			}
		} else if (!faultyReason.equals(other.faultyReason)) {
			return false;
		}
		if (enableOracles == null) {
			if (other.enableOracles != null) {
				return false;
			}
		} else if (!enableOracles.equals(other.enableOracles)) {
			return false;
		}
		if (formParameters == null) {
			if (other.formParameters != null) {
				return false;
			}
		} else if (!formParameters.equals(other.formParameters)) {
			return false;
		}
		if (fulfillsDependencies == null) {
			if (other.fulfillsDependencies != null) {
				return false;
			}
		} else if (!fulfillsDependencies.equals(other.fulfillsDependencies)) {
			return false;
		}
		if (headerParameters == null) {
			if (other.headerParameters != null) {
				return false;
			}
		} else if (!headerParameters.equals(other.headerParameters)) {
			return false;
		}
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (inputFormat == null) {
			if (other.inputFormat != null) {
				return false;
			}
		} else if (!inputFormat.equals(other.inputFormat)) {
			return false;
		}
		if (method != other.method) {
			return false;
		}
		if (operationId == null) {
			if (other.operationId != null) {
				return false;
			}
		} else if (!operationId.equals(other.operationId)) {
			return false;
		}
		if (outputFormat == null) {
			if (other.outputFormat != null) {
				return false;
			}
		} else if (!outputFormat.equals(other.outputFormat)) {
			return false;
		}
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		if (pathParameters == null) {
			if (other.pathParameters != null) {
				return false;
			}
		} else if (!pathParameters.equals(other.pathParameters)) {
			return false;
		}
		if (queryParameters == null) {
			if (other.queryParameters != null) {
				return false;
			}
		} else if (!queryParameters.equals(other.queryParameters)) {
			return false;
		}
		return true;
	}
}
