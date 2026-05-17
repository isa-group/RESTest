package es.us.isa.restest.writers.restassured;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.writers.IWriter;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.configuration.TestConfigurationVisitor.hasStatefulGenerators;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.apache.commons.lang3.StringEscapeUtils.escapeJava;

/** This class defines a test writer for the REST Assured framework. It creates a Java class with JUnit test cases
 * ready to be executed.
 * 
 * @author Sergio Segura &amp; Alberto Martin-Lopez
 *
 */
public class RESTAssuredWriter implements IWriter {

	private boolean OAIValidation = true;
	private boolean logging = false;				// Log everything (ONLY IF THE TEST FAILS)
	private boolean allureReport = false;			// Generate request and response attachment for allure reports
	private boolean enableStats = false;			// If true, export test results data to CSV
	private boolean enableOutputCoverage = false;	// If true, export output coverage data to CSV

	private String specPath;						// Path to OAS specification file
	private String testConfPath;					// Path to testConf
	private OpenAPISpecification spec;				// OpenAPI spec
	private TestConfigurationObject testConf;		// testConf
	private String targetDirJava;					// Path to target Java dir (where tests are generated)
	private String className;						// Test class name
	private String testId;							// Test suite ID
	private String packageName;						// Package name
	private String baseURI;							// API base URI
	private boolean logToFile;						// If 'true', REST-Assured requests and responses will be logged into external files
	private boolean statefulFilter;					// If 'true', stateful filter will be used in written classes
	private String proxy;							// Proxy to use for all requests in format host:port
	private String[] headers;

	private String APIName;							// API name (necessary for folder name of exported data)

	private static final Logger logger = LogManager.getLogger(RESTAssuredWriter.class.getName());
	
	public RESTAssuredWriter(String specPath, String testConfPath, String targetDirJava, String className, String packageName, String baseURI, Boolean logToFile) {
		this.specPath = specPath;
		this.spec = new OpenAPISpecification(specPath);
		this.testConfPath = testConfPath;
		this.testConf = loadConfiguration(testConfPath, spec);
		this.targetDirJava = targetDirJava;
		this.className = className;
		this.packageName = packageName;
		this.baseURI = baseURI;
		this.logToFile = logToFile;
		this.statefulFilter = hasStatefulGenerators(testConf);
	}
	
	/* (non-Javadoc)
	 * @see es.us.isa.restest.writers.IWriter#write(java.util.Collection)
	 */
	@Override
	public void write(Collection<TestCase> testCases) {
		
		// Initializing content
		String contentFile = "";
		
		// Generating imports
		contentFile += generateImports(packageName);
		
		// Generate className
		contentFile += generateClassName(className);
		
		// Generate attributes
		contentFile += generateAttributes(specPath);
		
		// Generate variables to be used.
		contentFile += generateSetUp(baseURI);

		// Generate tests
		int ntest=1;
		for(TestCase t: testCases)
			contentFile += generateTest(t,ntest++);
		
		// Close class
		contentFile += "}\n";
		
		//Save to file
		saveToFile(targetDirJava,className,contentFile);
		
		/* Test Compile
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		compiler.run(System.in, System.out, System.err, TEST_LOCATION + this.specification.getInfo().getTitle().replaceAll(" ", "") + "Test.java");
		*/
	}

