package es.us.isa.restest.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import es.us.isa.restest.specification.ParameterFeatures;
import io.swagger.models.Operation;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.Parameter;

/**
 * 
 * @author Sergio Segura
 */
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

	/**
	 * Returns the parameters that are required for the operation.
	 * @param operation Operation in the specification
	 * @return
	 */
	public static List<Parameter> getRequiredParameters(Operation operation) {
		return operation.getParameters().stream()
				.filter(Parameter::getRequired)
				.collect(Collectors.toList());
	}

	/**
	 * Returns the parameters that are required for the operation and are not path parameters.
	 * @param operation Operation in the specification
	 * @return
	 */
	public static List<Parameter> getRequiredNotPathParameters(Operation operation) {
		return getRequiredParameters(operation).stream()
				.filter(p -> !p.getIn().equals("path"))
				.collect(Collectors.toList());
	}

	/**
	 * Returns the parameters of an operation whose values can be changed for invalid values.
	 * These include the following:
	 * <ol>
	 *     <li>Integer. Can be changed to number, boolean or string.</li>
	 *     <li>Integer with min/max constraints. Can violate constraints.</li>
	 *     <li>Number. Can be changed to boolean or string.</li>
	 *     <li>Number with min/max constraints. Can violate constraints.</li>
	 *     <li>Boolean. Can be changed to number, integer or string.</li>
	 *     <li>String with format. Can be changed to random string.</li>
	 *     <li>String with minLength/maxLength. Can violate constraints.</li>
	 *     <li>Enum. Can be changed to value out of enum range.</li>
	 * </ol>
	 * <b>NOTE: Body parameters are not considered.</b>
	 * @param operation Operation in the specification
	 * @return
	 */
	public static List<Parameter> getParametersSubjectToInvalidValueChange(Operation operation) {
		return operation.getParameters().stream()
				.filter(p -> !p.getIn().equals("body")) // Remove body parameters
				.filter(p -> {
					ParameterFeatures pFeatures = new ParameterFeatures(p);
					// If the parameter fulfills one of the following conditions, add it to the list
					return (pFeatures.getType().equals("integer") || pFeatures.getType().equals("number")
							|| pFeatures.getType().equals("boolean") || (pFeatures.getType().equals("string")
							&& (pFeatures.getMinLength() != null || pFeatures.getMaxLength() != null
							|| pFeatures.getFormat() != null)) || pFeatures.getEnumValues() != null);
				})
				.collect(Collectors.toList());
	}

	public static Boolean hasDependencies(Operation operation) {
		try {
			List<String> dependencies = (List<String>)operation.getVendorExtensions().get("x-dependencies");
			return dependencies != null && dependencies.size() != 0;
		} catch (Exception e) { // If the "x-dependencies" extension is not correctly used
			return false;
		}
	}

	public static List<Parameter> getNonEnumParameters(Operation operation) {
		return operation.getParameters().stream()
				.filter(p -> {
					ParameterFeatures pFeatures = new ParameterFeatures(p);
					return (pFeatures.getEnumValues() == null && !pFeatures.getType().equals("boolean"));
				})
				.collect(Collectors.toList());
	}
}















