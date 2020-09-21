package es.us.isa.restest.searchbased.operators;

import com.google.common.collect.Lists;
import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import org.uma.jmetal.operator.CrossoverOperator;

import java.util.ArrayList;
import java.util.List;

public class AllCrossoverOperators extends AbstractCrossoverOperator {

	List<? extends AbstractCrossoverOperator> operators;

	public AllCrossoverOperators(List<? extends AbstractCrossoverOperator> operators) {
		super(1, null);
		this.operators=operators;
	}

	@Override
	protected List<RestfulAPITestSuiteSolution> doCrossover(double probability, RestfulAPITestSuiteSolution parent1, RestfulAPITestSuiteSolution parent2) {
		List<RestfulAPITestSuiteSolution> crossedoverSolution = Lists.newArrayList(parent1, parent2);
		for(AbstractCrossoverOperator operator:operators) {
			crossedoverSolution = operator.execute(crossedoverSolution);
		}

		return crossedoverSolution;
	}
}
