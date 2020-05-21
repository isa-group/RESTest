package es.us.isa.restest.main;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.FileManager.deleteDir;
import static es.us.isa.restest.util.PropertyManager.readExperimentProperty;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.util.Timer.TestStep.ALL;

public class IterativeExample {

    private static Integer numTestCases = 10;               // Number of test cases per operation
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
    private static Float faultyRatio = 0.1f;                // Percentage of faulty test cases to generate. Defaults to 0.1
    private static Integer totalNumTestCases = -1;			// Total number of test cases to be generated
    private static Integer timeDelay = -1;                  // Delay between requests

    // For CBT only:
    private static Float faultyDependencyRatio = 0.5f;      // Percentage of faulty test cases due to dependencies to generate. Defaults to 0.05 (0.1*0.5)
    private static Integer reloadInputDataEvery = 100;      // Number of requests using the same randomly generated input data
    private static Integer inputDataMaxValues = 1000;       // Number of values used for each parameter when reloading input data

    private static final Logger logger = LogManager.getLogger(IterativeExample.class.getName());

    public static void main(String[] args) {
        Timer.startCounting(ALL);

        if(args.length > 0)
            setEvaluationParameters(args[0]);
        else
            setEvaluationParameters(readProperty("evaluation.properties.dir") +  "/yelp_businessesSearch.properties");

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

            logger.info("Iteration {}. {} test cases generated.", iteration, runner.getNumTestCases());
            iteration++;
        }

        if(enableCSVStats) {
            String csvNFPath = csvReportManager.getTestDataDir() + "/" + readProperty("data.tests.testcases.nominalfaulty.file");
            generator.exportNominalFaultyToCSV(csvNFPath, "total");
        }

        Timer.stopCounting(ALL);

        generateTimeReport();
    }

    private static void setEvaluationParameters(String evalPropertiesFilePath) {

        numTestCases = readExperimentProperty(evalPropertiesFilePath, "numtestcases") != null?
                Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "numtestcases")) :
                numTestCases;

        OAISpecPath = readExperimentProperty(evalPropertiesFilePath, "oaispecpath");
        confPath = readExperimentProperty(evalPropertiesFilePath, "confpath");

        targetDirJava = readExperimentProperty(evalPropertiesFilePath, "targetdirjava") != null?
                readExperimentProperty(evalPropertiesFilePath, "targetdirjava") :
                generateDefaultTargetDir();

        packageName = readExperimentProperty(evalPropertiesFilePath, "packagename") != null?
                readExperimentProperty(evalPropertiesFilePath, "packagename") :
                getOASTitle(false);

        experimentName = readExperimentProperty(evalPropertiesFilePath, "experimentname") != null?
                readExperimentProperty(evalPropertiesFilePath, "experimentname") :
                getOASTitle(false);

        testClassName = readExperimentProperty(evalPropertiesFilePath, "testclassname") != null?
                readExperimentProperty(evalPropertiesFilePath, "testclassname") :
                getOASTitle(true);

        enableInputCoverage = readExperimentProperty(evalPropertiesFilePath, "enableinputcoverage") != null?
                Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "enableinputcoverage")) :
                enableInputCoverage;

        enableOutputCoverage = readExperimentProperty(evalPropertiesFilePath, "enableoutputcoverage") != null?
                Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "enableoutputcoverage")) :
                enableOutputCoverage;

        enableCSVStats = readExperimentProperty(evalPropertiesFilePath, "enablecsvstats") != null?
                Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "enablecsvstats")) :
                enableCSVStats;

        ignoreDependencies = readExperimentProperty(evalPropertiesFilePath, "ignoredependencies") != null?
                Boolean.parseBoolean(readExperimentProperty(evalPropertiesFilePath, "ignoredependencies")) :
                ignoreDependencies;

        totalNumTestCases = readExperimentProperty(evalPropertiesFilePath, "numtotaltestcases") != null?
                Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "numtotaltestcases")) :
                totalNumTestCases;

        timeDelay = readExperimentProperty(evalPropertiesFilePath, "delay") != null?
                Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "delay")) :
                timeDelay;

        faultyRatio = readExperimentProperty(evalPropertiesFilePath, "faultyratio") != null?
                Float.parseFloat(readExperimentProperty(evalPropertiesFilePath, "faultyratio")) :
                faultyRatio;

        faultyDependencyRatio = readExperimentProperty(evalPropertiesFilePath, "faultydependencyratio") != null?
                Float.parseFloat(readExperimentProperty(evalPropertiesFilePath, "faultydependencyratio")) :
                faultyDependencyRatio;

        reloadInputDataEvery = readExperimentProperty(evalPropertiesFilePath, "reloadinputdataevery") != null?
                Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "reloadinputdataevery")) :
                reloadInputDataEvery;

        inputDataMaxValues = readExperimentProperty(evalPropertiesFilePath, "inputdatamaxvalues") != null?
                Integer.parseInt(readExperimentProperty(evalPropertiesFilePath, "inputdatamaxvalues")) :
                inputDataMaxValues;
    }

    private static String generateDefaultTargetDir() {
        return "src/generation/java/" + getOASTitle(false);
    }

    private static String getOASTitle(boolean capitalize) {
        if(spec == null) {
            spec = new OpenAPISpecification(OAISpecPath);
        }
        String title = spec.getSpecification().getInfo().getTitle().replaceAll("[^\\p{L}\\p{Nd}\\s]+", "").trim();
        title = (capitalize? title.substring(0,1).toUpperCase() : title.substring(0,1).toLowerCase()) +
                (title.length() > 1? formatTitle(title.substring(1).split("\\s")) : "");
        return title;
    }

    private static String formatTitle(String[] sp) {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < sp.length; i++) {
            if(i == 0) {
                builder.append(sp[i]);
            } else {
                builder.append(sp[i].substring(0, 1).toUpperCase());
                if(sp[i].length() > 1) {
                   builder.append(sp[i].substring(1));
                }
            }
        }
        return builder.toString();
    }
    // Create a test case generator
    private static AbstractTestCaseGenerator createGenerator() {
        // Load spec
        if(spec == null) {
            spec = new OpenAPISpecification(OAISpecPath);
        }

        // Load configuration
        TestConfigurationObject conf = loadConfiguration(confPath);

        // Create generator
        AbstractTestCaseGenerator generator;
        if(ignoreDependencies)
            generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
        else {
            generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
            ((ConstraintBasedTestCaseGenerator) generator).setFaultyDependencyRatio(faultyDependencyRatio);
            ((ConstraintBasedTestCaseGenerator) generator).setInputDataMaxValues(inputDataMaxValues);
            ((ConstraintBasedTestCaseGenerator) generator).setReloadInputDataEvery(reloadInputDataEvery);
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

    private static void generateTimeReport() {
        ObjectMapper mapper = new ObjectMapper();
        String timePath = readProperty("data.tests.dir") + "/" + experimentName + "/" + readProperty("data.tests.time");
        try {
            mapper.writeValue(new File(timePath), Timer.getCounters());
        } catch (IOException e) {
            logger.error("The time report cannot be generated. Stack trace:");
            logger.error(e.getMessage());
        }
        logger.info("Time report generated.");
    }

    private static void delay() {
        try {
            TimeUnit.SECONDS.sleep(timeDelay);
        } catch (InterruptedException e) {
           logger.error("Error introducing delay: {}", e.getMessage());
        }
    }
}
