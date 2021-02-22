package es.us.isa.restest.testcases.diversity;

import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.TestManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

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

        assertEquals(0.15,  d1, 0.01);

        t1.add(0, tcs.get(2));
        Double d2 = diversity.evaluate(t1);

        assertEquals(0.71,  d2, 0.01);

        t1.add(0, tcs.get(3));
        Double d3 = diversity.evaluate(t1);

        assertEquals(0.69,  d3, 0.01);

        List<TestCase> t2 = tcs.subList(1, 3);
        Double d4 = diversity.evaluate(t2);

        assertEquals(1., d4, 0.);
    }

    @Test
    public void diversityNotNormalizeTest() {
        Diversity diversity = new Diversity(SimilarityMeter.METRIC.LEVENSHTEIN, false);

        List<TestCase> t1 = new ArrayList<>(tcs.subList(0, 2));
        Double d1 = diversity.evaluate(t1);

        assertEquals(0.15,  d1, 0.01);

        t1.add(0, tcs.get(2));
        Double d2 = diversity.evaluate(t1);

        assertEquals(2.15,  d2, 0.01);

        t1.add(0, tcs.get(3));
        Double d3 = diversity.evaluate(t1);

        assertEquals(4.19,  d3, 0.01);

        List<TestCase> t2 = tcs.subList(1, 3);
        Double d4 = diversity.evaluate(t2);

        assertEquals(1., d4, 0.);


    }


}
