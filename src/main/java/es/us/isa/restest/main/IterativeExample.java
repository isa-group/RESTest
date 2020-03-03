package es.us.isa.restest.main;

import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.*;

import static es.us.isa.restest.util.FileManager.createDir;

public class IterativeExample {

    private static int numTestCases;			        // Number of test cases per operation
    private static String OAISpecPath;		                // Path to OAS specification file
    private static String confPath;	                        // Path to test configuration file
    private static String targetDirJava;	                // Directory where tests will be generated.
    //	private static String targetDirTestData = "target/test-data";						// Directory where tests will be exported to CSV.
//	private static String targetDirCoverageData = "target/coverage-data";				    // Directory where coverage will be exported to CSV.
    private static String packageName;						// Package name.
    private static String APIName;							// API name
    private static String testClassName;					// Name prefix of the class to be generated
    private static Boolean enableInputCoverage = true;      // Set to 'true' if you want the input coverage report.
    private static Boolean enableOutputCoverage = true;     // Set to 'true' if you want the input coverage report.
    private static Boolean enableCSVStats = true;           // Set to 'true' if you want statistics in a CSV file.
    private static Boolean ignoreDependencies = false;      // Set to 'true' if you don't want to use IDLReasoner.
    private static Float faultyRatio = 0.1f;                // Percentage of faulty test cases to generate. Defaults to 0.1
    private static int totalNumTestCases = 50;				// Total number of test cases to be generated
    private static int timeDelay = -1;

    public static void main(String[] args) {

        if(args.length > 0)
            setParameters(args[0]);
        else
            setParameters("src/main/resources/APIProperties/youtube_search.properties");

        // Create target directory if it does not exists
        createDir(targetDirJava);

        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        AbstractTestCaseGenerator generator = MainUtils.createGenerator(spec, confPath, numTestCases, ignoreDependencies);	                                        // Test case generator
        generator.setFaultyRatio(faultyRatio);
        IWriter writer = MainUtils.createWriter(spec, OAISpecPath, targetDirJava, testClassName, packageName, enableOutputCoverage, APIName);   // Test case writer
        AllureReportManager reportManager = MainUtils.createAllureReportManager(APIName);		                                                // Allure test case reporter
        CSVReportManager csvReportManager = MainUtils.createCSVReportManager(APIName, enableCSVStats, enableInputCoverage);			                                // CSV test case reporter
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

        if(enableCSVStats) {
            String csvNFPath = csvReportManager.getTestDataDir() + "/" + PropertyManager.readProperty("data.tests.testcases.nominalfaulty.file");
            generator.exportNominalFaultyToCSV(csvNFPath, "total");
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
        totalNumTestCases = Integer.parseInt(PropertyManager.readProperty(APIPropertyFilePath, "api.numtotaltestcases"));
        timeDelay = Integer.parseInt(PropertyManager.readProperty(APIPropertyFilePath, "api.delay"));

        String faultyRatioString = PropertyManager.readProperty(APIPropertyFilePath, "api.faultyratio");
        if (faultyRatioString != null)
            faultyRatio = Float.parseFloat(faultyRatioString);
    }
}
