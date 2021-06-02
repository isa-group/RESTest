package es.us.isa.restest.inputs.semantic;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static es.us.isa.restest.inputs.semantic.NLPUtils.extractPredicateCandidatesFromDescription;
import static es.us.isa.restest.inputs.semantic.NLPUtils.posTagging;
import static org.junit.Assert.assertTrue;

public class MatchingRulesTest {

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule1() {
        Map<Double, Set<String>> descriptionCandidates = new HashMap<>();

        String parameterName1 = "currency";
        String parameterDescription1 = "A valid currency code";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName1, parameterDescription1);

        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.keySet().size() == 1);
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(3.0).contains("currencycode"));

    }

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule2() {
        Map<Double, Set<String>> descriptionCandidates = new HashMap<>();

        String parameterName2 = "lang";
        String parameterDescription2 = "A valid language code";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName2, parameterDescription2);

        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.keySet().size() == 1);
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("langcode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("languagecode"));

    }

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule3() {
        Map<Double, Set<String>> descriptionCandidates = new HashMap<>();

        String parameterName3 = "foo";
        String parameterDescription3 = "A city code or a imdb id";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName3, parameterDescription3);

        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.keySet().size()==1);
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("citycode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("imdbid"));

    }

    @Test
    public void testExtractPredicateCandidatesFromDescriptionInPlural() {
        Map<Double, Set<String>> descriptionCandidates = new HashMap<>();

        // RULE 1
        String parameterName1Plural = "currency";
        String parameterDescription1Plural = "A valid currencies codes";

//        List<String> descriptionPosTagged = posTagging(parameterDescription1Plural);
//        System.out.println(descriptionPosTagged);

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName1Plural, parameterDescription1Plural);

        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.keySet().size() == 1);
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(3.0).contains("currencycode"));

        // RULE 2

        String parameterName2Plural = "lang";
        String parameterDescription2Plural = "A valid languages codes";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName2Plural, parameterDescription2Plural);

        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.keySet().size() == 1);
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("langcode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("languagecode"));

        // RULE 3
        String parameterName3Plural = "foo";
        String parameterDescription3Plural = "A cities codes or a imdb ids";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName3Plural, parameterDescription3Plural);

        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.keySet().size()==1);
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("citycode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("imdbid"));

    }

}
