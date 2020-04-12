package es.us.isa.restest.main;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.*;

import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.FileManager.deleteDir;
import static es.us.isa.restest.util.PropertyManager.readExperimentProperty;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.util.Timer.TestStep.ALL;

public class IterativeExample {

    private static int numTestCases;			            // Number of test cases per operation
    private static String OAISpecPath;		                // Path to OAS specification file
    private static OpenAPISpecification spec;               // OAS
    private static String confPath;	                        // Path to test configuration file
    private static String targetDirJava;	                // Directory where tests will be generated.
    private static String packageName;						// Package name.
    private static String experimentName;					// Used as identifier for folders, etc.
    private static String testClassName;					// Name prefix of the class to be generated
    private static Boolean enableInputCoverage = true;      // Set to 'true' if you want the input coverage report.
    private static Boolean enableOutputCoverage = true;     // Set to 'true' if you want the input coverage report.
    private static Boolean enableCSVStats = true;           // Set to 'true' if you want statistics in a CSV file.
    private static Boolean ignoreDependencies = false;      // Set to 'true' if you don't want to use IDLReasoner.
    private static float faultyRatio = 0.1f;                // Percentage of faulty test cases to generate. Defaults to 0.1
    private static float faultyDependencyRatio = 0.5f;      // Percentage of faulty test cases due to dependencies to generate. Defaults to 0.05 (0.1*0.5)
    private static int totalNumTestCases = 50;				// Total number of test cases to be generated
    private static int timeDelay = -1;

    public static void main(String[] args) {
        Timer.startCounting(ALL);

        if(args.length > 0)
            setEvaluationParameters(args[0]);
        else
            setEvaluationParameters(readProperty("evaluation.properties.dir") +  "/foursquare.properties");

        // Create target directory if it does not exists
        createDir(targetDirJava);

        // RESTest runner
        AbstractTestCaseGenerator generator = createGenerator();            // Test case generator
        IWriter writer = createWriter();                                    // Test case writer
        AllureReportManager reportManager = createAllureReportManager();    // Allure test case reporter
        CSVReportManager csvReportManager = createCSVReportManager();       // CSV test case reporter
        CoverageMeter coverageMeter = createCoverageMeter();                // Coverage meter
        RESTestRunner runner = new RESTestRunner(testClassName, targetDirJava, packageName, generator, writer, reportManager, csvReportManager, coverageMeter);

        int iteration = 1;
        while (totalNumTestCases == -1 || runner.getNumTestCases() < totalNumTestCases) {

            // Introduce optional delay
            if (iteration!=1 && timeDelay!=-1)
                delay();

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
            String csvNFPath = csvReportManager.getTestDataDir() + "/" + readProperty("data.tests.testcases.nominalfaulty.file");
            generator.exportNominalFaultyToCSV(csvNFPath, "total");
        }

        Timer.stopCounting(ALL);
        runner.generateTimeReport();
    }

    private static void setEvaluationParameters(String evalPropertiesFilePath) {
        numTestCases = Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "numtestcases"));
        OAISpecPath = readExperimentProperty(evalPropertiesFilePath, "oaispecpath");
        confPath = readExperimentProperty(evalPropertiesFilePath, "confpath");
        targetDirJava = readExperimentProperty(evalPropertiesFilePath, "targetdirjava");
        packageName = readExperimentProperty(evalPropertiesFilePath, "packagename");
        experimentName = readExperimentProperty(evalPropertiesFilePath, "experimentname");
        testClassName = readExperimentProperty(evalPropertiesFilePath, "testclassname");
        enableInputCoverage = Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "enableinputcoverage"));
        enableOutputCoverage = Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "enableoutputcoverage"));
        enableCSVStats = Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "enablecsvstats"));
        ignoreDependencies = Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "ignoredependencies"));
        totalNumTestCases = Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "numtotaltestcases"));
        timeDelay = Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "delay"));
        faultyRatio = Float.parseFloat(readExperimentProperty(evalPropertiesFilePath, "faultyratio"));
        faultyDependencyRatio = Float.parseFloat(readExperimentProperty(evalPropertiesFilePath, "faultydependencyratio"));
    }

    // Create a test case generator
    private static AbstractTestCaseGenerator createGenerator() {
        // Load spec
        spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = loadConfiguration(confPath);

        // Create generator
        AbstractTestCaseGenerator generator;
        if(ignoreDependencies)
            generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
        else {
            generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
            ((ConstraintBasedTestCaseGenerator) generator).setFaultyDependencyRatio(faultyDependencyRatio);
        }
        generator.setFaultyRatio(faultyRatio);

        return generator;
    }

    // Create a writer for RESTAssured
    private static IWriter createWriter() {
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDirJava, testClassName, packageName, basePath);
        writer.setLogging(true);
        writer.setAllureReport(true);
        writer.setEnableStats(enableOutputCoverage);
        writer.setAPIName(experimentName);
        return writer;
    }

    // Create an Allure report manager
    private static AllureReportManager createAllureReportManager() {
        String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/" + experimentName;
        String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/" + experimentName;

        // Delete previous results (if any)
        deleteDir(allureResultsDir);
        deleteDir(allureReportDir);

        AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);
        arm.setHistoryTrend(true);
        return arm;
    }

    // Create a CSV report manager
    private static CSVReportManager createCSVReportManager() {
        String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/" + experimentName;
        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/" + experimentName;

        // Delete previous results (if any)
        deleteDir(testDataDir);
        deleteDir(coverageDataDir);

        // Recreate directories
        createDir(testDataDir);
        createDir(coverageDataDir);

        CSVReportManager csvReportManager = new CSVReportManager(testDataDir, coverageDataDir);
        csvReportManager.setEnableStats(enableCSVStats);
        csvReportManager.setEnableInputCoverage(enableInputCoverage);

        return csvReportManager;
    }

    private static CoverageMeter createCoverageMeter() {
        if(enableInputCoverage && enableOutputCoverage) {
            return new CoverageMeter(new CoverageGatherer(spec));
        }

        return null;
    }

    private static void delay() {
        try {
            TimeUnit.SECONDS.sleep(timeDelay);
        } catch (InterruptedException e) {
            System.err.println("Error introducing delay: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
