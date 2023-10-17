package es.us.isa.restest.runners;

import es.us.isa.restest.generators.ConstraintBasedTestCaseGenerator;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.junit.Test;

import java.util.Collection;

import static es.us.isa.restest.util.FileManager.createDir;
import static es.us.isa.restest.util.FileManager.deleteDir;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RESTestExecutorTest {

    public static final String PROPERTY_FILE_PATH = "src/test/resources/Restcountries/restcountries_demo.properties";

    @Test
    public void testExecutor() throws RESTestException {


        RESTestLoader loader = new RESTestLoader(PROPERTY_FILE_PATH);

        deleteDir(loader.getTargetDirJava());

        ConstraintBasedTestCaseGenerator generator = (ConstraintBasedTestCaseGenerator) loader.createGenerator();
        Collection<TestCase> testCases = generator.generate();

        createDir(loader.getTargetDirJava());

        loader.createStatsReportManager();

        RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
        writer.write(testCases);

        RESTestExecutor executor = new RESTestExecutor(PROPERTY_FILE_PATH);
        executor.execute();

        deleteDir(loader.getTargetDirJava());

        assertTrue(true);

    }


    @Test
    public void testExecuteWithNonExistingTestClass() {

        try {
            RESTestExecutor executor = new RESTestExecutor(PROPERTY_FILE_PATH);
            executor.execute();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }


    }

}
