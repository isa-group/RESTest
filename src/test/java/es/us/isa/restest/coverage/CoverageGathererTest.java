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

        System.out.println(covGath.getCoverageCriteria().get(1).getElements().entrySet().iterator().next().getKey());
    }
}