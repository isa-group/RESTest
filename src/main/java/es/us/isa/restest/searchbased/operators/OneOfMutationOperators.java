package es.us.isa.restest.searchbased.operators;

import java.util.Map;

import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

public class OneOfMutationOperators extends AbstractAPITestCaseMutationOperator {

	Map<? extends AbstractAPITestCaseMutationOperator,Double> operators;
	Double total;
	
	public OneOfMutationOperators(double mutationProbability, PseudoRandomGenerator randomGenerator,Map<? extends AbstractAPITestCaseMutationOperator,Double> operators) {
		super(mutationProbability, randomGenerator);
		this.operators=operators;
		this.total=0.0;
		for(Double value:operators.values())
			this.total+=value;
	}

	@Override
	protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
		double value=this.getRandomGenerator().nextDouble(0.0, total);
		double threshold=0;
		for(Map.Entry<? extends AbstractAPITestCaseMutationOperator,Double> entry:operators.entrySet()) {
			threshold+=entry.getValue();
			if(threshold>=value) {
				entry.getKey().doMutation(mutationProbability, solution);
			}
		}
	}

}
