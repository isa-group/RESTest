package es.us.isa.restest.coverage;

import static es.us.isa.restest.coverage.CriterionType.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import es.us.isa.restest.coverage.CoverageGatherer;
import es.us.isa.restest.coverage.CriterionType;
import es.us.isa.restest.specification.OpenAPISpecification;

public class CoverageGathererTest {

    @Test
    public void coverageGathererTest() {
        String oasPath = "src/test/resources/Bikewise/swagger.yaml";
        OpenAPISpecification oas = new OpenAPISpecification(oasPath);
        List<CriterionType> coverageCriterionTypes = new ArrayList<>();

        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        coverageCriterionTypes.add(RESPONSE_BODY_PROPERTIES);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);

        CoverageGatherer covGath = new CoverageGatherer(oas, coverageCriterionTypes);

        assertEquals("There should be 9 criterion types", 9, covGath.getCoverageCriterionTypes().size());
        assertEquals("There should be 22 criteria", 22, covGath.getCoverageCriteria().size());
        assertEquals("There should be 63 elements to cover", 63, covGath.getCoverageCriteria().stream().mapToInt(criterion -> criterion.getElements().size()).sum());
        for (CoverageCriterion criterion: covGath.getCoverageCriteria()) {
            criterion.getElements().forEach((element, isCovered) -> {
                assertFalse("The element " + criterion.getRootPath() + "->" + element + " should not be covered", isCovered);
            });
        }

    }

    @Test
    public void coverageGathererComplexParametersTest() {
        String oasPath = "src/test/resources/GitHub/swagger_forTestSuite.yaml";
        OpenAPISpecification oas = new OpenAPISpecification(oasPath);
        List<CriterionType> coverageCriterionTypes = new ArrayList<>();

        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        coverageCriterionTypes.add(RESPONSE_BODY_PROPERTIES);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);

        CoverageGatherer covGath = new CoverageGatherer(oas, coverageCriterionTypes);

        System.out.println("As long as this is printed, this test cases passes (no exceptions thrown).");

    }

    @Test
    public void coverageGathererComplexComplexRefResponseBodyPropertiesTest() {
        String oasPath = "src/test/resources/restest-test-resources/swagger-dhl.yaml";
        OpenAPISpecification oas = new OpenAPISpecification(oasPath);
        List<CriterionType> coverageCriterionTypes = new ArrayList<>();

        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        coverageCriterionTypes.add(RESPONSE_BODY_PROPERTIES);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);

        CoverageGatherer covGath = new CoverageGatherer(oas, coverageCriterionTypes);

        assertTrue(covGath.getCoverageCriteria().stream().filter(cc -> cc.getType() == RESPONSE_BODY_PROPERTIES && cc.getRootPath().equals("/find-by-keyword-id->GET->200->{place{address{")).findFirst().get().getElements().size()==4);
        assertFalse(covGath.getCoverageCriteria().stream().filter(cc -> cc.getType() == RESPONSE_BODY_PROPERTIES && cc.getRootPath().equals("/find-by-keyword-id->GET->200->{place{address{countryCode")).findFirst().isPresent());

        System.out.println("As long as this is printed, this test cases passes (no exceptions thrown).");

    }
}