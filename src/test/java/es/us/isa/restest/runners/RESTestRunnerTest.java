package es.us.isa.restest.runners;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.reporting.AllureReportManager;
import es.us.isa.restest.reporting.StatsReportManager;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;

import org.junit.Test;

import java.util.ArrayList;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class RESTestRunnerTest {

    @Test
    public void testRunner() throws RESTestException {
        deleteDir("src/generation/java/runnerTest");
        createDir("src/generation/java/runnerTest");

        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/YouTube/swagger_betty.yaml");
        String confPath = "src/test/resources/YouTube/testConf_betty.yaml";
        TestConfigurationObject conf = loadConfiguration(confPath, spec);
        String testId = IDGenerator.generateId();

        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter("src/test/resources/YouTube/swagger_betty.yaml", "src/test/resources/YouTube/testConf_betty.yaml", "src/generation/java/runnerTest", "RunnerTest", "runnerTest", basePath, false);
        writer.setLogging(true);
        writer.setAllureReport(true);
        writer.setEnableStats(true);
        writer.setAPIName("RunnerTest");
        writer.setTestId(testId);

        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, 2);

        String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/RunnerTest";
        String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/RunnerTest";

        deleteDir(allureResultsDir);
        deleteDir(allureReportDir);

        AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir, new ArrayList<>());
        arm.setHistoryTrend(true);

        String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/RunnerTest";
        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/RunnerTest";

        deleteDir(testDataDir);
        deleteDir(coverageDataDir);

        createDir(testDataDir);
        createDir(coverageDataDir);

        StatsReportManager statsReportManager = new StatsReportManager(testDataDir, coverageDataDir);
        statsReportManager.setCoverageMeter(new CoverageMeter(new CoverageGatherer(spec)));

        RESTestRunner runner = new RESTestRunner("RunnerTest", "src/generation/java/runnerTest", "runnerTest", false, false, spec, confPath, generator,writer, arm, statsReportManager);
        runner.setExecuteTestCases(true);
        runner.setTestId(testId);

        runner.run();

        assertTrue(checkIfExists("src/generation/java/runnerTest"));

        assertTrue(checkIfExists("target/allure-results/RunnerTest"));
        assertTrue(checkIfExists("target/allure-reports/RunnerTest"));
        assertTrue(checkIfExists("target/coverage-data/RunnerTest"));
        assertTrue(checkIfExists("target/test-data/RunnerTest"));
    }
}