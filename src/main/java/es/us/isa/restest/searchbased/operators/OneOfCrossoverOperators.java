package es.us.isa.restest.searchbased.operators;

import com.google.common.collect.Lists;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import java.util.List;
import java.util.Map;

public class OneOfCrossoverOperators extends AbstractCrossoverOperator {

	Map<? extends AbstractCrossoverOperator,Double> operators;
	Double total;

	public OneOfCrossoverOperators(double mutationProbability, RandomGenerator<Double> randomGenerator, Map<? extends AbstractCrossoverOperator,Double> operators) {
		super(mutationProbability, randomGenerator);
		this.operators=operators;
		this.total=0.0;
		for(Double value:operators.values())
			this.total+=value;
	}

	@Override
	protected List<RestfulAPITestSuiteSolution> doCrossover(double probability, RestfulAPITestSuiteSolution parent1, RestfulAPITestSuiteSolution parent2) {
		List<RestfulAPITestSuiteSolution> crossedoverSolution = Lists.newArrayList(parent1, parent2);
		double value=this.crossoverRandomGenerator.getRandomValue() * total;
		double threshold=0;
		for(Map.Entry<? extends AbstractCrossoverOperator,Double> entry:operators.entrySet()) {
			threshold+=entry.getValue();
			if(threshold>=value) {
				crossedoverSolution = entry.getKey().execute(crossedoverSolution);
			}
		}

		return crossedoverSolution;
	}
}
