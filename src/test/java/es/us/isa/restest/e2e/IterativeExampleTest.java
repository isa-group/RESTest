package es.us.isa.restest.e2e;

import es.us.isa.restest.main.TestGenerationAndExecution;
import es.us.isa.restest.util.RESTestException;

import org.junit.Test;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class IterativeExampleTest {

    @Test
    public void testIterativeExampleWithBasicPropertiesFile() throws RESTestException {
        String[] args = {"src/main/resources/ExperimentsSetup/bikewise_test.properties"};
        TestGenerationAndExecution.main(args);

        assertTrue(checkIfExists("src/generation/java/bikewise"));

        assertTrue(checkIfExists("target/allure-results/bikewise"));
        assertTrue(checkIfExists("target/allure-reports/bikewise"));
        assertTrue(checkIfExists("target/test-data/bikewise/time.json"));
        assertTrue(checkIfExists("target/coverage-data/bikewise/test-coverage-priori.csv"));
        assertTrue(checkIfExists("target/coverage-data/bikewise/test-coverage-posteriori.csv"));
        assertTrue(checkIfExists("target/test-data/bikewise/test-cases.csv"));
        assertTrue(checkIfExists("target/test-data/bikewise/test-results.csv"));

    }
}
