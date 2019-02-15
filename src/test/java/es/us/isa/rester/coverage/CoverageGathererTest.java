package es.us.isa.rester.coverage;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.auth.AUTH;
import org.junit.Test;

import es.us.isa.rester.coverage.CoverageGatherer;
import static es.us.isa.rester.coverage.CriterionType.*;
import es.us.isa.rester.specification.OpenAPISpecification;

public class CoverageGathererTest {

    @Test
    public void getPathsCriterion() {
        String oasPath = "src/test/resources/specifications/petstore.json";
        OpenAPISpecification oas = new OpenAPISpecification(oasPath);

        List<CriterionType> coverageCriterionTypes = new ArrayList<>();
        coverageCriterionTypes.add(PATH);
        coverageCriterionTypes.add(OPERATION);
        coverageCriterionTypes.add(PARAMETER);
        coverageCriterionTypes.add(PARAMETER_VALUE);
        coverageCriterionTypes.add(INPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(OUTPUT_CONTENT_TYPE);
        coverageCriterionTypes.add(AUTHENTICATION);
        coverageCriterionTypes.add(STATUS_CODE);
        coverageCriterionTypes.add(STATUS_CODE_CLASS);
        CoverageGatherer covGath = new CoverageGatherer(oas, coverageCriterionTypes);

        System.out.println(covGath.getCoverageCriteria().get(1).getAllElements().get(1));
    }
}