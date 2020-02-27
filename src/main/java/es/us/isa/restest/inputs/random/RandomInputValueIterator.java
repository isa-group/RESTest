package es.us.isa.restest.inputs.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Random iterator for a list of input values of type <T>
 * 
 * @author Sergio Segura
 */
public class RandomInputValueIterator<T> extends RandomGenerator{ 

    private List<T> values;
    private Integer minValues; // Default to 1
    private Integer maxValues; // Default to 1
    private String separator;
     
    public RandomInputValueIterator() {
    	super();
    }
    
    public RandomInputValueIterator(List<T> values) {
    	super();
    	
    	this.values = values;
    }

	public Object nextValue() {
		Object value=null;
		
		if (!values.isEmpty()) {
			if(separator != null && (minValues != null || maxValues != null)) {
				minValues = minValues == null? 1 : minValues;
				maxValues = maxValues == null? minValues : maxValues;
				value = new ArrayList<>();
				List<T> localValues = new ArrayList<>(values);
				Random random = new Random();
				double d = random.nextDouble();
				int numValues = 0;
				//while(minValues == null && d<1/2  (minValues == null || minValues > numValues) || ((maxValues == null || maxValues > numValues) && d<1/2)) {
				while(minValues > numValues || (maxValues > numValues && d < 0.5)) {
					Object valueToAdd = localValues.get(rand.nextInt(0, localValues.size()-1));
					localValues.remove(valueToAdd);
					((List)value).add(valueToAdd);
					numValues++;
					d = random.nextDouble();
				}
			} else
				value= values.get(rand.nextInt(0, values.size()-1));
		}
		
		return value;
	}
	
	@Override
	public String nextValueAsString() {
    	String nextValueAsString;
    	Object nextValue = nextValue();
    	if(nextValue instanceof List) {
    		nextValueAsString = (String) ((List)nextValue).stream()
													.map(x -> x.toString())
													.collect(Collectors.joining(separator));
		} else nextValueAsString = nextValue().toString();
		return nextValueAsString;
	}
	
	public List<T> getValues() {
		return values;
	}
	
	public void setValues(List<T> values) {
		this.values = values;
	}

	public Integer getMinValues() {
    	return minValues;
	}

	public Integer getMaxValues() {
    	return maxValues;
	}

	public void setMinValues(Integer minValues) {
    	this.minValues = minValues;
	}

	public void setMaxValues(Integer maxValues) {
		this.maxValues = maxValues;
	}

	public String getSeparator() {
    	return separator;
	}

	public void setSeparator(String separator) {
    	this.separator = separator;
	}
}
