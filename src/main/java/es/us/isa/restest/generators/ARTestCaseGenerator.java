package es.us.isa.restest.generators;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.objectfunction.SimilarityMeter;
import es.us.isa.restest.util.RESTestException;

import java.util.Collection;

//TODO: ART implementation

public class ARTestCaseGenerator extends ConstraintBasedTestCaseGenerator {

    private SimilarityMeter similarityMeter;
    private Integer testsPerIteration = 100;

    public ARTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
        super(spec, conf, nTests);
    }

    @Override
    public Collection<TestCase> generate(Collection<TestConfigurationFilter> filters) throws RESTestException {
        return null;
    }

    @Override
    protected Collection<TestCase> generateOperationTestCases(Operation testOperation) throws RESTestException {


        return null;
    }

    @Override
    public TestCase generateNextTestCase(Operation testOperation) throws RESTestException {
        return null;
    }

    public SimilarityMeter getSimilarityMeter() {
        return similarityMeter;
    }

    public void setSimilarityMeter(SimilarityMeter.METRIC similarityMetric) {
        this.similarityMeter = new SimilarityMeter(similarityMetric);
    }

    public Integer getTestsPerIteration() {
        return testsPerIteration;
    }

    public void setTestsPerIteration(Integer testsPerIteration) {
        this.testsPerIteration = testsPerIteration;
    }
}
