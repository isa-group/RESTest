package es.us.isa.restest.runners;


import es.us.isa.restest.util.ClassLoader;

import es.us.isa.restest.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static es.us.isa.restest.util.Timer.TestStep.TEST_SUITE_EXECUTION;

public class RESTestExecutor {

    private static final Logger logger = LogManager.getLogger(RESTestExecutor.class.getName());

    RESTestLoader loader;

    public RESTestExecutor(String propertyFilePath) {
        loader = new RESTestLoader(propertyFilePath);
    }

    public void execute() {
        String filePath = loader.targetDirJava + "/" + loader.testClassName + ".java";
        String className = loader.packageName + "." + loader.testClassName;
        Path path = Paths.get(filePath);
        if(!Files.exists(path)) {
            logger.error("Test class {} not found in {}", className, filePath);
            throw new IllegalArgumentException("Test class " + className + " not found in " + filePath);
        }else{
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
        junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
        Timer.startCounting(TEST_SUITE_EXECUTION);
        Result result = junit.run(testClass);
        Timer.stopCounting(TEST_SUITE_EXECUTION);
        int successfulTests = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
        logger.info("{} tests run in {} seconds. Successful: {}, Failures: {}, Ignored: {}", result.getRunCount(), result.getRunTime()/1000, successfulTests, result.getFailureCount(), result.getIgnoreCount());

    }

}