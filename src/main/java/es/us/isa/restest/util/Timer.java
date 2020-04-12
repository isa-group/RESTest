package es.us.isa.restest.util;

import java.util.*;

public class Timer {

    private Map<String, Long> counters;

    public Timer() {
        counters = new HashMap<>();
    }

    public void startCounting(String message) {
        counters.put(message, new Date().getTime());
    }

    public void stopCounting(String message) {
        counters.put(message, new Date().getTime() - counters.get(message));
    }

    public Map<String, Long> getCounters() {
        return counters;
    }

    public void setCounters(Map<String, Long> counters) {
        this.counters = counters;
    }

}
