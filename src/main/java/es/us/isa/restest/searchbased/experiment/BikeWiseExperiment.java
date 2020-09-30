/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.experiment;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.searchbased.SearchBasedTestSuiteGenerator;
import es.us.isa.restest.searchbased.objectivefunction.Coverage;
import es.us.isa.restest.searchbased.objectivefunction.InputCoverage;
import es.us.isa.restest.searchbased.objectivefunction.RestfulAPITestingObjectiveFunction;
import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;
import es.us.isa.restest.searchbased.objectivefunction.UniqueFailures;
import es.us.isa.restest.searchbased.terminationcriteria.MaxEvaluations;
import es.us.isa.restest.searchbased.terminationcriteria.MaxExecutedRequests;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.IWriter;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static es.us.isa.restest.searchbased.terminationcriteria.Or.or;
import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.searchbased.terminationcriteria.And.and;
import static es.us.isa.restest.util.FileManager.deleteDir;
import static es.us.isa.restest.util.PropertyManager.readProperty;
import static es.us.isa.restest.util.Timer.TestStep.ALL;

/**
 *
 * @author japar
 */
public class BikeWiseExperiment {

    private static int fixedTestSuiteSize = 2; // Number of test cases per suite
    private static int minTestSuiteSize = 1;
    private static int maxTestSuiteSize = 16;
    private static int populationSize = 10; // Population size for the evolutionary algorithm
    private static int maxEvaluations = 5000;
    private static int maxExecutedRequests=100;
    private static OpenAPISpecification spec;
    private static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml"; // Path to OAS specification file
    private static String confPath = "src/test/resources/Bikewise/fullConf.yaml"; // Path to test configuration file
    private static String experimentName = "bikewise"; // Experiment name
    private static String targetDir = "src/generation/java/bikewise"; // Directory where tests will be generated.
    private static String testClassName = "BikewiseTest"; // Name of the class where tests will be written.
    private static String packageName = "bikewise";							// Package name
    private static long seed = 1979;

    private static final Logger logger = LogManager.getLogger(BikeWiseExperiment.class.getName());

    public static void main(String[] args) {
        Timer.startCounting(ALL);

        createDir(targetDir);

        spec = new OpenAPISpecification(OAISpecPath);

        // RESTest runner
        IWriter writer = createWriter();                                    // Test case writer
        AllureReportManager reportManager = createAllureReportManager();    // Allure test case reporter
        StatsReportManager statsReportManager = createStatsReportManager(); // Stats reporter
        RESTestRunner runner = new RESTestRunner(testClassName, targetDir, packageName, null, (RESTAssuredWriter) writer, reportManager, statsReportManager);

        // ORDER IS IMPORTANT!!! First one will be used to determine the "best" test suite
        List<RestfulAPITestingObjectiveFunction> objectiveFunctions = Lists.newArrayList(
                new InputCoverage(),
    			new SuiteSize()
    	);
    	
        SearchBasedTestSuiteGenerator generator=new SearchBasedTestSuiteGenerator(
                            spec,
                            confPath,
                            experimentName,
                            objectiveFunctions,
                            targetDir,
                            seed,
                            minTestSuiteSize,
                            maxTestSuiteSize,                            
                            populationSize,
                            or(	new MaxEvaluations(maxEvaluations),
                                new MaxExecutedRequests(maxExecutedRequests)),
                            runner
        );
                       
        try {
            generator.run();
            Timer.stopCounting(ALL);
            generateTimeReport();
        } catch (IOException ex) {
            logger.error(ex);
        }
        
    }

    // Create a writer for RESTAssured
    private static IWriter createWriter() {
        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDir, testClassName, packageName, basePath);
        writer.setLogging(true);
        writer.setAllureReport(true);
        writer.setEnableStats(true);
        writer.setEnableOutputCoverage(true);
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

    private static StatsReportManager createStatsReportManager() {
        String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/" + experimentName;
        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/" + experimentName;

        // Delete previous results (if any)
        deleteDir(testDataDir);
        deleteDir(coverageDataDir);

        // Recreate directories
        createDir(testDataDir);
        createDir(coverageDataDir);

        return new StatsReportManager(testDataDir, coverageDataDir, true, true, true, new CoverageMeter(new CoverageGatherer(spec)));
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
}
