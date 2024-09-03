package es.us.isa.restest.runners;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.*;
import es.us.isa.restest.main.CreateTestConf;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.*;
import es.us.isa.restest.writers.IWriter;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.FileManager.deleteDir;

/**
 * This class load the properties files and generate the main objects for test case generation, execution and reporting.
 */
public class RESTestLoader {

	static String userPropertiesFilePath;				// Path to user properties file (if any). If not provided, default values will be used from global property file (config.properties).

	Integer numTestCases; 								// Number of test cases per operation
	String OAISpecPath; 								// Path to OAS specification file
	OpenAPISpecification spec; 							// OAS specification
	String confPath; 									// Path to test configuration file
	String targetDirJava; 								// Directory where tests will be generated.
	String packageName; 								// Package name used on the generated test cases.
	String experimentName; 								// Used as identifier for folders, etc.
	String testClassName; 								// Name prefix of the class to be generated
	Boolean enableInputCoverage; 						// Set to 'true' for generating input coverage report.
	Boolean enableOutputCoverage; 						// Set to 'true' for generating output coverage report.
	Boolean enableCSVStats; 							// Set to 'true' for generating statistics in a CSV file.
	Boolean deletePreviousResults; 						// Set to 'true' to delete previous CSVs and Allure reports.
	Float faultyRatio; 									// Percentage of faulty test cases to generate. Defaults to 0.1
	Integer totalNumTestCases; 							// Total number of test cases to be generated.
	Integer timeDelay; 									// Delay between requests in seconds (-1 for no delay)
	String generator; 									// Generator (RT: Random testing, CBT:Constraint-based testing...)
	Boolean logToFile;									// If 'true', log messages will be printed to external files
	Boolean executeTestCases;							// If 'false', test cases will be generated but not executed
	Boolean allureReports;								// If 'true', Allure reports will be generated
	String allureResultsPath;							// Path to Allure results
	String allureReportsPath;							// Path to Allure reports
	Boolean checkTestCases;								// If 'true', test cases will be checked with OASValidator before executing them
	String proxy;										// Proxy to use for all requests in format host:port

	// For Constraint-based testing and AR Testing:
	Float faultyDependencyRatio; 						// Percentage of faulty test cases due to dependencies to generate.
	Integer reloadInputDataEvery; 						// Number of requests using the same randomly generated input data
	Integer inputDataMaxValues; 						// Number of values used for each parameter when reloading input data

	// For AR Testing only:
	String similarityMetric;							// The algorithm to measure the similarity between test cases
	Integer numberCandidates;							// Number of candidate test cases per AR iteration

	// Logger
	Logger logger = LogManager.getLogger(RESTestLoader.class.getName());

	public RESTestLoader (String userPropertiesFilePath) {
		this.userPropertiesFilePath = userPropertiesFilePath;
		readProperties();
	}

	public RESTestLoader(String userPropertiesFilePath, boolean reloadProperties) {
		if (reloadProperties) {
			PropertyManager.setUserPropertiesFilePath(null);
		}
		this.userPropertiesFilePath = userPropertiesFilePath;
		readProperties();
	}

	public RESTestLoader() {
		readProperties();
	}

	// Create a test case generator
	public AbstractTestCaseGenerator createGenerator() throws RESTestException {

		// Load configuration
		TestConfigurationObject conf = loadConfiguration(confPath, spec);

		// Create generator
		AbstractTestCaseGenerator gen = null;

		switch (generator) {
		case "FT":
			gen = new FuzzingTestCaseGenerator(spec, conf, numTestCases);
			break;
		case "RT":
			gen = new RandomTestCaseGenerator(spec, conf, numTestCases);
			((RandomTestCaseGenerator) gen).setFaultyRatio(faultyRatio);
			break;
		case "CBT":
			gen = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
			((ConstraintBasedTestCaseGenerator) gen).setFaultyDependencyRatio(faultyDependencyRatio);
			((ConstraintBasedTestCaseGenerator) gen).setInputDataMaxValues(inputDataMaxValues);
			((ConstraintBasedTestCaseGenerator) gen).setReloadInputDataEvery(reloadInputDataEvery);
			gen.setFaultyRatio(faultyRatio);
			break;
		case "ART":
			gen = new ARTestCaseGenerator(spec, conf, numTestCases);
			((ARTestCaseGenerator) gen).setFaultyDependencyRatio(faultyDependencyRatio);
			((ARTestCaseGenerator) gen).setInputDataMaxValues(inputDataMaxValues);
			((ARTestCaseGenerator) gen).setReloadInputDataEvery(reloadInputDataEvery);
			((ARTestCaseGenerator) gen).setDiversity(similarityMetric);
			((ARTestCaseGenerator) gen).setNumberOfCandidates(numberCandidates);
			gen.setFaultyRatio(faultyRatio);
			break;
		default:
			throw new RESTestException("Property 'generator' must be one of 'FT', 'RT', 'CBT' or 'ART'");
		}

		gen.setCheckTestCases(checkTestCases);

		return gen;
	}

