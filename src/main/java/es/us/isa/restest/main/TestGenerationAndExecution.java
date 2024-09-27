package es.us.isa.restest.main;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.*;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.ARTEInputGenerator.szEndpoint;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.Timer.TestStep.ALL;

/*
 * This class shows the basic workflow of test case generation -> test case execution -> test reporting
 */
public class TestGenerationAndExecution {

	// Properties file with configuration settings
	private static String propertiesFilePath = "src/test/resources/GitHub/props.properties";

	private static List<String> argsList;								// List containing args

	private static Integer numTestCases; 								// Number of test cases per operation
	private static String OAISpecPath; 									// Path to OAS specification file
	private static OpenAPISpecification spec; 							// OAS specification
	private static String confPath; 									// Path to test configuration file
	private static TestConfigurationObject testConf;					// Test configuration object
	private static String targetDirJava; 								// Directory where tests will be generated.
	private static String packageName; 									// Package name.
	private static String experimentName; 								// Used as identifier for folders, etc.
	private static String testClassName; 								// Name prefix of the class to be generated
	private static Boolean enableInputCoverage; 						// Set to 'true' if you want the input coverage report.
	private static Boolean enableOutputCoverage; 						// Set to 'true' if you want the output coverage report.
	private static Boolean enableCSVStats; 								// Set to 'true' if you want statistics in a CSV file.
	private static Boolean deletePreviousResults; 						// Set to 'true' if you want previous CSVs and Allure reports.
	private static Float faultyRatio; 									// Percentage of faulty test cases to generate. Defaults to 0.1
	private static Integer totalNumTestCases; 							// Total number of test cases to be generated (-1 for infinite loop)
	private static Integer timeDelay; 									// Delay between requests in seconds (-1 for no delay)
	private static String generatorType; 								// Generator (RT: Random testing, CBT:Constraint-based testing)
	private static Boolean logToFile;									// If 'true', log messages will be printed to external files
	private static boolean executeTestCases;							// If 'false', test cases will be generated but not executed
	private static boolean allureReports;								// If 'true', Allure reports will be generated
	private static boolean checkTestCases;								// If 'true', test cases will be checked with OASValidator before executing them
	private static String proxy;										// Proxy to use for all requests in format host:port

	// For Constraint-based testing and AR Testing:
	private static Float faultyDependencyRatio; 						// Percentage of faulty test cases due to dependencies to generate.
	private static Integer reloadInputDataEvery; 						// Number of requests using the same randomly generated input data
	private static Integer inputDataMaxValues; 							// Number of values used for each parameter when reloading input data

	// For AR Testing only:
	private static String similarityMetric;								// The algorithm to measure the similarity between test cases
	private static Integer numberCandidates;							// Number of candidate test cases per AR iteration

	// For Machine Learning testing:
	private static Integer mlTrainingRequestsPerIteration;				// While Active Learning: Number of requests per iteration
	private static Integer mlTrainingMaxIterationsNotLearning;			// While Active Learning: Max allowed number of iterations without learning
	private static Float mlTrainingPrecisionThreshold;					// While Active Learning: Desired precision to be achieved by the ML model
	private static Integer mlCandidatesRatio;							// When generating N desired test cases with ML, generate N*mlCandidatesRatio potential test cases
	private static Float mlResamplingRatio;								// TODO
	private static Boolean mlInitialData;								// Whether to use initial data to train the ML model or not (stored in /target/test-data/experimentName)
	private static String mlLearningStrategy;							// Set to "active" for active learning or "random" for random learning

	// ARTE
	private static Boolean learnRegex;									// Set to 'true' if you want RESTest to automatically generate Regular expressions that filter the semantically generated input data
	private static boolean secondPredicateSearch;
	private static int maxNumberOfPredicates;                			// MaxNumberOfPredicates = AdditionalPredicates + 1
	private static int minimumValidAndInvalidValues;
	private static String metricToUse;
	private static Double minimumValueOfMetric;
	private static int maxNumberOfTriesToGenerateRegularExpression;

	private static AbstractTestCaseGenerator generator;
	private static IWriter writer;
	private static StatsReportManager statsReportManager;
	private static AllureReportManager reportManager;
	private static RESTestRunner runner;

	private static Logger logger = LogManager.getLogger(TestGenerationAndExecution.class.getName());

