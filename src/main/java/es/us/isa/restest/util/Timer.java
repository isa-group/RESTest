package es.us.isa.restest.util;

import java.util.*;

public class Timer {

    private static Map<String, List<Long>> counters = new HashMap<>();

    public static Map<String, List<Long>> getCounters() {
        return counters;
    }

    public static void startCounting(TestStep step) {
        counters.putIfAbsent(step.name, new ArrayList<>());
        List<Long> stepMeasures = counters.get(step.name);
        if (stepMeasures.size() > 0 && stepMeasures.get(stepMeasures.size()-1) < 0)
            throw new IllegalStateException("A timer of the same type can only be started once before it's stopped.");
        stepMeasures.add(-new Date().getTime());
    }

    public static void stopCounting(TestStep step) {
        Long stopTime = new Date().getTime();
        List<Long> stepMeasures = counters.get(step.name);
        stepMeasures.set(stepMeasures.size()-1, stopTime+stepMeasures.get(stepMeasures.size()-1));
    }

    public enum TestStep {
        TEST_CASE_GENERATION("Test case generation"),
        TEST_SUITE_GENERATION("Test suite generation"),
        TEST_SUITE_EXECUTION("Test suite execution"),
        ALL("Whole process");

        private String name;

        TestStep(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
