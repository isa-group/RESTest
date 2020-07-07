/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.Operation;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import static es.us.isa.restest.testcases.TestCase.checkFulfillsDependencies;
import static es.us.isa.restest.testcases.TestCase.getFaultyReasons;
import static es.us.isa.restest.util.SpecificationVisitor.findParameter;
import static es.us.isa.restest.util.SpecificationVisitor.getRequiredParameters;

/**
 *
 * @author japar
 */
public abstract class AbstractAPITestCaseMutationOperator implements MutationOperator<RestfulAPITestSuiteSolution> {

    private double mutationProbability;
    private PseudoRandomGenerator randomGenerator;
    protected boolean mutationApplied;

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

    protected Collection<ParameterFeatures> getAllPresentParameters(TestCase testCase) {
        Set<ParameterFeatures> parameters = new HashSet<>();
        for (String pathParam : testCase.getPathParameters().keySet())
            parameters.add(new ParameterFeatures(pathParam, "path", null));
        for (String queryParam : testCase.getQueryParameters().keySet())
            parameters.add(new ParameterFeatures(queryParam, "query", null));
        for (String headerParam : testCase.getHeaderParameters().keySet())
            parameters.add(new ParameterFeatures(headerParam, "header", null));
        for (String formParam : testCase.getFormParameters().keySet())
            parameters.add(new ParameterFeatures(formParam, "formData", null));
        // TODO: Support body parameter mutation:
        /*if(testCase.getBodyParameter()!=null) {            
            parameterNames.add("body");
        }*/
        return parameters;
    }

    protected abstract void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution);
}