	private String generateImports(String packageName) {
		String content = "";
		
		if (packageName!=null)
			content += "package " + packageName + ";\n\n";
				
		content += "import org.junit.*;\n"
				+  "import io.restassured.RestAssured;\n"
				+  "import io.restassured.response.Response;\n"
				+  "import org.junit.FixMethodOrder;\n"
				+  "import static org.junit.Assert.fail;\n"
				+  "import com.fasterxml.jackson.databind.ObjectMapper;\n"
				+  "import static org.junit.Assert.assertTrue;\n"
				+  "import org.junit.runners.MethodSorters;\n"
		    +  "import io.qameta.allure.restassured.AllureRestAssured;\n"
				+  "import es.us.isa.restest.writers.restassured.filters.StatusCode5XXFilter;\n"
				+  "import es.us.isa.restest.writers.restassured.filters.NominalOrFaultyTestCaseFilter;\n"
				+  "import es.us.isa.restest.writers.restassured.filters.StatefulFilter;\n"
				+  "import java.io.File;\n";

		if (headers != null) {
			content += "import es.us.isa.restest.writers.restassured.filters.HeadersFilter;\n";
		}

		// OAIValidation (Optional)
//		if (OAIValidation)
		content += 	"import es.us.isa.restest.writers.restassured.filters.ResponseValidationFilter;\n";

//		// Coverage filter (optional)
//		if (enableOutputCoverage)
//			content += 	"import es.us.isa.testcases.restassured.filters.CoverageFilter;\n";

		// Coverage filter (optional)
		if (enableStats || enableOutputCoverage)
			content += 	"import es.us.isa.restest.writers.restassured.filters.CSVFilter;\n";

		if (logToFile) {
			content +=	"import java.io.PrintStream;\n"
					+	"import org.apache.logging.log4j.LogManager;\n"
					+   "import org.apache.logging.log4j.Logger;\n"
					+ 	"import org.apache.logging.log4j.Level;\n"
					+   "import org.apache.logging.log4j.io.IoBuilder;\n"
					+	"import org.apache.logging.log4j.core.LoggerContext;\n"
					+   "import io.restassured.filter.log.RequestLoggingFilter;\n"
					+   "import io.restassured.filter.log.ResponseLoggingFilter;\n"
					+	"import es.us.isa.restest.util.LoggerStream;\n";

		}

//		if (statefulFilter) {
//			content +=  "import java.nio.file.Files;\n"
//					+	"import java.nio.file.Paths;\n"
//					+   "import com.fasterxml.jackson.databind.node.ArrayNode;\n";
//		}
		
		content +="\n";
		
		return content;
	}
	
	private String generateClassName(String className) {
		return "@FixMethodOrder(MethodSorters.NAME_ASCENDING)\n"
			 + "public class " + className + " {\n\n";
	}
	
	private String generateAttributes(String specPath) {
		String content = "";
		
//		if (OAIValidation)
		content += "\tprivate static final String OAI_JSON_URL = \"" + specPath + "\";\n";

		if (headers != null) {
			content += "\tprivate static final HeadersFilter headersFilter = new HeadersFilter();\n";
		}

		content += "\tprivate static final StatusCode5XXFilter statusCode5XXFilter = new StatusCode5XXFilter();\n"
				+  "\tprivate static final NominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = new NominalOrFaultyTestCaseFilter();\n"
				+  "\tprivate static final ResponseValidationFilter validationFilter = new ResponseValidationFilter(OAI_JSON_URL);\n";
		if (statefulFilter)
			content += "\tprivate static final StatefulFilter statefulFilter = new StatefulFilter(\"" + specPath.substring(0, specPath.lastIndexOf('/')) + "\");\n";

		if (logToFile) {
			content +=  "\tprivate static RequestLoggingFilter requestLoggingFilter;\n"
					+   "\tprivate static ResponseLoggingFilter responseLoggingFilter;\n"
					+   "\tprivate static Logger logger = LogManager.getLogger(" + className + ".class.getName());\n";
		}


		if (allureReport)
			content += "\tprivate static final AllureRestAssured allureFilter = new AllureRestAssured();\n";

		if (enableStats || enableOutputCoverage) { // This is only needed to export output data to the proper folder
			content += "\tprivate static final String APIName = \"" + APIName + "\";\n"
					+  "\tprivate static final String testId = \"" + testId + "\";\n"
					+  "\tprivate static final CSVFilter csvFilter = new CSVFilter(APIName, testId);\n";
		}

//		if (statefulFilter) {
//			content += "\tprivate static final ObjectMapper objectMapper = new ObjectMapper();\n";
//		}

		content += "\n";
		
		return content;
	}
	
