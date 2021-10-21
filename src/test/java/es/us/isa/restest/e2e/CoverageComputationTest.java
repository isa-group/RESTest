package es.us.isa.restest.e2e;

import es.us.isa.restest.coverage.CoverageComputation;
import es.us.isa.restest.util.PropertyManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static es.us.isa.restest.util.FileManager.checkIfExists;
import static es.us.isa.restest.util.FileManager.deleteFile;
import static org.junit.Assert.*;

public class CoverageComputationTest {

    @Before
    public void resetSingleton() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field properties = PropertyManager.class.getDeclaredField("properties");
        properties.setAccessible(true);
        properties.set(null, null);

        Field experimentProperties = PropertyManager.class.getDeclaredField("experimentProperties");
        experimentProperties.setAccessible(true);
        experimentProperties.set(null, null);
    }

    @Test
    public void wrongNumberOfArgumentsTest() {
        try {
            String[] args = {"arg1"};
            CoverageComputation.main(args);
            fail("This test should throw an exception.");
        } catch (IllegalArgumentException e) {
            assertEquals ("You must provide two arguments: 1) path to OAS, and 2) path to " +
                    "folder containing test cases and test results in CSV format.", e.getMessage());
        }
    }

    @Test
    public void oasDoesntExistTest() {
        try {
            String[] args = {"noOas", "noPath"};
            CoverageComputation.main(args);
            fail("This test should throw an exception.");
        } catch (IllegalArgumentException e) {
            assertEquals ("The specified OAS file is not valid or does not exist.", e.getMessage());
        }
    }

    @Test
    public void dirPathDoesntExistTest() {
        try {
            String[] args = {"src/test/resources/restest-test-resources/coverage-data/swagger.yaml", "noPath"};
            CoverageComputation.main(args);
            fail("This test should throw an exception.");
        } catch (IllegalArgumentException e) {
            assertEquals ("The specified path is not a directory or does not exist.", e.getMessage());
        }
    }

    @Test
    public void validTest() {
        String[] args = {"src/test/resources/restest-test-resources/coverage-data/swagger.yaml", "src/test/resources/restest-test-resources/coverage-data"};
        CoverageComputation.main(args);

        String aPrioriCoveragePath = "src/test/resources/restest-test-resources/coverage-data/test-coverage-priori.csv";
        String aPosterioriCoveragePath = "src/test/resources/restest-test-resources/coverage-data/test-coverage-posteriori.csv";

        assertTrue(checkIfExists(aPrioriCoveragePath));
        assertTrue(checkIfExists(aPosterioriCoveragePath));

        deleteFile(aPrioriCoveragePath);
        deleteFile(aPosterioriCoveragePath);
    }
}
