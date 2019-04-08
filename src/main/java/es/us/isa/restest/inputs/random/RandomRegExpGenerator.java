package es.us.isa.restest.inputs.random;

import com.mifmif.common.regex.Generex;

/**
 * @author Sergio Segura
 *
 */
public class RandomRegExpGenerator extends RandomGenerator {

	private Generex generex;
	private int minLength=-1;
	private int maxLength=-1;
	
    public RandomRegExpGenerator(String regExp) {
    	super();
    	
    	generex = new Generex(regExp);
    	
    	// Generate and save seed
    	generex.setSeed(this.seed);
    }
   
	@Override
	public String nextValue() {
		String value=null;
		if (minLength!=-1 && maxLength!=-1)
			value = generex.random(minLength,maxLength);
		else if (minLength!=-1)
			value = generex.random(minLength);
		else
			value = generex.random();
		
		return value;
	}
	
	@Override
	public String nextValueAsString() {
		return nextValue();
	}

	public int getMinLength() {
		return minLength;
	}

	public void setMinLength(int minLength) {
		this.minLength = minLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	public void setSeed(long seed) {
    	generex.setSeed(seed);
	}
}
