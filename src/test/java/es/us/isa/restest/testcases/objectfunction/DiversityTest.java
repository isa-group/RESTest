package es.us.isa.restest.testcases.objectfunction;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.TestManager;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DiversityTest {

    public List<TestCase> tcs;

     @Before
     public void setUp() {
         tcs = TestManager.getTestCases("src/test/resources/csvData/testCasesDiversityTest.csv");
     }

    @Test
    public void diversityNormalizeTest() {
        Diversity diversity = new Diversity(SimilarityMeter.METRIC.LEVENSHTEIN, true);

        List<TestCase> t1 = new ArrayList<>(tcs.subList(0, 2));
        Double d1 = diversity.evaluate(t1);

        MatcherAssert.assertThat(d1, Matchers.closeTo(0.15, 0.01));

        t1.add(0, tcs.get(2));
        Double d2 = diversity.evaluate(t1);

        MatcherAssert.assertThat(d2, Matchers.closeTo(0.71, 0.01));

        t1.add(0, tcs.get(3));
        Double d3 = diversity.evaluate(t1);

        MatcherAssert.assertThat(d3, Matchers.closeTo(0.69, 0.01));

        List<TestCase> t2 = tcs.subList(1, 3);
        Double d4 = diversity.evaluate(t2);

        MatcherAssert.assertThat(d4, Matchers.is(1.));
    }

    @Test
    public void diversityNotNormalizeTest() {
        Diversity diversity = new Diversity(SimilarityMeter.METRIC.LEVENSHTEIN, false);

        List<TestCase> t1 = new ArrayList<>(tcs.subList(0, 2));
        Double d1 = diversity.evaluate(t1);

        MatcherAssert.assertThat(d1, Matchers.closeTo(0.15, 0.01));

        t1.add(0, tcs.get(2));
        Double d2 = diversity.evaluate(t1);

        MatcherAssert.assertThat(d2, Matchers.closeTo(2.15, 0.01));

        t1.add(0, tcs.get(3));
        Double d3 = diversity.evaluate(t1);

        MatcherAssert.assertThat(d3, Matchers.closeTo(4.19, 0.01));

        List<TestCase> t2 = tcs.subList(1, 3);
        Double d4 = diversity.evaluate(t2);

        MatcherAssert.assertThat(d4, Matchers.is(1.));


    }


}
