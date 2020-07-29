package es.us.isa.restest.searchbased.operators;

import java.util.List;

import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;

public class AllMutationOperators extends AbstractAPITestCaseMutationOperator {

	List<? extends AbstractAPITestCaseMutationOperator> operators;
	
	public AllMutationOperators(List<? extends AbstractAPITestCaseMutationOperator> operators) {
		super(1, null);
		this.operators=operators;
	}

	@Override
	protected void doMutation(double mutationProbability, RestfulAPITestSuiteSolution solution) {
		for(AbstractAPITestCaseMutationOperator operator:operators) {
			operator.doMutation(operator.getMutationProbability(), solution);
		}		
	}

}
