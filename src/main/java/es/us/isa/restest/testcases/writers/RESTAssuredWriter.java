package es.us.isa.restest.testcases.writers;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Map.Entry;

import es.us.isa.restest.testcases.TestCase;
import io.qameta.allure.restassured.AllureRestAssured;
import io.swagger.models.HttpMethod;
import io.swagger.models.Response;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.apache.commons.lang3.StringEscapeUtils.escapeJava;

/** This class defines a test writer for the REST Assured framework. It creates a Java class with JUnit test cases
 * ready to be executed.
 * 
 * @author Sergio Segura & Alberto Martin-Lopez
 *
 */
public class RESTAssuredWriter implements IWriter {
	
	
	private boolean OAIValidation = true;
	private boolean logging = false;				// Log everything (ONLY IF THE TEST FAILS)
	private boolean allureReport = false;			// Generate request and response attachment for allure reports
	private boolean enableStats = false;			// If true, export test results and output coverage data to CSV

	private String specPath;						// Path to OAS specification file
	private String testFilePath;					// Path to test configuration file
	private String className;						// Test class name
	private String packageName;						// Package name
	private String baseURI;							// API base URI

	private String APIName;							// API name (necessary for folder name of exported data)
	
	public RESTAssuredWriter(String specPath, String testFilePath, String className, String packageName, String baseURI) {
		this.specPath = specPath;
		this.testFilePath = testFilePath;
		this.className = className;
		this.packageName = packageName;
		this.baseURI = baseURI;
	}
	
