package es.us.isa.restest.testcases.objectfunction;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

public class SimilarityMeterTest {

    @Test
    public void jaccardSimilarityTest() {
        SimilarityMeter meter = new SimilarityMeter(SimilarityMeter.METRIC.JACCARD);
        Double sim1 = meter.apply("abcdef", "abcdef");
        MatcherAssert.assertThat(sim1, Matchers.is(1.));

        Double sim2 = meter.apply("abcdef", "ghijkl");
        MatcherAssert.assertThat(sim2, Matchers.is(0.));

        Double sim3 = meter.apply("trato", "trazo");
        MatcherAssert.assertThat(sim3, Matchers.is(.8));
    }

    @Test
    public void jaroWinklerSimilarityTest() {
        SimilarityMeter meter = new SimilarityMeter(SimilarityMeter.METRIC.JARO_WINKLER);
        Double sim1 = meter.apply("abcdef", "abcdef");
        MatcherAssert.assertThat(sim1, Matchers.is(1.));

        Double sim2 = meter.apply("casa", "monte");
        MatcherAssert.assertThat(sim2, Matchers.is(0.));

        Double sim3 = meter.apply("trato", "trazo");
        MatcherAssert.assertThat(sim3, Matchers.is(0.9066666666666667));
    }

    @Test
    public void levenshteinDistanceTest() {
        SimilarityMeter meter = new SimilarityMeter(SimilarityMeter.METRIC.LEVENSHTEIN);
        Double sim1 = meter.apply("abcdef", "abcdef");
        MatcherAssert.assertThat(sim1, Matchers.is(1.));

        Double sim2 = meter.apply("montejude", "casa");
        MatcherAssert.assertThat(sim2, Matchers.is(0.));

        Double sim3 = meter.apply("torta", "trato");
        MatcherAssert.assertThat(sim3, Matchers.is(.4));
    }
}
