//package es.us.isa.restest.main;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.concurrent.TimeUnit;
//
//import org.apache.commons.io.FileUtils;
//import es.us.isa.restest.configuration.TestConfigurationIO;
//import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
//import es.us.isa.restest.generators.AbstractTestCaseGenerator;
//import es.us.isa.restest.generators.RandomTestCaseGenerator;
//import es.us.isa.restest.runners.RESTestRunner;
//import es.us.isa.restest.specification.OpenAPISpecification;
//import es.us.isa.restest.testcases.writers.IWriter;
//import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
//import es.us.isa.restest.util.AllureReportManager;
//import es.us.isa.restest.util.IDGenerator;
//import es.us.isa.restest.util.PropertyManager;
//
///**
// * Reliability test scenario example: n tests are generated and executed on each iteration.
// * Tests have always the same names and are therefore considered as the same tests by Allure.
// * This allows to check the trend of passes and failures on each iteration (out of n tests)
// * An optional time delay can be set between every two iterations.
// * @author Sergio Segura
// *
// */
//public class BikeWiseReliabilityExample {
//
//	private static int numTestCases = 2;												// Number of test cases per operation
//	private static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		// Path to OAS specification file
//	private static String confPath = "src/test/resources/Bikewise/fullConf.yaml";		// Path to test configuration file
//	private static String targetDir = "src/generation/java/bikewise";					// Directory where tests will be generated.
//	private static String packageName = "bikewise";										// Package name.
//	private static String APIName = "Bikewise";											// API name
//	private static String testClassName = "BikewiseTest";								// Name prefix of the class to be generated
//	private static OpenAPISpecification spec;
//	private static int nIterations = 5;													// Total number of iterations
//	private static int timeDelay = -1;													// Optional time delay between iterations (in seconds)
//	private static long seed=28;														// Seed for IDGenerator
//
//	public static void main(String[] args) {
//
//		// Create target directory if it does not exists
//		createTargetDir();
//
//		// RESTest runner
//		AbstractTestCaseGenerator generator = createGenerator();		// Test case generator
//		IWriter writer = createWriter();								// Test case writer
//		AllureReportManager reportManager = createReportManager();		// Allure test case reporter
//		RESTestRunner runner = new RESTestRunner(testClassName, targetDir, packageName, generator, writer, reportManager, null);
//
//		int iteration = 1;
//		while (iteration <= nIterations) {
//
//			// Introduce optional delay
//			if (iteration!=1 && timeDelay!=-1)
//				delay();
//
//			// Set seed to generate the same test methods' names
//			IDGenerator.setSeed(seed);
//
//			// Test case generation + execution + test report generation
//			runner.run();
//
//			System.out.println("Iteration "  + iteration + ". " +  runner.getNumTestCases() + " test cases generated.");
//			iteration++;
//		}
//
//		// Delete targetDir
//		// WATCH OUT: If the test class is not deleted, it will be loaded and run in the next execution.
//		//deleteDir(targetDir);
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
//		arm.setHistoryTrend(true);
//		return arm;
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
//}