package es.us.isa.restest.e2e;

import es.us.isa.restest.main.IterativeExample;
import org.junit.Test;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class IterativeExampleTest {

    @Test
    public void testIterativeExampleWithBasicPropertiesFile() {
        String[] args = {"src/main/resources/ExperimentsSetup/comments_betty.properties"};
        IterativeExample.main(args);

        assertTrue(checkIfExists("src/generation/java/commentsTest1"));

        assertTrue(checkIfExists("target/allure-results/commentsTest1"));
        assertTrue(checkIfExists("target/allure-reports/commentsTest1"));
        assertTrue(checkIfExists("target/test-data/commentsTest1/time.json"));
        assertTrue(checkIfExists("target/coverage-data/commentsTest1/test-coverage-priori.json"));
        assertTrue(checkIfExists("target/coverage-data/commentsTest1/test-coverage-posteriori.json"));
        assertTrue(checkIfExists("target/test-data/commentsTest1/test-cases.csv"));
        assertTrue(checkIfExists("target/test-data/commentsTest1/nominal-faulty.csv"));
        assertTrue(checkIfExists("target/test-data/commentsTest1/test-results.csv"));

    }
}
