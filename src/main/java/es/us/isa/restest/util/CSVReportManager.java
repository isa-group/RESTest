package es.us.isa.restest.util;

/**
 * Class for managing the generation of test reports in CSV
 *
 * @author Alberto Martin-Lopez
 */
public class CSVReportManager {

    private String testDataDir;
    private String coverageDataDir;
    private boolean enableStats = true;

    private boolean enableInputCoverage = true;

    public CSVReportManager() {
        this(PropertyManager.readProperty("data.tests.dir"), PropertyManager.readProperty("data.coverage.tests.dir"));
    }

    public CSVReportManager(String testDataDir, String coverageDataDir) {
        this.testDataDir = testDataDir;
        this.coverageDataDir = coverageDataDir;
    }

    public String getTestDataDir() {
        return testDataDir;
    }

    public void setTestDataDir(String testDataDir) {
        this.testDataDir = testDataDir;
    }

    public String getCoverageDataDir() {
        return coverageDataDir;
    }

    public void setCoverageDataDir(String coverageDataDir) {
        this.coverageDataDir = coverageDataDir;
    }

    public boolean getEnableStats() {
        return enableStats;
    }

    public void setEnableStats(boolean enableStats) {
        this.enableStats = enableStats;
    }

    public boolean getEnableInputCoverage() {
        return enableInputCoverage;
    }

    public void setEnableInputCoverage(boolean enableInputCoverage) {
        this.enableInputCoverage = enableInputCoverage;
    }
}
