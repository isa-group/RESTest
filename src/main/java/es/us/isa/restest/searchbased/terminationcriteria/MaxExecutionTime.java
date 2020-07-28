package es.us.isa.restest.searchbased.terminationcriteria;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;

public class MaxExecutionTime implements TerminationCriterion{

	private Long start;
	private long duration;
	
	public MaxExecutionTime(long duration, TimeUnit unit) {
		this.duration=Math.abs(duration)*unit.multiplier;
		this.start=null;
	}
	
	
	@Override
	public boolean test(SearchBasedAlgorithm t) {
		if(start==null) {
			start=System.currentTimeMillis();
		}		
		return System.currentTimeMillis() - start >= duration;
	}

	public enum TimeUnit {
		MILLISECONDS(1),
		SECONDS(1000),
		MINUTES(60000),
		HOURS(3600000);		 
		private long multiplier;
		TimeUnit(long multiplier){this.multiplier = multiplier;}
	}
}
