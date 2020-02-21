package es.us.isa.restest.coverage;

public class CoverageCriterionResult {

    String coverageCriterion;
    Float coverage;

    public CoverageCriterionResult(String coverageCriterion, Float coverage) {
        this.coverageCriterion = coverageCriterion;
        this.coverage = coverage;
    }

    public String getCoverageCriterion() {
        return coverageCriterion;
    }

    public void setCoverageCriterion(String coverageCriterion) {
        this.coverageCriterion = coverageCriterion;
    }

    public Float getCoverage() {
        return coverage;
    }

    public void setCoverage(Float coverage) {
        this.coverage = coverage;
    }
}
