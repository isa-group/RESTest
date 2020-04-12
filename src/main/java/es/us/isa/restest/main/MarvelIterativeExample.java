//package es.us.isa.restest.main;
//
//import es.us.isa.restest.configuration.TestConfigurationIO;
//import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
//import es.us.isa.restest.coverage.CoverageGatherer;
//import es.us.isa.restest.coverage.CoverageMeter;
//import es.us.isa.restest.generators.AbstractTestCaseGenerator;
//import es.us.isa.restest.generators.RandomTestCaseGenerator;
//import es.us.isa.restest.runners.RESTestRunner;
//import es.us.isa.restest.specification.OpenAPISpecification;
//import es.us.isa.restest.testcases.writers.IWriter;
//import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
//import es.us.isa.restest.util.AllureReportManager;
//import es.us.isa.restest.util.CSVReportManager;
//import es.us.isa.restest.util.IDGenerator;
//import es.us.isa.restest.util.PropertyManager;
//
//import java.util.concurrent.TimeUnit;
//
//import static es.us.isa.restest.util.FileManager.createDir;
//import static es.us.isa.restest.util.FileManager.deleteDir;
//
///**
// * Iterative test scenario example: Tests are generated, and executed incrementally in different iterations updating the test report at each step.
// * An optional time delay can be set between every two iterations.
// * Classes are named uniquely to avoid the same class being loaded and executed everytime.
// * @author Sergio Segura
// *
// */
//public class MarvelIterativeExample {
//
//	private static int numTestCases = 2;												// Number of test cases per operation
//	private static String OAISpecPath = "src/test/resources/Marvel/swagger.yaml";		// Path to OAS specification file
//	private static String confPath = "src/test/resources/Marvel/testConf.yaml";		// Path to test configuration file
//	private static String targetDirJava = "src/generation/java/marvel";				// Directory where tests will be generated.
////	private static String targetDirTestData = "target/test-data";						// Directory where tests will be exported to CSV.
////	private static String targetDirCoverageData = "target/coverage-data";				// Directory where coverage will be exported to CSV.
//	private static String packageName = "marvel";										// Package name.
//	private static String APIName = "marvel";											// API name
//	private static String testClassName = "MarvelTest";								// Name prefix of the class to be generated
//	private static OpenAPISpecification spec;
//	private static int totalNumTestCases = -1;											// Total number of test cases to be generated
//	private static int timeDelay = 7200;													// Optional time delay between iterations (in seconds)
//
//	public static void main(String[] args) {
//
//		// Create target directory if it does not exists
//		createDir(targetDirJava);
//
//		// RESTest runner
//		AbstractTestCaseGenerator generator = createGenerator();				// Test case generator
//		IWriter writer = createWriter();										// Test case writer
//		AllureReportManager reportManager = createAllureReportManager();		// Allure test case reporter
//		CSVReportManager csvReportManager = createCSVReportManager();			// CSV test case reporter
//		CoverageMeter covMeter = createCoverageMeter();							//Coverage meter
//		RESTestRunner runner = new RESTestRunner(testClassName, targetDirJava, packageName, generator, writer, reportManager, csvReportManager, covMeter);
//
//		int iteration = 1;
//		while (totalNumTestCases == -1 || runner.getNumTestCases() < totalNumTestCases) {
//
//			// Introduce optional delay
//			if (iteration!=1 && timeDelay!=-1)
//				delay();
//
//			// Generate unique test class name to avoid the same class being loaded everytime
//			String className = testClassName + "_" + IDGenerator.generateId();
//			((RESTAssuredWriter) writer).setClassName(className);
//			runner.setTestClassName(className);
//
//			// Test case generation + execution + test report generation
//			runner.run();
//
//			System.out.println("Iteration "  + iteration + ". " +  runner.getNumTestCases() + " test cases generated.");
//			iteration++;
//		}
//
//	}
//
//	private static void delay() {
//
//		try {
//			TimeUnit.SECONDS.sleep(timeDelay);
//		} catch (InterruptedException e) {
//			System.err.println("Error introducing delay: " + e.getMessage());
//			e.printStackTrace();
//		}
//
//	}
//
//	// Create a random test case generator
//	private static AbstractTestCaseGenerator createGenerator() {
//
//		// Load specification
//        spec = new OpenAPISpecification(OAISpecPath);
//
//        // Load configuration
//        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(confPath);
//
//        // Create generator and filter
//        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
//
//		return generator;
//	}
//
//	// Create a writer for RESTAssured
//	private static IWriter createWriter() {
//        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
//        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDirJava, testClassName, packageName, basePath);
//        writer.setLogging(true);
//        writer.setAllureReport(true);
//		writer.setEnableStats(true);
////		writer.setEnableStats(false);
//		writer.setAPIName(APIName);
//		return writer;
//	}
//
//	// Create an Allure report manager
//	private static AllureReportManager createAllureReportManager() {
//		String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/" + APIName;
//		String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/" + APIName;
//
//		// Delete previous results (if any)
//		deleteDir(allureResultsDir);
//		deleteDir(allureReportDir);
//
//		AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);
//		arm.setHistoryTrend(true);
//		return arm;
//	}
//
//	// Create a CSV report manager
//	private static CSVReportManager createCSVReportManager() {
//		String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/" + APIName;
//		String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/" + APIName;
//
//		// Delete previous results (if any)
//		deleteDir(testDataDir);
//		deleteDir(coverageDataDir);
//
//		// Recreate directories
//		createDir(testDataDir);
//		createDir(coverageDataDir);
//
//		return new CSVReportManager(testDataDir, coverageDataDir);
//
////		CSVReportManager csvReportManager = new CSVReportManager();
////		csvReportManager.setEnableStats(false);
////		return csvReportManager;
//	}
//
//	private static CoverageMeter createCoverageMeter() {
//		CoverageGatherer cg = new CoverageGatherer(spec);
//		CoverageMeter cm = new CoverageMeter(cg);
//		return cm;
//	}
//}