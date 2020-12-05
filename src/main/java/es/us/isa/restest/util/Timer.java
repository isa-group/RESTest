package es.us.isa.restest.util;

import java.util.*;

import static es.us.isa.restest.util.CSVManager.createCSVwithHeader;
import static es.us.isa.restest.util.CSVManager.writeCSVRow;
import static es.us.isa.restest.util.FileManager.checkIfExists;

public class Timer {

    private static Map<String, List<Long>> counters = new HashMap<>();

    public static Map<String, List<Long>> getCounters() {
        return counters;
    }

    public static void resetCounters() { counters = new HashMap<>(); }

    public static void startCounting(TestStep step) {
        counters.putIfAbsent(step.name, new ArrayList<>());
        List<Long> stepMeasures = counters.get(step.name);
        if (stepMeasures.size() > 0 && stepMeasures.get(stepMeasures.size()-1) < 0)
            stepMeasures.remove(stepMeasures.size() - 1);
//            throw new IllegalStateException("A timer of the same type can only be started once before it's stopped.");
        stepMeasures.add(-new Date().getTime());
    }

    public static void stopCounting(TestStep step) {
        Long stopTime = new Date().getTime();
        List<Long> stepMeasures = counters.get(step.name);
        stepMeasures.set(stepMeasures.size()-1, stopTime+stepMeasures.get(stepMeasures.size()-1));
    }

    public static void exportToCSV(String path, Integer iterations) {
        if (!checkIfExists(path)) { // If the file doesn't exist, create it (only once)
            StringBuilder header = new StringBuilder();
            boolean first = true;

            for(String counterName : counters.keySet()) {
                if (first) {
                    header.append(counterName);
                    first = false;
                } else header.append(",").append(counterName);
            }

            createCSVwithHeader(path, header.toString());


            for(int i = 0; i < iterations; i++) {
                writeRow(path, i);
            }
        }
    }

    private static void writeRow(String path, int i) {
        StringBuilder row = new StringBuilder();
        boolean first = true;

        for(Map.Entry<String, List<Long>> entry : counters.entrySet()) {
            Long value;

            if(entry.getKey().equals("Whole process")) {
                value = entry.getValue().get(0);
            } else value = entry.getValue().get(i);

            if (first) {
                row.append(value);
                first = false;
            } else row.append(",").append(value);
        }

        writeCSVRow(path, row.toString());
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
