package es.us.isa.restest.util;

import java.util.*;

public class Timer {

    private static Map<String, Long> counters = new HashMap<>();
    private static Map<TestStep, Integer> indexes = new HashMap<>();

    public static Map<String, Long> getCounters() {
        return counters;
    }

    public static void startCounting(TestStep step) {
        if (indexes.get(step) != null && counters.get(step.name + " - " + indexes.get(step)) < 0)
            throw new IllegalStateException("A timer of the same type can only be started once.");
        indexes.putIfAbsent(step, 0); // Initialize counter index
        indexes.put(step, indexes.get(step)+1);
        counters.put(getCounterName(step), -new Date().getTime());
    }

    public static void stopCounting(TestStep step) {
        Long stopTime = new Date().getTime();
        counters.put(getCounterName(step), stopTime+counters.get(getCounterName(step)));
    }

    private static String getCounterName(TestStep step) {
        return step.name + " - " + indexes.get(step);
    }

    public enum TestStep {
        TEST_GENERATION("Test case generation"), TEST_EXECUTION("Test case execution"), ALL("Whole process");

        private String name;

        TestStep(String name) {
            this.name = name;
        }
    }

}
