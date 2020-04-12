//package es.us.isa.restest.util;
//
//import es.us.isa.restest.configuration.TestConfigurationIO;
//import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
//import es.us.isa.restest.coverage.CoverageGatherer;
//import es.us.isa.restest.coverage.CoverageMeter;
//import es.us.isa.restest.generators.AbstractTestCaseGenerator;
//import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
//import es.us.isa.restest.generators.RandomTestCaseGenerator;
//import es.us.isa.restest.specification.OpenAPISpecification;
//import es.us.isa.restest.testcases.writers.IWriter;
//import es.us.isa.restest.testcases.writers.RESTAssuredWriter;
//
//import java.util.concurrent.TimeUnit;
//
//import static es.us.isa.restest.util.FileManager.createDir;
//import static es.us.isa.restest.util.FileManager.deleteDir;
//
//public class MainUtils {
//
//    // Create a random test case generator
//    public static AbstractTestCaseGenerator createGenerator(OpenAPISpecification spec, String confPath, int numTestCases, Boolean ignoreDependencies) {
//
//        // Load configuration
//        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(confPath);
//
//        // Create generator and filter
//        AbstractTestCaseGenerator generator;
//        if(ignoreDependencies)
//            generator = new RandomTestCaseGenerator(spec, conf, numTestCases);
//        else
//            generator = new ConstraintBasedTestCaseGenerator(spec, conf, numTestCases);
////        generator.setIgnoreDependencies(ignoreDependencies);
//        return generator;
//    }
//
//    // Create a writer for RESTAssured
//    public static IWriter createWriter(OpenAPISpecification spec, String OAISpecPath, String targetDirJava, String testClassName, String packageName, Boolean enableOutputStats, String APIName) {
//        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
//        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, targetDirJava, testClassName, packageName, basePath);
//        writer.setLogging(true);
//        writer.setAllureReport(true);
//        writer.setEnableStats(enableOutputStats);
//        writer.setAPIName(APIName);
//        return writer;
//    }
//
//    // Create an Allure report manager
//    public static AllureReportManager createAllureReportManager(String APIName) {
//        String allureResultsDir = PropertyManager.readProperty("allure.results.dir") + "/" + APIName;
//        String allureReportDir = PropertyManager.readProperty("allure.report.dir") + "/" + APIName;
//
//        // Delete previous results (if any)
//        deleteDir(allureResultsDir);
//        deleteDir(allureReportDir);
//
//        AllureReportManager arm = new AllureReportManager(allureResultsDir, allureReportDir);
//        arm.setHistoryTrend(true);
//        return arm;
//    }
//
//    // Create a CSV report manager
//    public static CSVReportManager createCSVReportManager(String APIName, Boolean enableCSVStats, Boolean enableInputCoverage) {
//        String testDataDir = PropertyManager.readProperty("data.tests.dir") + "/" + APIName;
//        String coverageDataDir = PropertyManager.readProperty("data.coverage.dir") + "/" + APIName;
//
//        // Delete previous results (if any)
//        deleteDir(testDataDir);
//        deleteDir(coverageDataDir);
//
//        // Recreate directories
//        createDir(testDataDir);
//        createDir(coverageDataDir);
//
//        CSVReportManager csvReportManager = new CSVReportManager(testDataDir, coverageDataDir);
//        csvReportManager.setEnableStats(enableCSVStats);
//        csvReportManager.setEnableInputCoverage(enableInputCoverage);
//
//        return csvReportManager;
//    }
//
//    public static CoverageMeter createCoverageMeter(OpenAPISpecification spec) {
//        CoverageGatherer cg = new CoverageGatherer(spec);
//        CoverageMeter cm = new CoverageMeter(cg);
//        return cm;
//    }
//
//    public static void delay(Integer timeDelay) {
//
//        try {
//            TimeUnit.SECONDS.sleep(timeDelay);
//        } catch (InterruptedException e) {
//            System.err.println("Error introducing delay: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//
//}
