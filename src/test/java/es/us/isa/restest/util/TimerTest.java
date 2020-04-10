package es.us.isa.restest.util;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class TimerTest {

    @Test
    public void timerTest() throws InterruptedException {
        Timer timer = new Timer();
        assertEquals("The timer should have no info", "", timer.info());
        timer.startCounting("Test case generation");
        Thread.sleep(500);
        timer.startCounting("Test case execution");
        Thread.sleep(1000);
        timer.stopCounting();
        Thread.sleep(1500);
        timer.startCounting("Report generation");
        timer.stopCounting();
        System.out.println(timer.info());
        assertEquals("The timer should have 3 counters", 3, timer.getCounters().size());
        assertTrue("The first counter should have lasted 500ms at least", timer.getCounters().get(0).getDuration() >= 500);
        assertTrue("The second counter should have lasted 1000ms at least", timer.getCounters().get(1).getDuration() >= 1000);
        assertTrue("The first counter should have lasted 0ms at least", timer.getCounters().get(2).getDuration() >= 0);
    }
}