	private String generateSetUp(String baseURI) {
		String content = "";

		content += "\t@BeforeClass\n"
				+  "\tpublic static void setUp() {\n";

		if (proxy != null) {
			content +=  "\t\tSystem.setProperty(\"http.proxyHost\", \"" + proxy.split(":")[0] + "\");\n"
					+	"\t\tSystem.setProperty(\"http.proxyPort\", \"" + proxy.split(":")[1] + "\");\n"
					+	"\t\tSystem.setProperty(\"http.nonProxyHosts\", \"localhost|127.0.0.1\");\n"
					+	"\t\tSystem.setProperty(\"https.proxyHost\", \"" + proxy.split(":")[0] + "\");\n"
					+	"\t\tSystem.setProperty(\"https.proxyPort\", \"" + proxy.split(":")[1] + "\");\n"
					+	"\t\tSystem.setProperty(\"https.nonProxyHosts\", \"localhost|127.0.0.1\");\n\n";
		}

		content += "\t\tRestAssured.baseURI = " + "\"" + baseURI + "\";\n\n";

		if (logToFile) {
			content +=	"\t\t// Configure logging\n"
					+	"\t\tSystem.setProperty(\"logFilename\", \"" + System.getProperty("logFilename") + "\");\n"
					+   "\t\tlogger = LogManager.getLogger(" + className + ".class.getName());\n"
					+   "\t\tPrintStream logStream = IoBuilder.forLogger(logger).buildPrintStream();\n"
					+   "\t\trequestLoggingFilter = RequestLoggingFilter.logRequestTo(logStream);\n"
					+   "\t\tresponseLoggingFilter = new ResponseLoggingFilter(logStream);\n"
					+	"\t\tLoggerContext ctx = (LoggerContext) LogManager.getContext(false);\n"
					+	"\t\tFile file = new File(\"src/main/resources/log4j2-logToFile.properties\");\n"
					+	"\t\tctx.setConfigLocation(file.toURI());\n"
					+	"\t\tctx.reconfigure();\n\n";
		}

		if (enableStats || enableOutputCoverage) {
			content += "\t\tstatusCode5XXFilter.setAPIName(APIName);\n"
					+  "\t\tstatusCode5XXFilter.setTestId(testId);\n"
					+  "\t\tnominalOrFaultyTestCaseFilter.setAPIName(APIName);\n"
					+  "\t\tnominalOrFaultyTestCaseFilter.setTestId(testId);\n"
					+  "\t\tvalidationFilter.setAPIName(APIName);\n"
					+  "\t\tvalidationFilter.setTestId(testId);\n";
		}

		if (headers != null && headers.length > 0) {
			String headers = Arrays.stream(this.headers)
					.map(header -> String.format("\"%s\"", header))
					.collect(Collectors.joining(",\n\t\t\t"));

			content += "\n\t\theadersFilter.setHeaders(\n"
					+ "\t\t\t" + headers + "\n"
					+ "\t\t);\n";
		}

		content += "\t}\n\n";

		return content;
	}

	private String generateTest(TestCase t, int instance) {
		String content="";
		
		// Generate test method header
		content += generateMethodHeader(t,instance);

		// Generate test case ID (only if stats enabled)
		content += generateTestCaseId(t.getId());

		// Generate initialization of filters for those that need it
		content += generateFiltersInitialization(t);

		// Generate the start of the try block
		content += generateTryBlockStart();
		
		// Generate RESTAssured object pointing to the right path
		content += generateRESTAssuredObject(t);
		
		// Generate header parameters
		content += generateHeaderParameters(t);
		
		// Generate query parameters
		content += generateQueryParameters(t);
		
		// Generate path parameters
		content += generatePathParameters(t);

		//Generate form-data parameters
		content += generateFormParameters(t);

		// Generate body parameter
		content += generateBodyParameter(t);

		// Generate filters
		content += generateFilters(t);
		
		// Generate HTTP request
		content += generateHTTPRequest(t);
		
		// Generate basic response validation
		//if(!OAIValidation)
//			content += generateResponseValidation(t);

		// Generate all stuff needed after the RESTAssured response validation
		content += generatePostResponseValidation(t);

		// Generate the end of the try block, including its corresponding catch
		content += generateTryBlockEnd();
		
		// Close test method
		content += "\t}\n\n";
		
		return content;
	}


	private String generateMethodHeader(TestCase t, int instance) {
		return "\t@Test\n" +
				"\tpublic void " + t.getId() + "() {\n";
	}

	private String generateTestCaseId(String testCaseId) {
		String content = "";

		if (enableStats || enableOutputCoverage) {
			content += "\t\tString testResultId = \"" + testCaseId + "\";\n\n";
		}

		return content;
	}