	public static void main(String[] args) throws RESTestException {

		Timer.startCounting(ALL);

		// ONLY FOR LOCAL COPY OF DBPEDIA
		if (szEndpoint.contains("localhost") || szEndpoint.contains("127.0.0.1"))
			System.setProperty("http.maxConnections", "10000");

		// Read .properties file path. This file contains the configuration parameters for the generation
		if (args.length > 0)
			propertiesFilePath = args[0];

		// Populate configuration parameters, either from arguments or from .properties file
		argsList = Arrays.asList(args);
		readParameterValues();

		// ML configuration
		if (generatorType.equals("MLT") && mlInitialData) {
			deletePreviousResults = false;
		}

		// Set proxy globally, if specified
		if (proxy != null) {
			System.setProperty("http.proxyHost", proxy.split(":")[0]);
			System.setProperty("http.proxyPort", proxy.split(":")[1]);
			System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
			System.setProperty("https.proxyHost", proxy.split(":")[0]);
			System.setProperty("https.proxyPort", proxy.split(":")[1]);
			System.setProperty("https.nonProxyHosts", "localhost|127.0.0.1");
		}

		// Create target directory if it does not exists
		createDir(targetDirJava);

		// Load specification
		spec = new OpenAPISpecification(OAISpecPath);

		// Load test configuration (except for FT, which cannot use a test configuration)
		if (!generatorType.equals("FT"))
			testConf = loadConfiguration(confPath, spec);

		// RESTest runner
		generator = createGenerator(); // Test case generator
		writer = createWriter(); // Test case writer
		statsReportManager = createStatsReportManager(); // Stats reporter
		reportManager = createAllureReportManager(); // Allure test case reporter

		runner = new RESTestRunner(testClassName, targetDirJava, packageName, learnRegex,
				secondPredicateSearch, spec, confPath, generator, writer,
				reportManager, statsReportManager);

		runner.setExecuteTestCases(executeTestCases);
		runner.setAllureReport(allureReports);


		// ML configuration
		if (generatorType.equals("MLT") && !mlInitialData) {
			mlLearning();
		}

		// Main loop
		int iteration = 1;
		while (totalNumTestCases == -1 || runner.getNumTestCases() < totalNumTestCases) {

			//	Limit the number of tests to execute in this iteration to the number of remaining tests
			runner.getGenerator().setNumberOfTests(Math.min(runner.getGenerator().getNumberOfTests(), totalNumTestCases-runner.getNumTestCases()));

			// Execute the iteration
			testIteration();

			logger.info("Iteration {}. {} test cases generated.", iteration, runner.getNumTestCases());

			// Introduce optional delay
			delay(timeDelay);

			iteration++;
		}

		Timer.stopCounting(ALL);

		generateTimeReport(iteration-1);
	}

	private static Double trainMlModel() {

		Response response = RestAssured.given()
				.queryParam("trainingPath", readParameterValue("data.tests.dir") + "/" + experimentName)
				.queryParam("resamplingRatio", mlResamplingRatio.toString())
				.queryParam("propertiesPath", propertiesFilePath)
				.get("http://localhost:8000/train")
				.andReturn();

			ObjectMapper mapper = new ObjectMapper();
			Map<String,Object> result = null;
			try {
				result = mapper.readValue(response.body().print(), Map.class);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}

			// Float score = response.body();
			Double score = (Double) result.get("score");

			logger.info("The score is " + score.toString() + ".");

			// String mlTrainProcessOutput = runCommand(mlTrainCommand, new String[]{propertiesFilePath, Float.toString(mlResamplingRatio)});
			// Float score = Float.parseFloat(mlTrainProcessOutput);

			return score;
		// } catch (RESTestException e) {
		// 	logger.warn("Error when training the ML model. The model will be retrained in the next iteration.");
		// 	return 0f;
	}

	private static void testIteration() throws RESTestException {
		// Generate unique test class name to avoid the same class being loaded everytime
		String id = IDGenerator.generateTimeId();
		String className = testClassName + "_" + id;
		((RESTAssuredWriter) writer).setClassName(className);
		((RESTAssuredWriter) writer).setTestId(id);
		runner.setTestClassName(className);
		runner.setTestId(id);

		// Test case generation + execution + test report generation
		runner.run();
	}

