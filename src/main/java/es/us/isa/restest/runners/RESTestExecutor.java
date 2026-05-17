package es.us.isa.restest.runners;


import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.ClassLoader;

import es.us.isa.restest.util.Timer;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.junit4.AllureJunit4;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static es.us.isa.restest.util.Timer.TestStep.TEST_SUITE_EXECUTION;

/**
 * This class implement execution of test cases according to the configuration properties.
 * @author José Luis García
 * @author Vicente Cambrón
 *
 */

public class RESTestExecutor {

    private static final Logger logger = LogManager.getLogger(RESTestExecutor.class.getName());

    RESTestLoader loader;

    public RESTestExecutor(String propertyFilePath) {
        this(new RESTestLoader(propertyFilePath));
    }

    public RESTestExecutor(String propertyFilePath, boolean reloadProperties) {
        this(new RESTestLoader(propertyFilePath, reloadProperties));
    }

    public RESTestExecutor(RESTestLoader loader) {
        this.loader = loader;
    }

    public void execute() {
        String filePath = loader.targetDirJava + "/" + loader.testClassName + ".java";
        String className = loader.packageName + "." + loader.testClassName;
        Path path = Paths.get(filePath);
        if(!Files.exists(path)) {
            logger.error("Test class {} not found in {}", className, filePath);
            throw new IllegalArgumentException("Test class " + className + " not found in " + filePath);
        }else{
            String allureResultsDirectory = loader.allureResultsPath + "/" + loader.experimentName;
            System.setProperty("allure.results.directory", allureResultsDirectory);
            Class<?> testClass = loadTestClass(filePath, className);
            runTests(testClass);
        }

    }

    private Class<?> loadTestClass(String filePath, String className) {
        logger.info("Compiling and loading test class {}.java", className);
        return ClassLoader.loadClass(filePath, className);
    }

    private void runTests(Class<?> testClass) {
        JUnitCore junit = new JUnitCore();
        AllureLifecycle allureLifecycle = new AllureLifecycle();
        junit.addListener(new AllureJunit4(allureLifecycle));
        loader.spec = new OpenAPISpecification(loader.OAISpecPath);
        loader.createStatsReportManager();
        Timer.startCounting(TEST_SUITE_EXECUTION);
        Result result = junit.run(testClass);
        Timer.stopCounting(TEST_SUITE_EXECUTION);
        int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
        logger.info("{} tests run in {} seconds. Successful: {}, Failures: {}, Ignored: {}", result.getRunCount(), result.getRunTime()/1000, successfulTests, result.getFailureCount(), result.getIgnoreCount());

    }

}