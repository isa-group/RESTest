package es.us.isa.restest.util;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.TestConfigurationVisitor;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestConfigurationVisitorTest {

    @Test
    public void testArteEnabled() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/restest-test-resources/openapiArteAndStateful.yaml");
        TestConfigurationObject testConf = TestConfigurationIO.loadConfiguration("src/test/resources/restest-test-resources/testConfArteAndStateful.yaml", spec);

        assertTrue(TestConfigurationVisitor.isArteEnabled(testConf));

        assertTrue(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(0)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(1)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(2)));
        assertTrue(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(3)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(4)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(5)));
    }

    @Test
    public void testArteDisabled() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/restest-test-resources/openapi.yaml");
        TestConfigurationObject testConf = TestConfigurationIO.loadConfiguration("src/test/resources/restest-test-resources/testConf.yaml", spec);

        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf));

        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(0)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(1)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(2)));
        assertFalse(TestConfigurationVisitor.isArteEnabled(testConf.getTestConfiguration().getOperations().get(3)));
    }

    @Test
    public void testStatefulEnabled() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/restest-test-resources/openapiArteAndStateful.yaml");
        TestConfigurationObject testConf = TestConfigurationIO.loadConfiguration("src/test/resources/restest-test-resources/testConfArteAndStateful.yaml", spec);

        assertTrue(TestConfigurationVisitor.hasStatefulGenerators(testConf));

        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(0)));
        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(1)));
        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(2)));
        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(3)));
        assertTrue(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(4)));
        assertTrue(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(5)));
    }

    @Test
    public void testStatefulDisabled() {
        OpenAPISpecification spec = new OpenAPISpecification("src/test/resources/restest-test-resources/openapi.yaml");
        TestConfigurationObject testConf = TestConfigurationIO.loadConfiguration("src/test/resources/restest-test-resources/testConf.yaml", spec);

        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf));

        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(0)));
        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(1)));
        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(2)));
        assertFalse(TestConfigurationVisitor.hasStatefulGenerators(testConf.getTestConfiguration().getOperations().get(3)));
    }
}
