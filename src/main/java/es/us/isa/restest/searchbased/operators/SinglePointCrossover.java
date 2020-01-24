/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import java.util.ArrayList;
import java.util.List;
import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.solution.BinarySolution;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;



/**
 *
 * @author japarejo
 */
public class SinglePointCrossover implements CrossoverOperator<RestfulAPITestSuiteSolution> {

    private double crossoverProbability;
    private RandomGenerator<Double> crossoverRandomGenerator;
    private BoundedRandomGenerator<Integer> pointRandomGenerator;

    public SinglePointCrossover(double crossoverProbability) {
        this(
                crossoverProbability,
                () -> JMetalRandom.getInstance().nextDouble(),
                (a, b) -> JMetalRandom.getInstance().nextInt(a, b));
    }

    public SinglePointCrossover(
            double crossoverProbability, RandomGenerator<Double> randomGenerator) {
        this(
                crossoverProbability,
                randomGenerator,
                BoundedRandomGenerator.fromDoubleToInteger(randomGenerator));
    }

    public SinglePointCrossover(
            double crossoverProbability,
            RandomGenerator<Double> crossoverRandomGenerator,
            BoundedRandomGenerator<Integer> pointRandomGenerator) {
        if (crossoverProbability < 0) {
            throw new JMetalException("Crossover probability is negative: " + crossoverProbability);
        }
        this.crossoverProbability = crossoverProbability;
        this.crossoverRandomGenerator = crossoverRandomGenerator;
        this.pointRandomGenerator = pointRandomGenerator;
    }

    @Override
    public int getNumberOfRequiredParents() {
        return 2;
    }

    @Override
    public int getNumberOfGeneratedChildren() {
        return 2;
    }

    public double getCrossoverProbability() {
        return crossoverProbability;
    }

    public void setCrossoverProbability(double crossoverProbability) {
        this.crossoverProbability = crossoverProbability;
    }

    @Override
    public List<RestfulAPITestSuiteSolution> execute(List<RestfulAPITestSuiteSolution> solutions) {
        assert(solutions!=null);
        assert(solutions.size() == 2);

        return doCrossover(crossoverProbability, solutions.get(0), solutions.get(1));
    }
    
     /**
   * Perform the crossover operation.
   *
   * @param probability Crossover setProbability
   * @param parent1 The first parent
   * @param parent2 The second parent
   * @return An array containing the two offspring
   */
    private List<RestfulAPITestSuiteSolution> doCrossover(double probability, RestfulAPITestSuiteSolution parent1, RestfulAPITestSuiteSolution parent2) {
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
      TestCase testCase=null;
      for(int i=0;i<=crossoverPoint;i++){
          testCase=offspring1.getVariable(i);
          offspring1.setVariable(i,offspring2.getVariableValue(i));
          offspring2.setVariable(i,testCase);
      }
            
    }
    return offspring;
    }

}
