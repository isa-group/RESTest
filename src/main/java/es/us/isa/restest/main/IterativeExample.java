package es.us.isa.restest.main;

import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.*;
import sun.applet.Main;

import static es.us.isa.restest.util.FileManager.createDir;

public class IterativeExample {

    private static int numTestCases = 2;												    // Number of test cases per operation
    private static String OAISpecPath = "src/test/resources/AmadeusHotel/swagger.yaml";		// Path to OAS specification file
    private static String confPath = "src/test/resources/AmadeusHotel/defaultConf.yaml";	// Path to test configuration file
    private static String targetDirJava = "src/generation/java/amadeusHotels";				// Directory where tests will be generated.
    //	private static String targetDirTestData = "target/test-data";						// Directory where tests will be exported to CSV.
//	private static String targetDirCoverageData = "target/coverage-data";				    // Directory where coverage will be exported to CSV.
    private static String packageName = "amadeusHotels";									// Package name.
    private static String APIName = "amadeusHotelSearch";									// API name
    private static String testClassName = "AmadeusHotelsTest";								// Name prefix of the class to be generated
    private static Boolean enableInputCoverage = true;                                      // Set to 'true' if you want the input coverage report.
    private static Boolean enableOutputCoverage = true;                                     // Set to 'true' if you want the input coverage report.
    private static Boolean enableCSVStats = true;                                           // Set to 'true' if you want statistics in a CSV file.
    private static Boolean ignoreDependencies = false;                                      // Set to 'true' if you don't want to use IDLReasoner.
    private static int totalNumTestCases = 50;											    // Total number of test cases to be generated
    private static int timeDelay = -1;

    public static void main(String[] args) {

        if(args.length > 0)
            setParameters(args[0]);

        // Create target directory if it does not exists
        createDir(targetDirJava);

        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        AbstractTestCaseGenerator generator = MainUtils.createGenerator(spec, confPath, numTestCases, ignoreDependencies);	                                        // Test case generator
        IWriter writer = MainUtils.createWriter(spec, OAISpecPath, targetDirJava, testClassName, packageName, enableOutputCoverage, APIName);   // Test case writer
        AllureReportManager reportManager = MainUtils.createAllureReportManager(APIName);		                                                // Allure test case reporter
        CSVReportManager csvReportManager = MainUtils.createCSVReportManager(APIName, enableCSVStats);			                                // CSV test case reporter
        RESTestRunner runner;
        if(enableInputCoverage && enableOutputCoverage) {
            CoverageMeter covMeter = MainUtils.createCoverageMeter(spec);							                                            //Coverage meter
            runner = new RESTestRunner(testClassName, targetDirJava, packageName, generator, writer, reportManager, csvReportManager, covMeter);
        } else
            runner = new RESTestRunner(testClassName, targetDirJava, packageName, generator, writer, reportManager, csvReportManager);
        int iteration = 1;
        while (totalNumTestCases == -1 || runner.getNumTestCases() < totalNumTestCases) {

            // Introduce optional delay
            if (iteration!=1 && timeDelay!=-1)
                MainUtils.delay(timeDelay);

            // Generate unique test class name to avoid the same class being loaded everytime
            String className = testClassName + "_" + IDGenerator.generateId();
            ((RESTAssuredWriter) writer).setClassName(className);
            runner.setTestClassName(className);

            // Test case generation + execution + test report generation
            runner.run();

            System.out.println("Iteration "  + iteration + ". " +  runner.getNumTestCases() + " test cases generated.");
            iteration++;
        }
    }

    private static void setParameters(String APIPropertyFilePath) {
        numTestCases = Integer.parseInt(PropertyManager.readProperty(APIPropertyFilePath, "api.numtestcases"));
        OAISpecPath = PropertyManager.readProperty(APIPropertyFilePath, "api.oaispecpath");
        confPath = PropertyManager.readProperty(APIPropertyFilePath, "api.confpath");
        targetDirJava = PropertyManager.readProperty(APIPropertyFilePath, "api.targetdirjava");
        packageName = PropertyManager.readProperty(APIPropertyFilePath, "api.packagename");
        APIName = PropertyManager.readProperty(APIPropertyFilePath, "api.apiname");
        testClassName = PropertyManager.readProperty(APIPropertyFilePath, "api.testclassname");
        enableInputCoverage = Boolean.parseBoolean(PropertyManager.readProperty(APIPropertyFilePath, "api.enableinputcoverage"));
        enableOutputCoverage = Boolean.parseBoolean(PropertyManager.readProperty(APIPropertyFilePath, "api.enableoutputcoverage"));
        enableCSVStats = Boolean.parseBoolean(PropertyManager.readProperty(APIPropertyFilePath, "api.enablecsvstats"));
        ignoreDependencies = Boolean.parseBoolean(PropertyManager.readProperty(APIPropertyFilePath, "api.ignoredependencies"));
    }
}