	private static void mlLearning() throws RESTestException {
		double precision = 0f;
		double maxPrecision = precision;
		int iterationsWithoutLearning = 0;

		if (mlInitialData) {
			precision = trainMlModel();
			maxPrecision = precision;
		}

		// Random or Active learning
		if (mlLearningStrategy.equals("random")) {
			RandomTestCaseGenerator randomTestGenerator = new RandomTestCaseGenerator(spec, testConf, mlTrainingRequestsPerIteration);

			// Use randomTestGenerator with the Runner
			runner.setGenerator(randomTestGenerator);
		} else {
			ALDrivenTestCaseGenerator ALTestGenerator = new ALDrivenTestCaseGenerator(spec, testConf, mlTrainingRequestsPerIteration);
			ALTestGenerator.setPropertiesFilePath(propertiesFilePath);
			ALTestGenerator.setMlTrainingRequestsPerIteration(mlTrainingRequestsPerIteration);
			ALTestGenerator.setMlTrainingMaxIterationsNotLearning(mlTrainingMaxIterationsNotLearning);
			ALTestGenerator.setMlCandidatesRatio(mlCandidatesRatio);
			ALTestGenerator.setExperimentFolder(readParameterValue("data.tests.dir") + "/" + experimentName);
			ALTestGenerator.setMlResamplingRatio(mlResamplingRatio);
			ALTestGenerator.setFaultyRatio(faultyRatio);

			// Use ALTestGenerator with the Runner
			runner.setGenerator(ALTestGenerator);
		}

		while (precision < mlTrainingPrecisionThreshold && iterationsWithoutLearning < mlTrainingMaxIterationsNotLearning && runner.getNumTestCases() < totalNumTestCases) {
			testIteration();
			precision = trainMlModel();
			if (precision > maxPrecision) {
				maxPrecision = precision;
				iterationsWithoutLearning = 0;
			} else
				iterationsWithoutLearning++;
		}

		// Reconfigure original generator with runner
		runner.setGenerator(generator);
	}

	// Create a test case generator
	private static AbstractTestCaseGenerator createGenerator() throws RESTestException {
		if(generatorType.equals("FT") && confPath == null) {
			logger.info("No testConf specified. Generating one");
			String[] args = {OAISpecPath};
			CreateTestConf.main(args);

			String specDir = OAISpecPath.substring(0, OAISpecPath.lastIndexOf('/'));
			confPath = specDir + "/testConf.yaml";
			logger.info("Created testConf in '{}'", confPath);

			testConf = loadConfiguration(confPath, spec);
		}

		// Create generator
		AbstractTestCaseGenerator gen = null;

		switch (generatorType) {
			case "FT":
				gen = new FuzzingTestCaseGenerator(spec, testConf, numTestCases);
				break;
			case "RT":
				gen = new RandomTestCaseGenerator(spec, testConf, numTestCases);
				((RandomTestCaseGenerator) gen).setFaultyRatio(faultyRatio);
				break;
			case "CBT":
				gen = new ConstraintBasedTestCaseGenerator(spec, testConf, numTestCases);
				((ConstraintBasedTestCaseGenerator) gen).setFaultyDependencyRatio(faultyDependencyRatio);
				((ConstraintBasedTestCaseGenerator) gen).setInputDataMaxValues(inputDataMaxValues);
				((ConstraintBasedTestCaseGenerator) gen).setReloadInputDataEvery(reloadInputDataEvery);
				gen.setFaultyRatio(faultyRatio);
				break;
			case "ART":
				gen = new ARTestCaseGenerator(spec, testConf, numTestCases);
				((ARTestCaseGenerator) gen).setFaultyDependencyRatio(faultyDependencyRatio);
				((ARTestCaseGenerator) gen).setInputDataMaxValues(inputDataMaxValues);
				((ARTestCaseGenerator) gen).setReloadInputDataEvery(reloadInputDataEvery);
				((ARTestCaseGenerator) gen).setDiversity(similarityMetric);
				((ARTestCaseGenerator) gen).setNumberOfCandidates(numberCandidates);
				gen.setFaultyRatio(faultyRatio);
				break;
			case "MLT":
				gen = new MLDrivenTestCaseGenerator(spec, testConf, numTestCases);
				((MLDrivenTestCaseGenerator) gen).setMlCandidatesRatio(mlCandidatesRatio);
				((MLDrivenTestCaseGenerator) gen).setPropertiesFilePath(propertiesFilePath);
				((MLDrivenTestCaseGenerator) gen).setExperimentFolder(readParameterValue("data.tests.dir") + "/" + experimentName);
				((MLDrivenTestCaseGenerator) gen).setMlResamplingRatio(mlResamplingRatio);
				gen.setFaultyRatio(faultyRatio);
				break;
			default:
				throw new RESTestException("Property 'generator' must be one of 'FT', 'RT', 'CBT', 'ART' or 'MLT'");
		}

		gen.setCheckTestCases(checkTestCases);

		return gen;
	}

