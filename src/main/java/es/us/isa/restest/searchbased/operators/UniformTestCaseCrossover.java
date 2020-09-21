package es.us.isa.restest.searchbased.operators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import es.us.isa.restest.searchbased.RestfulAPITestSuiteSolution;
import es.us.isa.restest.testcases.TestCase;
import org.uma.jmetal.util.pseudorandom.BoundedRandomGenerator;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import static es.us.isa.restest.searchbased.operators.Utils.resetTestResult;
import static es.us.isa.restest.searchbased.operators.Utils.updateTestCaseFaultyReason;

public class UniformTestCaseCrossover extends AbstractCrossoverOperator {

	    private boolean mutationApplied;

	    public UniformTestCaseCrossover(double crossoverProbability) {
	        super(crossoverProbability);
	    }

	    public UniformTestCaseCrossover(double crossoverProbability, RandomGenerator<Double> randomGenerator) {
	        super(crossoverProbability, randomGenerator);
	    }

	    public UniformTestCaseCrossover(
				double crossoverProbability,
				RandomGenerator<Double> crossoverRandomGenerator,
				BoundedRandomGenerator<Integer> pointRandomGenerator) {
	        super(crossoverProbability, crossoverRandomGenerator, pointRandomGenerator);
	    }

	@Override
	protected List<RestfulAPITestSuiteSolution> doCrossover(double probability,
			RestfulAPITestSuiteSolution parent1,
			RestfulAPITestSuiteSolution parent2) {
		List<RestfulAPITestSuiteSolution> offspring = new ArrayList<>(2);
        RestfulAPITestSuiteSolution offspring1=(RestfulAPITestSuiteSolution) parent1.copy();
        RestfulAPITestSuiteSolution offspring2=(RestfulAPITestSuiteSolution) parent2.copy();
        offspring.add(offspring1);
        offspring.add(offspring2);
    
      // 1. We choose randomly the cases for the parameter crossover:
    	int parent1TestCaseIndex=pointRandomGenerator.getRandomValue(0, parent1.getVariables().size()-1);
    	int parent2TestCaseIndex=pointRandomGenerator.getRandomValue(0, parent2.getVariables().size()-1);
    	TestCase testCase1=offspring1.getVariable(parent1TestCaseIndex);
    	TestCase testCase2=offspring2.getVariable(parent2TestCaseIndex);
    	// Crossover is applied only between testcases of the same operation: 
    	if(testCase1.getOperationId().equals(testCase2.getOperationId())) {
			// 2. 3. Apply the crossover:
			mutationApplied = false;
			doCrossover(probability, testCase1, testCase2);
			if (mutationApplied) {
				updateTestCaseFaultyReason(parent1, testCase1);
				updateTestCaseFaultyReason(parent2, testCase2);
				resetTestResult(testCase1.getId(), offspring1); // The test case changed, reset test result
				resetTestResult(testCase2.getId(), offspring2); // The test case changed, reset test result
			}
		}

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
		List<String> paramsToProcess=new ArrayList<>();
		for(String param:parameters1.keySet()) {
			processed.add(param);
			if(crossoverRandomGenerator.getRandomValue() < probability) {
				paramsToProcess.add(param);
				if (!mutationApplied) mutationApplied = true;
			}
		}
		for(String param:paramsToProcess) {			
			doCrossover(param,parameters1,parameters2);
		}
		paramsToProcess.clear(); 
		for(String param:parameters2.keySet()) 
			if(crossoverRandomGenerator.getRandomValue() < probability && !processed.contains(param)) {
				paramsToProcess.add(param);
				if (!mutationApplied) mutationApplied = true;
			}
		for(String param:paramsToProcess) {
			processed.add(param);
			doCrossover(param,parameters2,parameters1);
		}
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

}
