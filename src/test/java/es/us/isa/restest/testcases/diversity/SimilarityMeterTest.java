package es.us.isa.restest.testcases.diversity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimilarityMeterTest {

    @Test
    public void jaccardSimilarityTest() {
        SimilarityMeter meter = new SimilarityMeter(SimilarityMeter.METRIC.JACCARD);
        Double sim1 = meter.apply("abcdef", "abcdef");
        assertEquals(1., sim1, 0.);

        Double sim2 = meter.apply("abcdef", "ghijkl");
        assertEquals(0., sim2, 0.);

        Double sim3 = meter.apply("trato", "trazo");
        assertEquals(.8, sim3, 0.);
    }

    @Test
    public void jaroWinklerSimilarityTest() {
        SimilarityMeter meter = new SimilarityMeter(SimilarityMeter.METRIC.JARO_WINKLER);
        Double sim1 = meter.apply("abcdef", "abcdef");
        assertEquals(1., sim1, 0.);

        Double sim2 = meter.apply("casa", "monte");
        assertEquals(0., sim2, 0.);

        Double sim3 = meter.apply("trato", "trazo");
        assertEquals(0.9066666666666667, sim3, 0.);
    }

    @Test
    public void levenshteinDistanceTest() {
        SimilarityMeter meter = new SimilarityMeter(SimilarityMeter.METRIC.LEVENSHTEIN);
        Double sim1 = meter.apply("abcdef", "abcdef");
        assertEquals(1., sim1, 0.);

        Double sim2 = meter.apply("montejude", "casa");
        assertEquals(0., sim2, 0.);

        Double sim3 = meter.apply("torta", "trato");
        assertEquals(0.4, sim3, 0.);
    }
}
