package es.us.isa.restest.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Timer {
    private List<TimeEntry> counters;
    private TimeEntry currentCounter;

    public Timer() {
        counters = new ArrayList<>();
    }

    public void startCounting(String message) {
        if (currentCounter != null)
            currentCounter.end();
        currentCounter = new TimeEntry(message);
        counters.add(currentCounter);
    }

    public void stopCounting() {
        currentCounter.end();
        currentCounter = null;
    }

    public String info() {
        StringBuilder info = new StringBuilder();
        for (TimeEntry counter: counters) {
            info.append(counter.message).append(": ").append(counter.duration).append("\n");
        }
        return info.toString();
    }

    public List<TimeEntry> getCounters() {
        return counters;
    }

    public void setCounters(List<TimeEntry> counters) {
        this.counters = counters;
    }

    public TimeEntry getCurrentCounter() {
        return currentCounter;
    }

    public void setCurrentCounter(TimeEntry currentCounter) {
        this.currentCounter = currentCounter;
    }

    public class TimeEntry {
        private String message;
        private Date start;
        private Date stop;
        private Long duration;

        public TimeEntry(String message) {
            this.message = message;
            this.start = new Date();
        }

        public void end() {
            this.stop = new Date();
            this.duration = this.stop.getTime() - this.start.getTime();
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Date getStart() {
            return start;
        }

        public void setStart(Date start) {
            this.start = start;
        }

        public Date getStop() {
            return stop;
        }

        public void setStop(Date stop) {
            this.stop = stop;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }
    }
}