	private String generateFiltersInitialization(TestCase t) {
		String content = "";

		content += "\t\tnominalOrFaultyTestCaseFilter.updateFaultyData(" + t.getFaulty() + ", " + t.getFulfillsDependencies() + ", \"" + escapeJava(t.getFaultyReason()) + "\");\n" +
				"\t\tstatusCode5XXFilter.updateFaultyData(" + t.getFaulty() + ", " + t.getFulfillsDependencies() + ", \"" + escapeJava(t.getFaultyReason()) + "\");\n";

		if (enableStats || enableOutputCoverage)
			content += "\t\tcsvFilter.setTestResultId(testResultId);\n" +
					"\t\tstatusCode5XXFilter.setTestResultId(testResultId);\n" +
					"\t\tnominalOrFaultyTestCaseFilter.setTestResultId(testResultId);\n" +
					"\t\tvalidationFilter.setTestResultId(testResultId);\n";

		if (statefulFilter && t.getMethod().equals(HttpMethod.GET)) {
			content += "\t\tstatefulFilter.setOperation(\"" + t.getMethod().toString() + "\", \"" + t.getPath() + "\");\n";
		}

		content += "\n";

		return content;
	}

	private String generateTryBlockStart() {
		return "\t\ttry {\n";
	}

	private String generateRESTAssuredObject(TestCase t) {
		String content = "";
			
		content += "\t\t\tResponse response = RestAssured\n"
				+  "\t\t\t.given()\n";
			
//		if (logging)
//			content +="\t\t\t\t.log().ifValidationFails()\n";
		if (logging && !logToFile)
			content +="\t\t\t\t.log().all()\n";

		return content;
	}
	
	private String generateHeaderParameters(TestCase t) {
		String content = "";
		
		for(Entry<String,String> param: t.getHeaderParameters().entrySet())
			content += "\t\t\t\t.header(\"" + param.getKey() + "\", \"" + escapeJava(param.getValue()) + "\")\n";
		
		return content;
	}
	
	private String generateQueryParameters(TestCase t) {
		String content = "";
		
		for(Entry<String,String> param: t.getQueryParameters().entrySet())
			content += "\t\t\t\t.queryParam(\"" + param.getKey() + "\", \"" + escapeJava(param.getValue()) + "\")\n";
		
		return content;
	}
	
	private String generatePathParameters(TestCase t) {
		String content = "";
		
		for(Entry<String,String> param: t.getPathParameters().entrySet())
			content += "\t\t\t\t.pathParam(\"" + param.getKey() + "\", \"" + escapeJava(param.getValue().replace("{", "")) + "\")\n";
			// TODO: Once REST-Assured fixes the bug, stop removing "{" chars from path parameters

		return content;
	}

	private String generateFormParameters(TestCase t) {
		String content = "";

		if(t.getFormParameters().entrySet().stream().anyMatch(x -> checkIfExists(x.getValue())))
			content += "\t\t\t\t.contentType(\"multipart/form-data\")\n";
		else if(!t.getFormParameters().isEmpty())
			content += "\t\t\t\t.contentType(\"application/x-www-form-urlencoded\")\n";

		for(Entry<String,String> param : t.getFormParameters().entrySet()) {
			content += checkIfExists(param.getValue())? "\t\t\t\t.multiPart(\"" + param.getKey() +  "\", new File(\"" + escapeJava(param.getValue()) + "\"))\n"
					: "\t\t\t\t.formParam(\"" + param.getKey() + "\", \"" + escapeJava(param.getValue()) + "\")\n";
		}

		return content;
	}

	private String generateBodyParameter(TestCase t) {
		String content = "";
		String bodyParameter = escapeJava(t.getBodyParameter());
		if ((t.getFormParameters() == null || t.getFormParameters().size() == 0) &&
//				t.getBodyParameter() != null &&
				(t.getMethod().equals(HttpMethod.POST) || t.getMethod().equals(HttpMethod.PUT)
				|| t.getMethod().equals(HttpMethod.PATCH) ||
				(t.getBodyParameter() != null && t.getMethod().equals(HttpMethod.DELETE))))
			content += "\t\t\t\t.contentType(\"" + t.getInputFormat() + "\")\n";
		if (t.getBodyParameter() != null) {
			content += "\t\t\t\t.body(\"" + bodyParameter + "\")\n";
		}

		return content;
	}

