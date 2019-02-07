package es.us.isa.rester.util;

import java.util.Iterator;

import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

public class SpecificationVisitor {

	/**
	 * Returns the operation's parameter with name "paramName"
	 * @param operation Operation
	 * @param paramName Parameter's name
	 * @return
	 */
	public static Parameter findParameter(Operation operation, String paramName) {
		Parameter param = null;
		boolean found = false;
		
		Iterator<Parameter> it = operation.getParameters().iterator();
		while (it.hasNext() && !found) {
			Parameter p = it.next();
			if (p.getName().equalsIgnoreCase(paramName)) {
				param = p;
				found=true;
			}
		}
	
		return param;
	}
}
