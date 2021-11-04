package es.us.isa.restest.e2e;

import es.us.isa.restest.coverage.CoverageComputation;
import es.us.isa.restest.util.PropertyManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static es.us.isa.restest.util.CSVManager.readCSV;
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
            assertEquals ("You must provide two or three arguments: 1) path to OAS,2) path to " +
                    "folder containing test cases and test results in CSV format, and 3) batch size for computing " +
                    "coverage (optional, but improve performance for very large test suites, e.g., >100K test cases.", e.getMessage());
        }
    }

    @Test
    public void wrongThirdArgumentTest() {
        try {
            String[] args = {"src/test/resources/restest-test-resources/coverage-data/swagger.yaml", "src/test/resources/restest-test-resources/coverage-data", "noInteger"};
            CoverageComputation.main(args);
            fail("This test should throw an exception.");
        } catch (IllegalArgumentException e) {
            assertEquals ("The batch size must be an integer greater than 0.", e.getMessage());
        }
    }

    @Test
    public void wrongThirdArgument2Test() {
        try {
            String[] args = {"src/test/resources/restest-test-resources/coverage-data/swagger.yaml", "src/test/resources/restest-test-resources/coverage-data", "-5"};
            CoverageComputation.main(args);
            fail("This test should throw an exception.");
        } catch (IllegalArgumentException e) {
            assertEquals ("The batch size must be an integer greater than 0.", e.getMessage());
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

        List<List<String>> csvRowsAPriori = readCSV(aPrioriCoveragePath);
        int indexAPriori = csvRowsAPriori.get(0).indexOf("STATUS_CODE//find-by-address->GET");
        List<List<String>> csvRowsAPosteriori = readCSV(aPosterioriCoveragePath);
        int indexAPosteriori = csvRowsAPosteriori.get(0).indexOf("STATUS_CODE//find-by-address->GET");

        assertEquals("50.0", csvRowsAPriori.get(1).get(indexAPriori));
        assertEquals("50.0", csvRowsAPosteriori.get(1).get(indexAPosteriori));

        deleteFile(aPrioriCoveragePath);
        deleteFile(aPosterioriCoveragePath);
    }
}
