package es.us.isa.restest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class TimerTest {

    @Test
    public void timerTest() throws InterruptedException {
        Timer timer = new Timer();
        assertEquals("The timer should be empty", 0, timer.getCounters().size());
        timer.startCounting("Whole process");
        Thread.sleep(500);
        timer.startCounting("Test case generation");
        Thread.sleep(1000);
        timer.stopCounting("Test case generation");
        Thread.sleep(1500);
        timer.startCounting("Test case execution");
        timer.stopCounting("Test case execution");
        timer.stopCounting("Whole process");
        System.out.println(timer.getCounters());
        assertEquals("The timer should have 3 counters", 3, timer.getCounters().size());
        assertTrue("The first counter should have lasted 3000ms at least", timer.getCounters().get("Whole process") >= 3000);
        assertTrue("The second counter should have lasted 1000ms at least", timer.getCounters().get("Test case generation") >= 1000);
        assertTrue("The first counter should have lasted 0ms at least", timer.getCounters().get("Test case execution") >= 0);

        ObjectMapper mapper = new ObjectMapper();
        String timePath = PropertyManager.readProperty("data.tests.dir") + "/" + PropertyManager.readProperty("data.tests.time");
        try {
            mapper.writeValue(new File(timePath), timer.getCounters());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