	// Create RESTAssured writer
	public IWriter createWriter() {
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, confPath, targetDirJava, testClassName, packageName,
				basePath, logToFile);
		writer.setAllureReport(allureReports);
		writer.setEnableStats(enableCSVStats);
		writer.setEnableOutputCoverage(enableOutputCoverage);
		writer.setAPIName(experimentName);
		writer.setTestId(experimentName);
		writer.setProxy(proxy);
		return writer;
	}

	// Create Allure report manager
	public AllureReportManager createAllureReportManager() {
		AllureReportManager arm = null;
		if(executeTestCases) {
			String allureResultsDir = allureResultsPath + "/" + experimentName;
			String allureReportDir = allureReportsPath + "/" + experimentName;

			// Delete previous results (if any)
			if (deletePreviousResults) {
				deleteDir(allureResultsDir);
				deleteDir(allureReportDir);
			}

			//Find auth property names (if any)
			List<String> authProperties = AllureAuthManager.findAuthProperties(spec, confPath);

			arm = new AllureReportManager(allureResultsDir, allureReportDir, authProperties);
			arm.setEnvironmentProperties(userPropertiesFilePath);
			arm.setHistoryTrend(true);
		}
		return arm;
	}

	// Create statistics report manager
	public StatsReportManager createStatsReportManager() {
		String testDataDir = readProperty("data.tests.dir") + "/" + experimentName;
		String coverageDataDir = readProperty("data.coverage.dir") + "/" + experimentName;

		// Delete previous results (if any)
		if (deletePreviousResults) {
			deleteDir(testDataDir);
			deleteDir(coverageDataDir);
		}

    // Create target directories if they don't exist
		createDir(testDataDir);
		createDir(coverageDataDir);

		CoverageMeter coverageMeter = enableInputCoverage || enableOutputCoverage ? new CoverageMeter(new CoverageGatherer(spec)) : null;

		return new StatsReportManager(testDataDir, coverageDataDir, enableCSVStats, enableInputCoverage,
					enableOutputCoverage, coverageMeter);
	}

	// Read the parameter values from the .properties file. If the value is not found, the system looks for it in the global .properties file (config.properties)
	public void readProperties() {

		logToFile = Boolean.parseBoolean(readProperty("logToFile"));
		if(logToFile) {
			setUpLogger();
		}

		logger.info("Loading configuration parameter values");
		
		generator = readProperty("generator");
		logger.info("Generator: {}", generator);
		
		OAISpecPath = readProperty("oas.path");
		logger.info("OAS path: {}", OAISpecPath);

		// Load OAS specification
		spec = new OpenAPISpecification(OAISpecPath);
		
		confPath = readProperty("conf.path");
		logger.info("Test configuration path: {}", confPath);
		
		targetDirJava = readProperty("test.target.dir");
		logger.info("Target dir for test classes: {}", targetDirJava);
		
		experimentName = readProperty("experiment.name");
		logger.info("Experiment name: {}", experimentName);

		if (readProperty("experiment.execute") != null) {
			executeTestCases = Boolean.parseBoolean(readProperty("experiment.execute"));
		}
		logger.info("Experiment execution: {}", executeTestCases);

		if (readProperty("allure.report") != null) {
			allureReports = Boolean.parseBoolean(readProperty("allure.report"));
		}
		logger.info("Allure reports: {}", allureReports);

		allureResultsPath = readProperty("allure.results.dir");
		logger.info("Allure results path: {}", allureResultsPath);

		allureReportsPath = readProperty("allure.report.dir");
		logger.info("Allure reports path: {}", allureReportsPath);

		if (readProperty("proxy") != null) {
			proxy = readProperty("proxy");
			if ("null".equals(proxy) || proxy.split(":").length != 2)
				proxy = null;
			else
				setProxy();
		}
		logger.info("Proxy: {}", proxy);

		if (readProperty("testcases.check") != null)
			checkTestCases = Boolean.parseBoolean(readProperty("testcases.check"));
		logger.info("Check test cases: {}", checkTestCases);
		
		testClassName = readProperty("testclass.name");
		logger.info("Test class name: {}", testClassName);

		packageName = readProperty("test.target.package");
		logger.info("Package name: {}", packageName);

		if (readProperty("testsperoperation") != null)
			numTestCases = Integer.parseInt(readProperty("testsperoperation"));
		logger.info("Number of test cases per operation: {}", numTestCases);

		if (readProperty("numtotaltestcases") != null)
			totalNumTestCases = Integer.parseInt(readProperty("numtotaltestcases"));
		logger.info("Max number of test cases: {}", totalNumTestCases);

		if (readProperty("delay") != null)
			timeDelay = Integer.parseInt(readProperty("delay"));
		logger.info("Time delay: {}", timeDelay);

		if (readProperty("reloadinputdataevery") != null)
			reloadInputDataEvery = Integer.parseInt(readProperty("reloadinputdataevery"));
		logger.info("Input data reloading  (CBT): {}", reloadInputDataEvery);

		if (readProperty("inputdatamaxvalues") != null)
			inputDataMaxValues = Integer.parseInt(readProperty("inputdatamaxvalues"));
		logger.info("Max input test data (CBT): {}", inputDataMaxValues);

		if (readProperty("coverage.input") != null)
			enableInputCoverage = Boolean.parseBoolean(readProperty("coverage.input"));
		logger.info("Input coverage: {}", enableInputCoverage);

		if (readProperty("coverage.output") != null)
			enableOutputCoverage = Boolean.parseBoolean(readProperty("coverage.output"));
		logger.info("Output coverage: {}", enableOutputCoverage);

		if (readProperty("stats.csv") != null)
			enableCSVStats = Boolean.parseBoolean(readProperty("stats.csv"));
		logger.info("CSV statistics: {}", enableCSVStats);

		if (readProperty("deletepreviousresults") != null)
			deletePreviousResults = Boolean.parseBoolean(readProperty("deletepreviousresults"));
		logger.info("Delete previous results: {}", deletePreviousResults);

		if (readProperty("similarity.metric") != null)
			similarityMetric = readProperty("similarity.metric");
		logger.info("Similarity metric: {}", similarityMetric);

		if (readProperty("art.number.candidates") != null)
			numberCandidates = Integer.parseInt(readProperty("art.number.candidates"));
		logger.info("Number of candidates: {}", numberCandidates);

		if (readProperty("faulty.ratio") != null)
			faultyRatio = Float.parseFloat(readProperty("faulty.ratio"));
		logger.info("Faulty ratio: {}", faultyRatio);

		if (readProperty("faulty.dependency.ratio") != null)
			faultyDependencyRatio = Float.parseFloat(readProperty("faulty.dependency.ratio"));
		logger.info("Faulty dependency ratio: {}", faultyDependencyRatio);

	}


	// Read the parameter values from the user property file (if provided). If the value is not found, look for it in the global .properties file (config.properties)
	public String readProperty(String propertyName) {
		
		// Read property from user property file (if provided)
		String value = PropertyManager.readProperty(userPropertiesFilePath, propertyName);
		
		// If null, read property from global property file (config.properties)
		if (value ==null)
			value = PropertyManager.readProperty(propertyName);
		
		return value;
	}

	public TestConfigurationObject getTestConfigurationObject(){
		return loadConfiguration(confPath, spec);
	}



	// Set up logger
	private void setUpLogger() {
		// Recreate log directory if necessary
		if (Boolean.parseBoolean(readProperty("deletepreviousresults"))) {
			String logDataDir = readProperty("data.log.dir") + "/" + readProperty("experiment.name");
			deleteDir(logDataDir);
			createDir(logDataDir);
		}

		// Attach stdout and stderr to logger
		System.setOut(new PrintStream(new LoggerStream(LogManager.getLogger("stdout"), Level.INFO, System.out)));
		System.setErr(new PrintStream(new LoggerStream(LogManager.getLogger("stderr"), Level.ERROR, System.err)));

		// Configure regular logger
		String logPath = readProperty("data.log.dir") + "/" + readProperty("experiment.name") + "/" + readProperty("data.log.file");

		System.setProperty("logFilename", logPath);
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		File file = new File("src/main/resources/log4j2-logToFile.properties");
		ctx.setConfigLocation(file.toURI());
		ctx.reconfigure();
	}

	// Set proxy
	private void setProxy() {
		System.setProperty("http.proxyHost", proxy.split(":")[0]);
		System.setProperty("http.proxyPort", proxy.split(":")[1]);
		System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
		System.setProperty("https.proxyHost", proxy.split(":")[0]);
		System.setProperty("https.proxyPort", proxy.split(":")[1]);
		System.setProperty("https.nonProxyHosts", "localhost|127.0.0.1");
	}

	public String getTargetDirJava() {
		return targetDirJava;
	}

	public String getExperimentName(){ return experimentName; }

	public void setExperimentName(String experimentName) {
		this.experimentName = experimentName;
	}

	public String getAllureReportsPath() {
		return allureReportsPath;
	}

	public String getTestClassName() {
		return testClassName;
	}

	public void setTestClassName(String testClassName) {
		this.testClassName = testClassName;
	}
}