	private String generateFilters(TestCase t) {
		String content = "";

		if (headers != null) {
			content += "\t\t\t\t.filter(headersFilter)\n";
		}

		if(logToFile) {
			content += "\t\t\t\t.filter(requestLoggingFilter)\n"
					+  "\t\t\t\t.filter(responseLoggingFilter)\n";
		}

//		if (enableOutputCoverage) // Coverage filter
//			content += "\t\t\t\t.filter(new CoverageFilter(testResultId, APIName))\n";
		if (allureReport) // Allure filter
			content += "\t\t\t\t.filter(allureFilter)\n";
		// 5XX status code oracle:
		content += "\t\t\t\t.filter(statusCode5XXFilter)\n";
		// Validation of nominal and faulty test cases
		content += "\t\t\t\t.filter(nominalOrFaultyTestCaseFilter)\n";
//		if (OAIValidation)
		content += "\t\t\t\t.filter(validationFilter)\n";
		if (enableStats || enableOutputCoverage) // CSV filter
			content += "\t\t\t\t.filter(csvFilter)\n";
		if (statefulFilter && t.getMethod().equals(HttpMethod.GET)) {
			content += "\t\t\t\t.filter(statefulFilter)\n";
		}

		return content;
	}
	
	private String generateHTTPRequest(TestCase t) {
		String content = "\t\t\t.when()\n";

		content +=	 "\t\t\t\t." + t.getMethod().name().toLowerCase() + "(\"" + t.getPath() + "\");\n";

		content += "\n\t\t\tresponse.then()";

		if (logging && !logToFile)
			content += ".log().all()";

		content += ";\n";

		return content;
	}


	private String generatePostResponseValidation(TestCase t) {
		return "\t\t\tSystem.out.println(\"Test passed.\");\n";
	}

	private String generateTryBlockEnd() {
		return "\t\t} catch (RuntimeException ex) {\n"
				+  "\t\t\tSystem.err.println(ex.getMessage());\n"
				+  "\t\t\tfail(ex.getMessage());\n"
				+  "\t\t}\n";
	}
		
	private void saveToFile(String path, String className, String contentFile) {
		try(FileWriter testClass = new FileWriter(path + "/" + className + ".java")) {
			testClass.write(contentFile);
			testClass.flush();
		} catch(Exception ex) {
			logger.error("Error writing test file");
			logger.error("Exception: ", ex);
		} 
	}

	public boolean OAIValidation() {
		return OAIValidation;
	}

	public void setOAIValidation(boolean oAIValidation) {
		OAIValidation = oAIValidation;
	}

	public boolean isLogging() {
		return logging;
	}

	public void setLogging(boolean logging) {
		this.logging = logging;
	}
	
	public boolean allureReport() {
		return allureReport;
	}

	public void setAllureReport(boolean ar) {
		this.allureReport = ar;
	}

	public boolean getEnableStats() {
		return enableStats;
	}

	public void setEnableStats(boolean enableStats) {
		this.enableStats = enableStats;
	}

	public boolean isEnableOutputCoverage() {
		return enableOutputCoverage;
	}

	public void setEnableOutputCoverage(boolean enableOutputCoverage) {
		this.enableOutputCoverage = enableOutputCoverage;
	}

	public String getSpecPath() {
		return specPath;
	}

	public void setSpecPath(String specPath) {
		this.specPath = specPath;
	}

	public String getTargetDirJava() {
		return targetDirJava;
	}

	public void setTargetDirJava(String targetDirJava) {
		this.targetDirJava = targetDirJava;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getBaseURI() {
		return baseURI;
	}

	public void setBaseURI(String baseURI) {
		this.baseURI = baseURI;
	}

	public String getAPIName() {
		return APIName;
	}

	public void setAPIName(String APIName) {
		this.APIName = APIName;
	}

	public void setTestId(String testId) {
		this.testId = testId;
	}

	public boolean isStatefulFilter() {
		return statefulFilter;
	}

	public void setStatefulFilter(boolean statefulFilter) {
		this.statefulFilter = statefulFilter;
	}

	public String getProxy() {
		return proxy;
	}

	public void setProxy(String proxy) {
		this.proxy = proxy;
	}

	public String[] getHeaders() {
		return headers;
	}

	public void setHeaders(String[] headers) {
		this.headers = headers;
	}
}
