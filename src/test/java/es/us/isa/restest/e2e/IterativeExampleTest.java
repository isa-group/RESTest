package es.us.isa.restest.e2e;

import es.us.isa.restest.main.IterativeExample;
import org.junit.Test;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class IterativeExampleTest {

    @Test
    public void testIterativeExampleWithBasicPropertiesFile() {
        String[] args = {"src/main/resources/ExperimentsSetup/bikewise_test.properties"};
        IterativeExample.main(args);

        assertTrue(checkIfExists("src/generation/java/bikewise_example"));

        assertTrue(checkIfExists("target/allure-results/bikewise_example"));
        assertTrue(checkIfExists("target/allure-reports/bikewise_example"));
        assertTrue(checkIfExists("target/test-data/bikewise_example/time.json"));
        assertTrue(checkIfExists("target/coverage-data/bikewise_example/test-coverage.json"));
        assertTrue(checkIfExists("target/test-data/bikewise_example/test-cases.csv"));
        assertTrue(checkIfExists("target/test-data/bikewise_example/nominal-faulty.csv"));
        assertTrue(checkIfExists("target/test-data/bikewise_example/test-results.csv"));

    }
}
