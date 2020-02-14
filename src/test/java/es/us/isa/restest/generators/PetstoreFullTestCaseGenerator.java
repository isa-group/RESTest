package es.us.isa.restest.generators;

import static es.us.isa.restest.coverage.CriterionType.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CoverageMeter;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writers.RESTAssuredWriter;

public class PetstoreFullTestCaseGenerator {

    @Test
    public void petstoreFullTestCaseGenerator() {
        // Load specification
        String OAISpecPath = "src/test/resources/Petstore/swagger.yaml";
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/Petstore/fullConf.yaml");

        // Set number of test cases to be generated on each path, on each operation (HTTP method)
        int numTestCases = 3;

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

//        List<TestConfigurationFilter> filters = new ArrayList<>();
//        TestConfigurationFilter filter = new TestConfigurationFilter();
//        filter.setPath("/store/order/{orderId}");
//        filter.addGetMethod();
//        filters.add(filter);
//
//        TestConfigurationFilter filter2 = new TestConfigurationFilter();
//        filter2.setPath("/user/login");
//        filter2.addGetMethod();
//        filters.add(filter2);
//
//        TestConfigurationFilter filter3 = new TestConfigurationFilter();
//        filter3.setPath("/pet");
//        filter3.addPostMethod();
//        filter3.addPutMethod();
//        filters.add(filter3);
//
//        TestConfigurationFilter filter4 = new TestConfigurationFilter();
//        filter4.setPath("/store/order");
//        filter4.addPostMethod();
//        filters.add(filter4);
//
//        TestConfigurationFilter filter5 = new TestConfigurationFilter();
//        filter5.setPath("/user");
//        filter5.addPostMethod();
//        filters.add(filter5);

        Collection<TestCase> testCases = generator.generate();

        // assertEquals("Incorrect number of test cases", 42, testCases.size());

        // Check coverage
        CoverageGatherer coverageGatherer = new CoverageGatherer(spec);
        CoverageMeter coverageMeter = new CoverageMeter(coverageGatherer, testCases);
        System.out.println("Total coverage: " + coverageMeter.getTotalCoverage());
        System.out.println("Input coverage: " + coverageMeter.getInputCoverage());
        System.out.println("PATH coverage: " + coverageMeter.getCriterionTypeCoverage(PATH));
        System.out.println("OPERATION coverage: " + coverageMeter.getCriterionTypeCoverage(OPERATION));
        System.out.println("PARAMETER coverage: " + coverageMeter.getCriterionTypeCoverage(PARAMETER));
        System.out.println("PARAMETER_VALUE coverage: " + coverageMeter.getCriterionTypeCoverage(PARAMETER_VALUE));
        System.out.println("AUTHENTICATION coverage: " + coverageMeter.getCriterionTypeCoverage(AUTHENTICATION));
        System.out.println("INPUT_CONTENT_TYPE coverage: " + coverageMeter.getCriterionTypeCoverage(OPERATION));

        // Write RESTAssured test cases
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        RESTAssuredWriter writer = new RESTAssuredWriter(OAISpecPath, "src/generation/java/restassured", "PetstoreTest", "restassured", basePath.toLowerCase());
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.write(testCases);

    }
}
