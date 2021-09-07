package es.us.isa.restest.e2e;

import es.us.isa.restest.main.TestGenerationAndExecution;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static es.us.isa.restest.util.CSVManager.readValues;
import static es.us.isa.restest.util.FileManager.*;
import static org.junit.Assert.*;

public class IterativeExampleTest {

    @Before
    public void resetSingleton() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field properties = PropertyManager.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(null, null);

        Field experimentProperties = PropertyManager.class.getDeclaredField("experimentProperties");
        experimentProperties.setAccessible(true);
        experimentProperties.set(null, null);
    }

    @Ignore // Bikewise is currently down
    @Test
    public void testIterativeExampleWithBasicPropertiesFile() throws RESTestException {
        String propertiesFilePath = "src/test/resources/Bikewise/bikewise_test.properties";

        String[] args = {propertiesFilePath};
        TestGenerationAndExecution.main(args);

        assertTrue(checkIfExists("src/generation/java/bikewise"));

        assertTrue(checkIfExists("target/allure-results/bikewise"));
        assertTrue(checkIfExists("target/allure-reports/bikewise"));
        assertTrue(checkIfExists("target/test-data/bikewise/time.csv"));
        assertTrue(checkIfExists("target/coverage-data/bikewise"));
        assertTrue(checkIfExists("target/test-data/bikewise"));
        assertTrue(checkIfExists("log/bikewise.log"));
    }

    @Test
    public void testIterativeExampleRandomTestCaseGenerator() throws RESTestException {
        String propertiesFilePath = "src/test/resources/AnApiOfIceAndFire/iceandfire_e2e.properties";

        String[] args = {propertiesFilePath};
        TestGenerationAndExecution.main(args);

        assertTrue(checkIfExists("src/generation/java/anApiOfIceAndFire"));

        assertTrue(checkIfExists("target/allure-results/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/allure-reports/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/test-data/anApiOfIceAndFire/time.csv"));
        assertTrue(checkIfExists("target/coverage-data/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/test-data/anApiOfIceAndFire"));
    }

    @Test
    public void testIterativeExampleARTestCaseGeneration() throws RESTestException {
        String propertiesFilePath = "src/test/resources/AnApiOfIceAndFire/iceandfire_art.properties";

        String[] args = {propertiesFilePath};
        TestGenerationAndExecution.main(args);

        assertTrue(checkIfExists("src/generation/java/anApiOfIceAndFire"));

        assertTrue(checkIfExists("target/allure-results/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/allure-reports/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/test-data/anApiOfIceAndFire/time.csv"));
        assertTrue(checkIfExists("target/coverage-data/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/test-data/anApiOfIceAndFire"));
    }

    @Test
    public void testIterativeExampleFuzzingTestCaseGeneration() throws RESTestException {
        String propertiesFilePath = "src/test/resources/Comments/comments_betty.properties";

        String[] args = {propertiesFilePath};
        TestGenerationAndExecution.main(args);

        assertTrue(checkIfExists("src/generation/java/commentsTest"));

        assertTrue(checkIfExists("target/allure-results/commentsTest"));
        assertTrue(checkIfExists("target/allure-reports/commentsTest"));
        assertTrue(checkIfExists("target/test-data/commentsTest/time.csv"));
        assertTrue(checkIfExists("target/coverage-data/commentsTest"));
        assertTrue(checkIfExists("target/test-data/commentsTest"));
    }

    @Test
    public void testIterativeExampleExternalLogger() throws RESTestException, IOException {
        deleteFile("target/log/external_logger/log.log");

        String propertiesFilePath = "src/test/resources/restest-test-resources/external_logger.properties";

        String[] args = {propertiesFilePath};
        try {
            TestGenerationAndExecution.main(args);
            fail("An exception should be thrown for the previous props file");
        } catch(NullPointerException ignored) {}

        System.out.println("The log should contain this string");
        System.out.write('a');
        System.out.write("To be converted to byte array".getBytes());
        System.err.println("And also this string");

        assertTrue(checkIfExists("target/log/external_logger/log.log"));

        String logContent = readFile("target/log/external_logger/log.log");

        assertNotNull(logContent);
        assertTrue(logContent.contains("Loading configuration parameter values"));
        assertTrue(logContent.contains("INFO  stdout:36 - The log should contain this string"));
        assertTrue(logContent.contains("INFO  stdout:45 - a"));
        assertTrue(logContent.contains("ERROR stderr:36 - And also this string"));

        deleteFile("target/log/external_logger/log.log");
    }

    @Test
    public void testIterativeExampleCliCommandsOption1() throws RESTestException, IOException {
        deleteFile("target/log/external_logger/log.log");

        String propertiesFilePath = "src/test/resources/restest-test-resources/external_logger.properties";
        String cliOption1 = "logToFile=false";

        String[] args = {propertiesFilePath, cliOption1};
        try {
            TestGenerationAndExecution.main(args);
            fail("An exception should be thrown for the previous props file");
        } catch(NullPointerException ignored) {}

        assertFalse(checkIfExists("target/log/external_logger/log.log"));
    }

    @Test
    public void testIterativeExampleCliCommandsOption2() throws RESTestException, IOException {
        deleteFile("target/log/external_logger/log.log");

        String propertiesFilePath = "src/test/resources/restest-test-resources/external_logger.properties";
        String cliOption1Part1 = "logToFile";
        String cliOption1Part2 = "false";

        String[] args = {propertiesFilePath, cliOption1Part1, cliOption1Part2};
        try {
            TestGenerationAndExecution.main(args);
            fail("An exception should be thrown for the previous props file");
        } catch(NullPointerException ignored) {}

        assertFalse(checkIfExists("target/log/external_logger/log.log"));
    }

    @Test
    public void testARTEWithRegex() throws RESTestException {

        String semanticPropertiesFilePath = "src/test/resources/semanticAPITests/DHL/dhl_semantic.properties";
        String parameterValuesPath = "src/main/resources/TestData/Generated/DHL_e2e/findByAddress_countryCode.csv";

        Set<String> initialValues = new HashSet<>(readValues(parameterValuesPath));

        String[] args = {semanticPropertiesFilePath};

        String confPathSemantic = "src/test/resources/semanticAPITests/DHL/testConfSemantic.yaml";

        String testConfSemanticString = readFile(confPathSemantic);

        TestGenerationAndExecution.main(args);

        String propertiesPathOriginal = "src/test/resources/semanticAPITests/DHL/dhl.properties";

        assertTrue(checkIfExists(propertiesPathOriginal));
        assertTrue(checkIfExists(parameterValuesPath));


        deleteFile(parameterValuesPath);
        createFileIfNotExists(parameterValuesPath);
        collectionToCSV(parameterValuesPath, initialValues);
        deleteFile(confPathSemantic);
        writeFile(confPathSemantic, testConfSemanticString);

    }

}