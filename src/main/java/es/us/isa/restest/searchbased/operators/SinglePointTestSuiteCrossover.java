/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteGenerationProblem;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import java.util.ArrayList;
import java.util.List;

import es.us.isa.restest.testcases.TestResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;


/**
 *
 * @author japarejo
 */
public class SinglePointTestSuiteCrossover extends AbstractCrossoverOperator {

    private static final Logger logger = LogManager.getLogger(SinglePointTestSuiteCrossover.class.getName());

    public SinglePointTestSuiteCrossover(double crossoverProbability) {
        super(crossoverProbability);
    }

    public SinglePointTestSuiteCrossover(double crossoverProbability, RandomGenerator<Double> randomGenerator) {
        super(crossoverProbability, randomGenerator);
    }

    public SinglePointTestSuiteCrossover(
            double crossoverProbability,
            RandomGenerator<Double> crossoverRandomGenerator,
            BoundedRandomGenerator<Integer> pointRandomGenerator) {
        super(crossoverProbability, crossoverRandomGenerator, pointRandomGenerator);
    }

    public double getCrossoverProbability() {
        return crossoverProbability;
    }

    public void setCrossoverProbability(double crossoverProbability) {
        this.crossoverProbability = crossoverProbability;
    }

    /**
    * Perform the crossover operation.
    *
    * @param probability Crossover setProbability
    * @param parent1 The first parent
    * @param parent2 The second parent
    * @return An array containing the two offspring
    */
    @Override
    protected List<RestfulAPITestSuiteSolution> doCrossover(double probability, RestfulAPITestSuiteSolution parent1, RestfulAPITestSuiteSolution parent2) {
        List<RestfulAPITestSuiteSolution> offspring = new ArrayList<>(2);
        RestfulAPITestSuiteSolution offspring1=(RestfulAPITestSuiteSolution) parent1.copy();
        RestfulAPITestSuiteSolution offspring2=(RestfulAPITestSuiteSolution) parent2.copy();
        offspring.add(offspring1);
        offspring.add(offspring2);

    if (crossoverRandomGenerator.getRandomValue() < probability) {
        // 1. Get the total number of params
        int totalNumberOfVars= Math.min(parent1.getNumberOfVariables(),parent2.getNumberOfVariables());

        // 2. Calculate the point to make the crossover
        int crossoverPoint = pointRandomGenerator.getRandomValue(0, totalNumberOfVars - 1);


        // 3. Apply the crossover to the variable;
        TestCase testCase;
        TestResult testResult;
        for(int i=0;i<=crossoverPoint;i++){
            testCase=offspring1.getVariable(i);
            testResult=offspring1.getTestResult(testCase.getId());
            offspring1.replaceTestResult(testCase.getId(), offspring2.getTestResult(offspring2.getVariable(i).getId()));
            offspring1.setVariable(i,offspring2.getVariable(i));
            offspring2.replaceTestResult(offspring2.getVariable(i).getId(), testResult);
            offspring2.setVariable(i,testCase);
        }

        logger.info("Crossover probability fulfilled! Two test SUITES have been crossed over.");
    }
    return offspring;
    }

}
