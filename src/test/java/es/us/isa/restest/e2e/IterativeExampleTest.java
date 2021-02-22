package es.us.isa.restest.e2e;

import es.us.isa.restest.main.TestGenerationAndExecution;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class IterativeExampleTest {

    private static MockedStatic<PropertyManager> mock;

    @BeforeClass
    public static void setUpMock() {
        mock = Mockito.mockStatic(PropertyManager.class);
        mock.when(() -> PropertyManager.readProperty("allure.results.dir")).thenReturn("target/allure-results");
        mock.when(() -> PropertyManager.readProperty("allure.report.dir")).thenReturn("target/allure-reports");
        mock.when(() -> PropertyManager.readProperty("allure.command.windows")).thenReturn("allure/bin/allure.bat");
        mock.when(() -> PropertyManager.readProperty("allure.command.unix")).thenReturn("allure/bin/allure");
        mock.when(() -> PropertyManager.readProperty("allure.categories.path")).thenReturn("src/main/resources/allure-categories.json");
        mock.when(() -> PropertyManager.readProperty("data.coverage.dir")).thenReturn("target/coverage-data");
        mock.when(() -> PropertyManager.readProperty("data.coverage.testcases.file")).thenReturn("test-cases-coverage.csv");
        mock.when(() -> PropertyManager.readProperty("data.coverage.testresults.file")).thenReturn("test-results-coverage.csv");
        mock.when(() -> PropertyManager.readProperty("data.coverage.computation.priori.file")).thenReturn("test-coverage-priori");
        mock.when(() -> PropertyManager.readProperty("data.coverage.computation.posteriori.file")).thenReturn("test-coverage-posteriori");
        mock.when(() -> PropertyManager.readProperty("data.tests.dir")).thenReturn("target/test-data");
        mock.when(() -> PropertyManager.readProperty("data.tests.testcases.file")).thenReturn("test-cases");
        mock.when(() -> PropertyManager.readProperty("data.tests.testresults.file")).thenReturn("test-results");
        mock.when(() -> PropertyManager.readProperty("data.tests.time")).thenReturn("time.csv");
        mock.when(() -> PropertyManager.readProperty("experiment.execute")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty("deletepreviousresults")).thenReturn("true");
    }

    @Test
    public void testIterativeExampleWithBasicPropertiesFile() throws RESTestException {
        String propertiesFilePath = "src/test/resources/Bikewise/bikewise_test.properties";

        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "logToFile")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "log.path")).thenReturn("log/bikewise");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "generator")).thenReturn("CBT");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "oas.path")).thenReturn("src/test/resources/Bikewise/swagger.yaml");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "conf.path")).thenReturn("src/test/resources/Bikewise/fullConf.yaml");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "test.target.dir")).thenReturn("src/generation/java/bikewise");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "experiment.name")).thenReturn("bikewise");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "experiment.execute")).thenReturn(null);
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "testclass.name")).thenReturn("BikewiseTest");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "testsperoperation")).thenReturn("2");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "numtotaltestcases")).thenReturn("8");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "delay")).thenReturn("-1");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "reloadinputdataevery")).thenReturn("10");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "inputdatamaxvalues")).thenReturn("10");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "coverage.input")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "coverage.output")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "stats.csv")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "deletepreviousresults")).thenReturn(null);
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "faulty.ratio")).thenReturn("0.05");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "faulty.dependency.ratio")).thenReturn("0.5");

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
    public void testIterativeExampleARTestCaseGeneration() throws RESTestException {
        String propertiesFilePath = "src/test/resources/AnApiOfIceAndFire/iceandfire_art.properties";

        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "logToFile")).thenReturn("false");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "generator")).thenReturn("ART");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "oas.path")).thenReturn("src/test/resources/AnApiOfIceAndFire/swagger.yaml");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "conf.path")).thenReturn("src/test/resources/AnApiOfIceAndFire/testConf.yaml");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "test.target.dir")).thenReturn("src/generation/java/anApiOfIceAndFire");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "experiment.name")).thenReturn("anApiOfIceAndFire");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "experiment.execute")).thenReturn(null);
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "testclass.name")).thenReturn("AnApiOfIceAndFireTest");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "testsperoperation")).thenReturn("1");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "numtotaltestcases")).thenReturn("4");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "delay")).thenReturn("-1");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "reloadinputdataevery")).thenReturn("10");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "inputdatamaxvalues")).thenReturn("10");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "coverage.input")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "coverage.output")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "stats.csv")).thenReturn("true");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "deletepreviousresults")).thenReturn(null);
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "faulty.ratio")).thenReturn("0.1");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "faulty.dependency.ratio")).thenReturn("0.5");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "similarity.metric")).thenReturn("LEVENSHTEIN");
        mock.when(() -> PropertyManager.readProperty(propertiesFilePath, "art.number.candidates")).thenReturn("100");

        String[] args = {propertiesFilePath};
        TestGenerationAndExecution.main(args);

        assertTrue(checkIfExists("src/generation/java/anApiOfIceAndFire"));

        assertTrue(checkIfExists("target/allure-results/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/allure-reports/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/test-data/anApiOfIceAndFire/time.csv"));
        assertTrue(checkIfExists("target/coverage-data/anApiOfIceAndFire"));
        assertTrue(checkIfExists("target/test-data/anApiOfIceAndFire"));
    }
}
