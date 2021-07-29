package es.us.isa.restest.inputs.semantic;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static es.us.isa.restest.inputs.semantic.NLPUtils.extractPredicateCandidatesFromDescription;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MatchingRulesTest {

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule1() {
        String parameterName1 = "currency";
        String parameterDescription1 = "A valid currency code";

        Map<Double, Set<String>> descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName1, parameterDescription1);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(3.0).contains("currencycode"));

    }

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule2() {
        String parameterName2 = "lang";
        String parameterDescription2 = "A valid language code";

        Map<Double, Set<String>> descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName2, parameterDescription2);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("langcode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("languagecode"));

    }

    @Test
    public void testExtractPredicateCandidatesFromDescriptionRule3() {
        String parameterName3 = "foo";
        String parameterDescription3 = "A city code or a imdb id";

        Map<Double, Set<String>> descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName3, parameterDescription3);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("citycode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("imdbid"));

    }

    @Test
    public void testExtractPredicateCandidatesFromDescriptionInPlural() {
        // RULE 1
        String parameterName1Plural = "currency";
        String parameterDescription1Plural = "A valid currencies codes";

//        List<String> descriptionPosTagged = posTagging(parameterDescription1Plural);
//        System.out.println(descriptionPosTagged);

        Map<Double, Set<String>> descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName1Plural, parameterDescription1Plural);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(3.0).contains("currencycode"));

        // RULE 2
        String parameterName2Plural = "lang";
        String parameterDescription2Plural = "A valid languages codes";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName2Plural, parameterDescription2Plural);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("langcode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(2.0).contains("languagecode"));

        // RULE 3
        String parameterName3Plural = "foo";
        String parameterDescription3Plural = "A cities codes or a imdb ids";

        descriptionCandidates = extractPredicateCandidatesFromDescription(parameterName3Plural, parameterDescription3Plural);

        assertEquals("Error extracting predicate candidates from description", 1, descriptionCandidates.keySet().size());
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("citycode"));
        assertTrue("Error extracting predicate candidates from description", descriptionCandidates.get(1.0).contains("imdbid"));

    }

}
