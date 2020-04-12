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
        assertEquals("The timer should be empty", 0, Timer.getCounters().size());
        Timer.startCounting(Timer.TestStep.ALL);
        Thread.sleep(500);
        Timer.startCounting(Timer.TestStep.TEST_GENERATION);
        Thread.sleep(1000);
        boolean throwsException = false;
        try {
            Timer.startCounting(Timer.TestStep.TEST_GENERATION);
        } catch (IllegalStateException e) {
            throwsException = true;
        }
        assertTrue(throwsException);
        Timer.stopCounting(Timer.TestStep.TEST_GENERATION);
        Thread.sleep(1500);
        Timer.startCounting(Timer.TestStep.TEST_EXECUTION);
        Timer.stopCounting(Timer.TestStep.TEST_EXECUTION);
        Timer.startCounting(Timer.TestStep.TEST_GENERATION);
        Thread.sleep(200);
        Timer.startCounting(Timer.TestStep.TEST_EXECUTION);
        Thread.sleep(200);
        Timer.stopCounting(Timer.TestStep.TEST_GENERATION);
        Timer.stopCounting(Timer.TestStep.TEST_EXECUTION);
        Timer.stopCounting(Timer.TestStep.ALL);

        System.out.println(Timer.getCounters());
        assertEquals("The timer should have 5 counters", 5, Timer.getCounters().size());
        assertTrue("The ALL counter should have lasted 3000ms at least", Timer.getCounters().get("Whole process - 1") >= 3400);
        assertTrue("The first TEST_GENERATION counter should have lasted 1000ms at least", Timer.getCounters().get("Test case generation - 1") >= 1000);
        assertTrue("The first TEST_EXECUTION counter should have lasted 0ms at least", Timer.getCounters().get("Test case execution - 1") >= 0);
        assertTrue("The second TEST_GENERATION counter should have lasted 1000ms at least", Timer.getCounters().get("Test case generation - 2") >= 400);
        assertTrue("The second TEST_EXECUTION counter should have lasted 0ms at least", Timer.getCounters().get("Test case execution - 2") >= 200);

        ObjectMapper mapper = new ObjectMapper();
        String timePath = PropertyManager.readProperty("data.tests.dir") + "/" + PropertyManager.readProperty("data.tests.time");
        try {
            mapper.writeValue(new File(timePath), Timer.getCounters());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
