package es.us.isa.restest.searchbased.terminationcriteria;

import es.us.isa.restest.searchbased.algorithms.SearchBasedAlgorithm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MaxExecutionTime implements TerminationCriterion{

	private static final Logger logger = LogManager.getLogger(MaxExecutionTime.class.getName());
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
		long now = System.currentTimeMillis();
		logger.info("Stopping criterion state: " + (now-start) + " / " + duration);
		return now - start >= duration;
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
