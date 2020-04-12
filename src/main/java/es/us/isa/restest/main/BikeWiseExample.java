//
//
//package es.us.isa.restest.main;
//
//import java.io.File;
//import java.io.IOException;
//
//import es.us.isa.restest.coverage.CoverageGatherer;
//import es.us.isa.restest.coverage.CoverageMeter;
//import es.us.isa.restest.util.CSVReportManager;
//import org.apache.commons.io.FileUtils;
//
//import es.us.isa.restest.configuration.TestConfigurationIO;
//import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
//import es.us.isa.restest.generators.AbstractTestCaseGenerator;
//import es.us.isa.restest.generators.RandomTestCaseGenerator;
//import es.us.isa.restest.runners.RESTestRunner;
//import es.us.isa.restest.specification.OpenAPISpecification;
//import es.us.isa.restest.testcases.writers.IWriter;
//import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
//import es.us.isa.restest.util.AllureReportManager;
//import es.us.isa.restest.util.PropertyManager;
//
//import static es.us.isa.restest.util.FileManager.createDir;
//import static es.us.isa.restest.util.FileManager.deleteDir;
//
///**
// * Basic test scenario example: Random test case generation, execution and test report generation.
// * @author Sergio Segura
// *
// */
//public class BikeWiseExample {
//
//	private static int numTestCases = 5;												// Number of test cases per operation
//	private static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		// Path to OAS specification file
//	private static String confPath = "src/test/resources/Bikewise/fullConf.yaml";		// Path to test configuration file
//	private static String targetDir = "src/generation/java/bikewise";					// Directory where tests will be generated.
//	private static String APIName = "Bikewise";											// API name
//	private static String packageName = "bikewise";										// Package name of the test class.
//	private static String testClassName = "BikewiseTest";								// Name of the class to be generated
//	private static OpenAPISpecification spec;											// OAS
//	private static boolean enableStats = true;											// Collect coverage statistics
//
//
//	public static void main(String[] args) {
//
//		// Create target directory if it does not exists
//		createTargetDir();
//
//		// RESTest runner
//		AbstractTestCaseGenerator generator = createGenerator();		// Test case generator
//		IWriter writer = createWriter();								// Test case writer
//		AllureReportManager reportManager = createReportManager();		// Allure test case reporter (It delete previous report, if any)
//		CSVReportManager csvReportManager = createCSVReportManager();			// CSV test case reporter
//		CoverageMeter covMeter = createCoverageMeter();							//Coverage meter
////		csvReportManager.setEnableStats(true);
//		RESTestRunner runner = new RESTestRunner(testClassName, targetDir, packageName, generator, writer, reportManager, csvReportManager, covMeter);
//
//		// Test case generation + execution + test report generation
//		runner.run();
//
//		// Delete targetDir
//		// WATCH OUT: If the test classes are not deleted, they will be loaded and run in the next execution.
//		deleteDir(targetDir);
//
//	}
//
//	// Delete a directory
//	private static void deleteDir(String dirPath) {
//		File dir = new File(dirPath);
//
//		try {
//			FileUtils.deleteDirectory(dir);
//		} catch (IOException e) {
//			System.err.println("Error deleting target dir");
//			e.printStackTrace();
//		}
//	}
//
//	// Create target dir if it does not exist
//	private static void createTargetDir() {
//		File dir = new File(targetDir + "/");
//		dir.mkdirs();
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
//        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDir, testClassName, packageName, basePath.toLowerCase());
//        writer.setLogging(true);
//        writer.setAllureReport(true);
//		writer.setEnableStats(true);
//		writer.setAPIName(APIName);
//		return writer;
//	}
//
//	// Create an Allure report manager
//	private static AllureReportManager createReportManager() {
//		String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/" + APIName;
//		String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/" + APIName;
//
//		// Delete previous results (if any)
//		deleteDir(allureResultsDir);
//		deleteDir(allureReportDir);
//
//		AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);
//
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
//
//}