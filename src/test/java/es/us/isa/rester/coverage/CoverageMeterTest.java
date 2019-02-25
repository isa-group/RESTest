package es.us.isa.rester.coverage;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.rester.coverage.CoverageGatherer;
import es.us.isa.rester.coverage.CoverageMeter;
import static es.us.isa.rester.coverage.CriterionType.*;
import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.testcases.TestCase;

import io.swagger.models.HttpMethod;

public class CoverageMeterTest {

    @Test
    public void debugging() {
        String oasPath = "src/test/resources/specifications/petstore.json";
        OpenAPISpecification oas = new OpenAPISpecification(oasPath);

        List<CriterionType> coverageCriterionTypes = new ArrayList<>();
        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
        coverageCriterionTypes.add(AUTHENTICATION);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        coverageCriterionTypes.add(RESPONSE_BODY_PROPERTIES);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);
        CoverageGatherer covGath = new CoverageGatherer(oas, coverageCriterionTypes);

        CoverageMeter covMeter = new CoverageMeter(covGath);

        List<TestCase> testSuite = new ArrayList<>();
        TestCase testCase1 = new TestCase("addPet", "/pet", HttpMethod.POST);
        TestCase testCase2 = new TestCase("findPetsByStatus", "/pet/findByStatus", HttpMethod.GET);
        testCase2.addQueryParameter("status", "available");
        testCase2.setAuthentication("petstore_auth");
        TestCase testCase3 = new TestCase("uploadFile", "/pet/{petId}/uploadImage", HttpMethod.POST);
        testCase3.addQueryParameter("destinationFormat", "JPG");
        testCase3.addQueryParameter("convertToJPG", "false");

        testSuite.add(testCase1);
        testSuite.add(testCase2);
        testSuite.add(testCase3);

        covMeter.setTestSuite(testSuite);

        System.out.println("covMeter.getTotalCoverage(): " + covMeter.getTotalCoverage());
        System.out.println("covMeter.getInputCoverage(): " + covMeter.getInputCoverage());
        System.out.println("covMeter.getCriterionTypeCoverage(PATH): " + covMeter.getCriterionTypeCoverage(PATH));
        System.out.println("covMeter.getCriterionTypeCoverage(OPERATION): " + covMeter.getCriterionTypeCoverage(OPERATION));
        System.out.println("covMeter.getCriterionTypeCoverage(PARAMETER): " + covMeter.getCriterionTypeCoverage(PARAMETER));
        System.out.println("covMeter.getCriterionTypeCoverage(PARAMETER_VALUE): " + covMeter.getCriterionTypeCoverage(PARAMETER_VALUE));
        System.out.println("covMeter.getCriterionTypeCoverage(INPUT_CONTENT_TYPE): " + covMeter.getCriterionTypeCoverage(INPUT_CONTENT_TYPE));
        System.out.println("covMeter.getCriterionTypeCoverage(AUTHENTICATION): " + covMeter.getCriterionTypeCoverage(AUTHENTICATION));
        System.out.println("covMeter.getCriterionCoverage(OPERATION, /pet: " + covMeter.getCriterionCoverage(OPERATION, "/pet"));
        System.out.println("covMeter.getCriterionCoverage(PARAMETER, /pet/findByStatus->GET: " + covMeter.getCriterionCoverage(PARAMETER, "/pet/findByStatus->GET"));
        System.out.println("covMeter.getCriterionCoverage(AUTHENTICATION, /pet/findByStatus->GET: " + covMeter.getCriterionCoverage(AUTHENTICATION, "/pet/findByStatus->GET"));
        System.out.println("covMeter.getCriterionCoverage(PARAMETER, /pet/{petId}/uploadImage->POST: " + covMeter.getCriterionCoverage(PARAMETER, "/pet/{petId}/uploadImage->POST"));
        System.out.println("covMeter.getCriterionCoverage(PARAMETER_VALUE, /pet/{petId}/uploadImage->POST->destinationFormat: " + covMeter.getCriterionCoverage(PARAMETER_VALUE, "/pet/{petId}/uploadImage->POST->destinationFormat"));

        System.out.println(covGath.getCoverageCriteria().get(1).getAllElements().get(1));
    }
}