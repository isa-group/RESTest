package es.us.isa.rester.inputs.random;

import java.util.List;

/** Random iterator for a list of input values of type <T>
 * 
 * @author Sergio Segura
 */
public class RandomInputValueIterator<T> extends RandomGenerator{ 

    private List<T> values;
     
    public RandomInputValueIterator() {}
    
    public RandomInputValueIterator(List<T> values) {
    	super();
    	
    	this.values = values;
    }

	public Object nextValue() {
		Object value=null;
		
		if (!values.isEmpty())
			value= values.get(rand.nextInt(0, values.size()-1));
		
		return value;
	}
	
	@Override
	public String nextValueAsString() {
		return nextValue().toString();
	}
	
	public List<T> getValues() {
		return values;
	}
	
	public void setValues(List<T> values) {
		this.values = values;
	}

}
