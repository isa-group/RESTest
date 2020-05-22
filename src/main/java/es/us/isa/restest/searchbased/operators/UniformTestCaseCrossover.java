package es.us.isa.restest.searchbased.operators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;

public class UniformTestCaseCrossover implements CrossoverOperator<RestfulAPITestSuiteSolution> {

	  	private double crossoverProbability;
	    private RandomGenerator<Double> crossoverRandomGenerator;
	    private BoundedRandomGenerator<Integer> pointRandomGenerator;

	    public UniformTestCaseCrossover(double crossoverProbability) {
	        this(
	                crossoverProbability,
	                () -> JMetalRandom.getInstance().nextDouble(),
	                (a, b) -> JMetalRandom.getInstance().nextInt(a, b));
	    }

	    public UniformTestCaseCrossover(
	            double crossoverProbability, RandomGenerator<Double> randomGenerator) {
	        this(
	                crossoverProbability,
	                randomGenerator,
	                BoundedRandomGenerator.fromDoubleToInteger(randomGenerator));
	    }

	    public UniformTestCaseCrossover(
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
	public List<RestfulAPITestSuiteSolution> execute(List<RestfulAPITestSuiteSolution> solutions) {
		assert(solutions!=null);
        assert(solutions.size() == 2);

        return doCrossover(crossoverProbability, solutions.get(0), solutions.get(1));
	}

	private List<RestfulAPITestSuiteSolution> doCrossover(double probability,
			RestfulAPITestSuiteSolution parent1,
			RestfulAPITestSuiteSolution parent2) {
		List<RestfulAPITestSuiteSolution> offspring = new ArrayList<>(2);
        RestfulAPITestSuiteSolution offspring1=(RestfulAPITestSuiteSolution) parent1.copy();
        RestfulAPITestSuiteSolution offspring2=(RestfulAPITestSuiteSolution) parent2.copy();
        offspring.add(offspring1);
        offspring.add(offspring2);
    
      // 1. We choose randomly the cases for the parameter crossover:
    	int parent1TestCaseIndex=pointRandomGenerator.getRandomValue(0, parent1.getNumberOfVariables()-1);
    	int parent2TestCaseIndex=pointRandomGenerator.getRandomValue(0, parent2.getNumberOfVariables()-1);
    	TestCase testCase1=offspring1.getVariable(parent1TestCaseIndex);
    	TestCase testCase2=offspring2.getVariable(parent1TestCaseIndex);
    	// Crossover is applied only between testcases of the same operation: 
    	if(testCase1.getOperationId().equals(testCase2.getOperationId())) 
      // 2. 3. Apply the crossover:
    		doCrossover(probability,testCase1,testCase2);                      
    	return offspring;

	}

	private void doCrossover(double probability, TestCase testCase1, TestCase testCase2) {
		doHeadersCrossover(probability,testCase1,testCase2);
		doPathCrossover(probability,testCase1,testCase2);
		doQueryCrossover(probability,testCase1,testCase2);
		doFormCrossover(probability,testCase1,testCase2);
		doBodyCrossover(probability,testCase1,testCase2);
		//doAuthCrossover(probability,testCase1,testCase2);
	}

	private void doFormCrossover(double probability, TestCase testCase1, TestCase testCase2) {		
		doCrossover(probability,testCase1.getFormParameters(),testCase2.getFormParameters());
	}

	private void doBodyCrossover(double probability, TestCase testCase1, TestCase testCase2) {
		if (crossoverRandomGenerator.getRandomValue() < probability) {
			String body1=testCase1.getBodyParameter();
			testCase1.setBodyParameter(testCase2.getBodyParameter());
			testCase2.setBodyParameter(body1);
		}
		
	}

	private void doQueryCrossover(double probability, TestCase testCase1, TestCase testCase2) {
		doCrossover(probability,testCase1.getQueryParameters(),testCase2.getQueryParameters());
	}

	private void doCrossover(double probability,Map<String,String> parameters1,Map<String,String> parameters2) {
		Set<String> processed=new HashSet<String>();		
		for(String param:parameters1.keySet()) {
			processed.add(param);
			if(crossoverRandomGenerator.getRandomValue() < probability) 
				doCrossover(param,parameters1,parameters2);
		}
		for(String param:parameters2.keySet()) 
			if(crossoverRandomGenerator.getRandomValue() < probability) 
				doCrossover(param,parameters2,parameters1);
	}
	
	private void doCrossover(String param, Map<String, String> parameters1, Map<String, String> parameters2) {
		String value=parameters1.get(param);
		if(parameters2.containsKey(param)) 
			parameters1.put(param,parameters2.get(param));
		else
			parameters1.remove(param);
		parameters2.put(param, value);
	}

	private void doPathCrossover(double probability, TestCase testCase1, TestCase testCase2) {
		doCrossover(probability,testCase1.getPathParameters(),testCase2.getPathParameters());		
	}

	private void doHeadersCrossover(double probability, TestCase testCase1, TestCase testCase2) {
		doCrossover(probability,testCase1.getHeaderParameters(),testCase2.getHeaderParameters());
		
	}

	@Override
	public int getNumberOfRequiredParents() {
		return 2;
	}

	@Override
	public int getNumberOfGeneratedChildren() {		
		return 2;
	}

}