	// Create a writer for RESTAssured
	private static IWriter createWriter() {
		String basePath = spec.getSpecification().getServers().get(0).getUrl();
		RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, confPath, targetDirJava, testClassName, packageName,
				basePath, logToFile);
		writer.setLogging(true);
		writer.setAllureReport(true);
		writer.setEnableStats(enableCSVStats);
		writer.setEnableOutputCoverage(enableOutputCoverage);
		writer.setAPIName(experimentName);
		writer.setProxy(proxy);
		return writer;
	}

	// Create an Allure report manager
	private static AllureReportManager createAllureReportManager() {
		AllureReportManager arm = null;
		if(executeTestCases) {
			String allureResultsDir = readParameterValue("allure.results.dir") + "/" + experimentName;
			String allureReportDir = readParameterValue("allure.report.dir") + "/" + experimentName;

			// Delete previous results (if any)
			if (deletePreviousResults) {
				deleteDir(allureResultsDir);
				deleteDir(allureReportDir);
			}

			//Find auth property names (if any)
			List<String> authProperties = AllureAuthManager.findAuthProperties(spec, confPath);

			arm = new AllureReportManager(allureResultsDir, allureReportDir, authProperties);
			arm.setEnvironmentProperties(propertiesFilePath);
			arm.setHistoryTrend(true);
		}
		return arm;
	}

	// Create a statistics report manager
	private static StatsReportManager createStatsReportManager() {
		String testDataDir = readParameterValue("data.tests.dir") + "/" + experimentName;
		String coverageDataDir = readParameterValue("data.coverage.dir") + "/" + experimentName;

		// Delete previous results (if any)
		if (deletePreviousResults) {
			deleteDir(testDataDir);
			deleteDir(coverageDataDir);

			// Recreate directories
			createDir(testDataDir);
			createDir(coverageDataDir);
		}

		CoverageMeter coverageMeter = enableInputCoverage || enableOutputCoverage ? new CoverageMeter(new CoverageGatherer(spec)) : null;

		return new StatsReportManager(testDataDir, coverageDataDir, enableCSVStats, enableInputCoverage,
					enableOutputCoverage, coverageMeter, secondPredicateSearch, maxNumberOfPredicates,
					minimumValidAndInvalidValues, metricToUse, minimumValueOfMetric,
					maxNumberOfTriesToGenerateRegularExpression);
	}

	private static void generateTimeReport(Integer iterations) {
		String timePath = readParameterValue("data.tests.dir") + "/" + experimentName + "/" + readParameterValue("data.tests.time");
		try {
			Timer.exportToCSV(timePath, iterations);
		} catch (RuntimeException e) {
			logger.error("The time report cannot be generated. Stack trace:");
			logger.error(e.getMessage());
		}
		logger.info("Time report generated.");
	}

	/*
	 * Stop the execution n seconds
	 */
	private static void delay(Integer time) {
		if (time == -1)
			return;
		try {
			logger.info("Introducing delay of {} seconds", time);
			TimeUnit.SECONDS.sleep(time);
		} catch (InterruptedException e) {
			logger.error("Error introducing delay", e);
			logger.error(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	// Read the parameter values from the .properties file. If the value is not found, the system looks for it in the global .properties file (config.properties)
	private static void readParameterValues() {

		logToFile = Boolean.parseBoolean(readParameterValue("logToFile"));
		if(logToFile) {
			setUpLogger();
		}

		logger.info("Loading configuration parameter values");
		
		generatorType = readParameterValue("generator");
		logger.info("Generator: {}", generatorType);
		
		OAISpecPath = readParameterValue("oas.path");
		logger.info("OAS path: {}", OAISpecPath);
		
		confPath = readParameterValue("conf.path");
		logger.info("Test configuration path: {}", confPath);
		
		targetDirJava = readParameterValue("test.target.dir");
		logger.info("Target dir for test classes: {}", targetDirJava);
		
		experimentName = readParameterValue("experiment.name");
		logger.info("Experiment name: {}", experimentName);
		packageName = experimentName;

		if (readParameterValue("experiment.execute") != null) {
			executeTestCases = Boolean.parseBoolean(readParameterValue("experiment.execute"));
		}
		logger.info("Experiment execution: {}", executeTestCases);

		if (readParameterValue("allure.report") != null) {
			allureReports = Boolean.parseBoolean(readParameterValue("allure.report"));
		}
		logger.info("Allure reports: {}", allureReports);

		if (readParameterValue("proxy") != null) {
			proxy = readParameterValue("proxy");
			if ("null".equals(proxy) || proxy.split(":").length != 2)
				proxy = null;
		}
		logger.info("Proxy: {}", proxy);

		if (readParameterValue("testcases.check") != null)
			checkTestCases = Boolean.parseBoolean(readParameterValue("testcases.check"));
		logger.info("Check test cases: {}", checkTestCases);

		testClassName = readParameterValue("testclass.name");
		logger.info("Test class name: {}", testClassName);

		if (readParameterValue("testsperoperation") != null)
			numTestCases = Integer.parseInt(readParameterValue("testsperoperation"));
		logger.info("Number of test cases per operation: {}", numTestCases);

		if (readParameterValue("numtotaltestcases") != null)
			totalNumTestCases = Integer.parseInt(readParameterValue("numtotaltestcases"));
		logger.info("Max number of test cases: {}", totalNumTestCases);

		if (readParameterValue("delay") != null)
			timeDelay = Integer.parseInt(readParameterValue("delay"));
		logger.info("Time delay: {}", timeDelay);

		if (readParameterValue("reloadinputdataevery") != null)
			reloadInputDataEvery = Integer.parseInt(readParameterValue("reloadinputdataevery"));
		logger.info("Input data reloading  (CBT): {}", reloadInputDataEvery);

		if (readParameterValue("inputdatamaxvalues") != null)
			inputDataMaxValues = Integer.parseInt(readParameterValue("inputdatamaxvalues"));
		logger.info("Max input test data (CBT): {}", inputDataMaxValues);

		if (readParameterValue("coverage.input") != null)
			enableInputCoverage = Boolean.parseBoolean(readParameterValue("coverage.input"));
		logger.info("Input coverage: {}", enableInputCoverage);

		if (readParameterValue("coverage.output") != null)
			enableOutputCoverage = Boolean.parseBoolean(readParameterValue("coverage.output"));
		logger.info("Output coverage: {}", enableOutputCoverage);

		if (readParameterValue("stats.csv") != null)
			enableCSVStats = Boolean.parseBoolean(readParameterValue("stats.csv"));
		logger.info("CSV statistics: {}", enableCSVStats);

		if (readParameterValue("deletepreviousresults") != null)
			deletePreviousResults = Boolean.parseBoolean(readParameterValue("deletepreviousresults"));
		logger.info("Delete previous results: {}", deletePreviousResults);

		if (readParameterValue("similarity.metric") != null)
			similarityMetric = readParameterValue("similarity.metric");
		logger.info("Similarity metric: {}", similarityMetric);

		if (readParameterValue("art.number.candidates") != null)
			numberCandidates = Integer.parseInt(readParameterValue("art.number.candidates"));
		logger.info("Number of candidates: {}", numberCandidates);

		if (readParameterValue("faulty.ratio") != null)
			faultyRatio = Float.parseFloat(readParameterValue("faulty.ratio"));
		logger.info("Faulty ratio: {}", faultyRatio);

		if (readParameterValue("faulty.dependency.ratio") != null)
			faultyDependencyRatio = Float.parseFloat(readParameterValue("faulty.dependency.ratio"));
		logger.info("Faulty dependency ratio: {}", faultyDependencyRatio);

		// MLT
		if (readParameterValue("ml.training.iteration.requests") != null)
			mlTrainingRequestsPerIteration = Integer.parseInt(readParameterValue("ml.training.iteration.requests"));
		logger.info("ML training requests per iteration: {}", mlTrainingRequestsPerIteration);

		if (readParameterValue("ml.training.max.iterations.not.learning") != null)
			mlTrainingMaxIterationsNotLearning = Integer.parseInt(readParameterValue("ml.training.max.iterations.not.learning"));
		logger.info("ML max iterations not learning: {}", mlTrainingMaxIterationsNotLearning);

		if (readParameterValue("ml.training.precision.threshold") != null)
			mlTrainingPrecisionThreshold = Float.parseFloat(readParameterValue("ml.training.precision.threshold"));
		logger.info("ML training precision threshold: {}", mlTrainingPrecisionThreshold);

		if (readParameterValue("ml.candidates.ratio") != null)
			mlCandidatesRatio = Integer.parseInt(readParameterValue("ml.candidates.ratio"));
		logger.info("ML candidates ratio: {}", mlCandidatesRatio);

		if (readParameterValue("ml.resampling.ratio") != null)
			mlResamplingRatio = Float.parseFloat(readParameterValue("ml.resampling.ratio"));
		logger.info("ML resampling ratio: {}", mlResamplingRatio);

		if (readParameterValue("ml.initialdata") != null)
			mlInitialData = Boolean.parseBoolean(readParameterValue("ml.initialdata"));
		logger.info("ML initial data: {}", mlInitialData);

		if (readParameterValue("ml.learning.strategy") != null)
			mlLearningStrategy = readParameterValue("ml.learning.strategy");
		logger.info("ML learning strategy: {}", mlLearningStrategy);

		// ARTE
		if (readParameterValue("learnRegex") != null)
			learnRegex = Boolean.parseBoolean(readParameterValue("learnRegex"));
		logger.info("Learn Regular expressions: {}", learnRegex);

		if (readParameterValue("secondPredicateSearch") != null)
			secondPredicateSearch = Boolean.parseBoolean(readParameterValue("secondPredicateSearch"));
		logger.info("Second Predicate Search: {}", secondPredicateSearch);

		if (readParameterValue("maxNumberOfPredicates") != null)
			maxNumberOfPredicates = Integer.parseInt(readParameterValue("maxNumberOfPredicates"));
		logger.info("Maximum number of predicates: {}", maxNumberOfPredicates);

		if (readParameterValue("minimumValidAndInvalidValues") != null)
			minimumValidAndInvalidValues = Integer.parseInt(readParameterValue("minimumValidAndInvalidValues"));
		logger.info("Minimum valid and invalid values: {}", minimumValidAndInvalidValues);

		if (readParameterValue("metricToUse") != null)
			metricToUse = readParameterValue("metricToUse");
		logger.info("Metric to use: {}", metricToUse);

		if (readParameterValue("minimumValueOfMetric") != null)
			minimumValueOfMetric = Double.parseDouble(readParameterValue("minimumValueOfMetric"));
		logger.info("Minimum value of metric: {}", minimumValueOfMetric);

		if (readParameterValue("maxNumberOfTriesToGenerateRegularExpression") != null)
			maxNumberOfTriesToGenerateRegularExpression = Integer.parseInt(readParameterValue("maxNumberOfTriesToGenerateRegularExpression"));
		logger.info("Maximum number of tries to generate a regular expression: {}", maxNumberOfTriesToGenerateRegularExpression);
	}

	// Read the parameter value from: 1) CLI; 2) the local .properties file; 3) the global .properties file (config.properties)
	private static String readParameterValue(String propertyName) {

		String value = null;

		if (argsList.contains(propertyName))
			value = argsList.get(argsList.indexOf(propertyName) + 1);
		else if (argsList.stream().anyMatch(arg -> arg.matches("^" + propertyName + "=.*")))
			value = argsList.stream().filter(arg -> arg.matches("^" + propertyName + "=.*")).findFirst().get().split("=")[1];
		else if (PropertyManager.readProperty(propertiesFilePath, propertyName) != null) // Read value from local .properties file
			value = PropertyManager.readProperty(propertiesFilePath, propertyName);
		else if (PropertyManager.readProperty(propertyName) != null) // Read value from global .properties file
			value = PropertyManager.readProperty(propertyName);

		return value;
	}


	public static TestConfigurationObject getTestConfigurationObject(){
		return testConf;
	}

	public static String getExperimentName(){ return experimentName; }

	private static void setUpLogger() {
		// Recreate log directory if necessary
		if (Boolean.parseBoolean(readParameterValue("deletepreviousresults"))) {
			String logDataDir = readParameterValue("data.log.dir") + "/" + readParameterValue("experiment.name");
			deleteDir(logDataDir);
			createDir(logDataDir);
		}

		// Attach stdout and stderr to logger
		System.setOut(new PrintStream(new LoggerStream(LogManager.getLogger("stdout"), Level.INFO, System.out)));
		System.setErr(new PrintStream(new LoggerStream(LogManager.getLogger("stderr"), Level.ERROR, System.err)));

		// Configure regular logger
		String logPath = readParameterValue("data.log.dir") + "/" + readParameterValue("experiment.name") + "/" + readParameterValue("data.log.file");

		System.setProperty("logFilename", logPath);
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		File file = new File("src/main/resources/log4j2-logToFile.properties");
		ctx.setConfigLocation(file.toURI());
		ctx.reconfigure();
	}

}
