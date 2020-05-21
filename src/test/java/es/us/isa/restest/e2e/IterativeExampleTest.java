package es.us.isa.restest.e2e;

import es.us.isa.restest.main.IterativeExample;
import org.junit.Test;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static org.junit.Assert.assertTrue;

public class IterativeExampleTest {

    @Test
    public void testIterativeExampleWithBasicPropertiesFile() {
        String[] args = {"src/main/resources/ExperimentsSetup/bikewise_basic.properties"};
        IterativeExample.main(args);

        assertTrue(checkIfExists("src/generation/java/bikeWiseAPIV2"));

        assertTrue(checkIfExists("target/allure-results/bikeWiseAPIV2"));
        assertTrue(checkIfExists("target/allure-reports/bikeWiseAPIV2"));
        assertTrue(checkIfExists("target/test-data/bikeWiseAPIV2/time.json"));

    }
}
