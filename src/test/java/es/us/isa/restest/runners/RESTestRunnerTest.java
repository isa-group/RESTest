package es.us.isa.restest.runners;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
import es.us.isa.restest.util.AllureReportManager;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.StatsReportManager;
import org.junit.Test;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class RESTestRunnerTest {

    @Test
    public void testRunner() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/Bikewise/swagger.yaml");
        TestConfigurationObject conf = loadConfiguration("src/test/resources/Bikewise/fullConf.yaml");

        createDir("src/generation/java/runnerTest");

        String basePath = spec.getSpecification().getServers().get(0).getUrl();
        RESTAssuredWriter writer = new RESTAssuredWriter("src/test/resources/Bikewise/swagger.yaml", "src/generation/java/runnerTest", "RunnerTest", "runnerTest", basePath);
        writer.setLogging(true);
        writer.setAllureReport(true);
        writer.setEnableStats(true);
        writer.setAPIName("RunnerTest");

        RandomTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, 2);

        String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/RunnerTest";
        String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/RunnerTest";

        deleteDir(allureResultsDir);
        deleteDir(allureReportDir);

        AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);
        arm.setHistoryTrend(true);

        String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/RunnerTest";
        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/RunnerTest";

        deleteDir(testDataDir);
        deleteDir(coverageDataDir);

        createDir(testDataDir);
        createDir(coverageDataDir);

        StatsReportManager statsReportManager = new StatsReportManager(testDataDir, coverageDataDir);
        statsReportManager.setEnableCSVStats(true);
        statsReportManager.setEnableInputCoverage(true);
        statsReportManager.setCoverageMeter(new CoverageMeter(new CoverageGatherer(spec)));

        RESTestRunner runner = new RESTestRunner("RunnerTest", "src/generation/java/RunnerTest", "runnerTest", generator, writer, arm, statsReportManager);

        runner.run();

        assertTrue(checkIfExists("src/generation/java/runnerTest"));

        assertTrue(checkIfExists("target/allure-results/RunnerTest"));
        assertTrue(checkIfExists("target/allure-reports/RunnerTest"));
        assertTrue(checkIfExists("target/coverage-data/RunnerTest/test-coverage.json"));
        assertTrue(checkIfExists("target/test-data/RunnerTest/test-cases.csv"));
        assertTrue(checkIfExists("target/test-data/RunnerTest/nominal-faulty.csv"));
        assertTrue(checkIfExists("target/test-data/RunnerTest/test-results.csv"));
    }
}