	/* (non-Javadoc)
	 * @see es.us.isa.restest.testcases.writers.IWriter#write(java.util.Collection)
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
		saveToFile(testFilePath,className,contentFile);
		
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
				+  "import org.apache.logging.log4j.LogManager;\n"
				+  "import org.apache.logging.log4j.Logger;\n"
				+  "import io.restassured.RestAssured;\n"
				+  "import io.restassured.response.Response;\n"
				+  "import com.fasterxml.jackson.databind.JsonNode;\n"
				+  "import com.fasterxml.jackson.databind.ObjectMapper;\n"
				+  "import java.io.IOException;\n"
				+  "import org.junit.FixMethodOrder;\n"
				+  "import static org.junit.Assert.fail;\n"
				+  "import static org.junit.Assert.assertTrue;\n"
				+  "import org.junit.runners.MethodSorters;\n"
		        +  "import io.qameta.allure.restassured.AllureRestAssured;\n"
				+  "import es.us.isa.restest.validation.StatusCode5XXFilter;\n"
				+  "import es.us.isa.restest.validation.NominalOrFaultyTestCaseFilter;\n"
				+  "import java.io.File;\n";
		
		// OAIValidation (Optional)
//		if (OAIValidation)
		content += 	"import es.us.isa.restest.validation.ResponseValidationFilter;\n";

		// Coverage filter (optional)
		if (enableStats)
			content += 	"import es.us.isa.restest.coverage.CoverageFilter;\n";
		
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
		content += "\tprivate static final String OAI_JSON_URL = \"" + specPath + "\";\n"
				+  "\tprivate final ResponseValidationFilter validationFilter = new ResponseValidationFilter(OAI_JSON_URL);\n"
				+  "\tprivate StatusCode5XXFilter statusCode5XXFilter = new StatusCode5XXFilter();\n";

		if (allureReport)
			content += "\tprivate AllureRestAssured allureFilter = new AllureRestAssured();\n";

		if (enableStats) // This is only needed to export output data to the proper folder
			content += "\tprivate final String APIName = \"" + APIName + "\";\n";

		content += "\n";
		
		return content;
	}
	
	private String generateSetUp(String baseURI) {
		return 	"\t@BeforeClass\n "
			  + "\tpublic static void setUp() {\n"
			  + "\t\tRestAssured.baseURI = " + "\"" + baseURI + "\";\n"
			  +	"\t}\n\n";
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

		// Generate all stuff needed before the RESTAssured request
		content += generatePreRequest(t);
		
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

		if (enableStats) {
			content += "\t\tString testResultId = \"" + testCaseId + "\";\n\n";
		}

		return content;
	}

	private String generateFiltersInitialization(TestCase t) {
		return "\t\tNominalOrFaultyTestCaseFilter nominalOrFaultyTestCaseFilter = " +
			   "new NominalOrFaultyTestCaseFilter(" + t.getFaulty() + ", " + t.getFulfillsDependencies() + ", \"" + t.getFaultyReason() + "\");\n\n";
	}

	private String generateTryBlockStart() {
		return "\t\ttry {\n";
	}

	private String generatePreRequest(TestCase t) {
		String content = "";

		if (t.getBodyParameter() != null) {
			content += generateJSONtoObjectConversion(t);
		}

		return content;
	}

	private String generateJSONtoObjectConversion(TestCase t) {
		String content = "";
		String bodyParameter = escapeJava(t.getBodyParameter());

		content += "\t\t\tObjectMapper objectMapper = new ObjectMapper();\n"
				+  "\t\t\tJsonNode jsonBody =  objectMapper.readTree(\""
				+  bodyParameter
				+  "\");\n\n";

		return content;
	}

	private String generateRESTAssuredObject(TestCase t) {
		String content = "";
			
		content += "\t\t\tResponse response = RestAssured\n"
				+  "\t\t\t.given()\n";
			
//		if (logging)
//			content +="\t\t\t\t.log().ifValidationFails()\n";
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
			content += "\t\t\t\t.pathParam(\"" + param.getKey() + "\", \"" + escapeJava(param.getValue()) + "\")\n";
		
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

		if ((t.getFormParameters() == null || t.getFormParameters().size() == 0) &&
				(t.getMethod().equals(HttpMethod.POST) || t.getMethod().equals(HttpMethod.PUT)
				|| t.getMethod().equals(HttpMethod.PATCH) || t.getMethod().equals(HttpMethod.DELETE)))
			content += "\t\t\t\t.contentType(\"application/json\")\n";
		if (t.getBodyParameter() != null) {
			content += "\t\t\t\t.body(jsonBody)\n";
		}

		return content;
	}

	private String generateFilters(TestCase t) {
		String content = "";

		if (enableStats) // Coverage filter
			content += "\t\t\t\t.filter(new CoverageFilter(testResultId, APIName))\n";
		if (allureReport) // Allure filter
			content += "\t\t\t\t.filter(allureFilter)\n";
		// 5XX status code oracle:
		content += "\t\t\t\t.filter(statusCode5XXFilter)\n";
		// Validation of nominal and faulty test cases
		content += "\t\t\t\t.filter(nominalOrFaultyTestCaseFilter)\n";
//		if (OAIValidation)
		content += "\t\t\t\t.filter(validationFilter)\n";


		return content;
	}
	
	private String generateHTTPRequest(TestCase t) {
		String content = "\t\t\t.when()\n";

		content +=	 "\t\t\t\t." + t.getMethod().name().toLowerCase() + "(\"" + t.getPath() + "\");\n";
		
		// Create response log
//		if (logging) {
//			content += "\n\t\t\tresponse.then().log().ifValidationFails();"
//			         + "\n\t\t\tresponse.then().log().ifError();\n";
//		}
		content += "\n\t\t\tresponse.then().log().all();\n";
		
//		if (OAIValidation)
//			content += "\t\t} catch (RuntimeException ex) {\n"
//					+  "\t\t\tSystem.err.println(\"Validation results: \" + ex.getMessage());\n"
//					+  "\t\t\tfail(\"Validation failed\");\n"
//					+	"\t\t}\n";
		
		//content += "\n";
		
		return content;
	}
	
	
//	private String generateResponseValidation(TestCase t) {
//		String content = "";
//		String expectedStatusCode = null;
//		boolean thereIsDefault = false;
//
//		// Get status code of the expected response
//		for (Entry<String, Response> response: t.getExpectedOutputs().entrySet()) {
//			if (response.getValue().equals(t.getExpectedSuccessfulOutput())) {
//				expectedStatusCode = response.getKey();
//			}
//			// If there is a default response, use it if the expected status code is not found
//			if (response.getKey().equals("default")) {
//				thereIsDefault = true;
//			}
//		}
//
//		if (expectedStatusCode == null && !thereIsDefault) {
//			// Default expected status code to 200
//			expectedStatusCode = "200";
//		}
//
//		// Assert status code only if it was found among possible status codes. Otherwise, only JSON structure will be validated
//		//TODO: Improve oracle of status code
////		if (expectedStatusCode != null) {
////			content = "\t\t\tresponse.then().statusCode("
////					+ expectedStatusCode
////					+ ");\n\n";
////		}
////		content = "\t\t\tassertTrue(\"Received status 500. Server error found.\", response.statusCode() < 500);\n";
//
//		return content;
//
//		/*String content = "\t\tswitch(response.getStatusCode()) {\n";
//		boolean hasDefaultCase = false;
//
//		for(Entry<String, Response> response: t.getExpectedOutputs().entrySet()) {
//
//			// Default response
//			if (response.getKey().equals("default")) {
//				content += "\t\t\tdefault:\n";
//				hasDefaultCase = true;
//			} else		// Specific HTTP code
//				content += "\t\tcase " + response.getKey() + ":\n";
//
//
//				content += "\t\t\tresponse.then().contentType(\"" + t.getOutputFormat() + "\");\n";
//
//				//TODO: JSON validation
//				content += "\t\t\tbreak;\n";
//		}
//
//		if (!hasDefaultCase)
//			content += "\t\tdefault: \n"
//					+ "\t\t\tSystem.err.println(\"Unexpected HTTP code: \" + response.getStatusCode());\n"
//					+ "\t\t\tfail();\n"
//					+ "\t\t\tbreak;\n";
//
//		// Close switch sentence
//		content += "\t\t}\n";
//
//		return content;*/
//	}

	private String generatePostResponseValidation(TestCase t) {
		
		String content = "\t\t\tSystem.out.println(\"Test passed.\");\n";

		if (t.getBodyParameter() != null) {
			content += "\t\t} catch (IOException e) {\n"
					+  "\t\t\te.printStackTrace();\n";
		}

		return content;
	}

	private String generateTryBlockEnd() {
		return "\t\t} catch (RuntimeException ex) {\n"
				+  "\t\t\tSystem.err.println(ex.getMessage());\n"
				+  "\t\t\tfail(ex.getMessage());\n"
				+  "\t\t}";
	}
		
	private void saveToFile(String path, String className, String contentFile) {
		FileWriter testClass = null;
		try {
			testClass = new FileWriter(path + "/" + className + ".java");
			testClass.write(contentFile);
			testClass.flush();
			testClass.close();
		} catch(Exception ex) {
			System.err.println("Error writing test file: " + ex.getMessage());
			ex.printStackTrace();
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

	public String getSpecPath() {
		return specPath;
	}

	public void setSpecPath(String specPath) {
		this.specPath = specPath;
	}

	public String getTestFilePath() {
		return testFilePath;
	}

	public void setTestFilePath(String testFilePath) {
		this.testFilePath = testFilePath;
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
}
