/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

/**
 *
 * @author japar
 */
public abstract class AbstractAPITestCaseMutationOperator implements MutationOperator<RestfulAPITestSuiteSolution> {

    private double mutationProbability;
    private PseudoRandomGenerator randomGenerator;

    public AbstractAPITestCaseMutationOperator(double mutationProbability, PseudoRandomGenerator randomGenerator) {
        this.mutationProbability = mutationProbability;
        this.randomGenerator = randomGenerator;
    }

    /* Getter */
    public double getMutationProbability() {
        return mutationProbability;
    }

    /* Setters */
    public void setMutationProbability(double mutationProbability) {
        this.mutationProbability = mutationProbability;
    }

    public PseudoRandomGenerator getRandomGenerator() {
        return randomGenerator;
    }

    @Override
    public RestfulAPITestSuiteSolution execute(RestfulAPITestSuiteSolution solution) {
        assert (solution != null);

        doMutation(getMutationProbability(), solution);
        return solution;
    }

    protected Collection<String> getAllPresentParameters(TestCase testCase) {
        Set<String> parameterNames = new HashSet<>();
        for (String pathParam : testCase.getPathParameters().keySet()) {
            assert (!parameterNames.contains(pathParam));
            parameterNames.add(pathParam);
        }
        for (String queryParam : testCase.getQueryParameters().keySet()) {
            assert (!parameterNames.contains(queryParam));
            parameterNames.add(queryParam);
        }
        for (String headerParam : testCase.getHeaderParameters().keySet()) {
            assert (!parameterNames.contains(headerParam));
            parameterNames.add(headerParam);
        }
        for (String formParam : testCase.getFormParameters().keySet()) {
            assert (!parameterNames.contains(formParam));
            parameterNames.add(formParam);
        }
        // TODO: Support body parameter mutation:
        /*if(testCase.getBodyParameter()!=null) {            
            parameterNames.add("body");
        }*/
        return parameterNames;
    }

    protected abstract void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution);
}
