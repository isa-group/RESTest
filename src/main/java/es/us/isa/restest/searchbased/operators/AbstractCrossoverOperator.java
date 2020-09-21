package es.us.isa.restest.searchbased.operators;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import java.util.List;

public abstract class AbstractCrossoverOperator implements CrossoverOperator<RestfulAPITestSuiteSolution> {

    protected double crossoverProbability;
    protected RandomGenerator<Double> crossoverRandomGenerator;
    protected BoundedRandomGenerator<Integer> pointRandomGenerator;

    public AbstractCrossoverOperator(double crossoverProbability) {
        this(
                crossoverProbability,
                () -> JMetalRandom.getInstance().nextDouble(),
                (a, b) -> JMetalRandom.getInstance().nextInt(a, b));
    }

    public AbstractCrossoverOperator(
            double crossoverProbability, RandomGenerator<Double> randomGenerator) {
        this(
                crossoverProbability,
                randomGenerator,
                BoundedRandomGenerator.fromDoubleToInteger(randomGenerator));
    }

    public AbstractCrossoverOperator(
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

    protected abstract List<RestfulAPITestSuiteSolution> doCrossover(double probability, RestfulAPITestSuiteSolution parent1, RestfulAPITestSuiteSolution parent2);
}
