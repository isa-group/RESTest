package es.us.isa.restest.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import es.us.isa.restest.specification.ParameterFeatures;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * 
 * @author Sergio Segura
 */
public class SpecificationVisitor {

	private SpecificationVisitor() {
		//Utility class
	}

	public static final String MEDIA_TYPE_APPLICATION_JSON = "application/json";
	public static final String MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
	public static final String MEDIA_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";
	public static final String BOOLEAN_TYPE = "boolean";

	/**
	 * Returns the operation's parameter with name "paramName"
	 * @param operation Operation
	 * @param paramName Parameter's name
	 * @return
	 */
	public static ParameterFeatures findParameter(Operation operation, String paramName) {
		ParameterFeatures param = null;
		boolean found = false;

		Iterator<Parameter> it = operation.getParameters().iterator();
		while (it.hasNext() && !found) {
			Parameter p = it.next();
			if (p.getName().equalsIgnoreCase(paramName)) {
				param = new ParameterFeatures(p);
				found=true;
			}
		}

		if(!found && paramName.equals("body") && operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_JSON)) {
			param = new ParameterFeatures("body", "body", operation.getRequestBody().getRequired());

		} else if(!found) {

			MediaType mediaType = operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ?
					operation.getRequestBody().getContent().get(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) :
					operation.getRequestBody().getContent().get(MEDIA_TYPE_MULTIPART_FORM_DATA);
			Iterator<Map.Entry> formDataIterator = mediaType.getSchema().getProperties().entrySet().iterator();

			while (formDataIterator.hasNext()) {
				Schema s = ((Map.Entry<String, Schema>) it.next()).getValue();
				if (s.getName().equalsIgnoreCase(paramName)) {
					param = new ParameterFeatures(s, mediaType.getSchema().getRequired() != null && mediaType.getSchema().getRequired().contains(s.getName()));
					break;
				}
			}
		}
	
		return param;
	}

	/**
	 * Returns the parameters that are required for the operation.
	 * @param operation Operation in the specification
	 * @return
	 */
	public static List<ParameterFeatures> getRequiredParameters(Operation operation) {
		List<ParameterFeatures> requiredParameters = operation.getParameters().stream()
				.filter(Parameter::getRequired)
				.map(ParameterFeatures::new)
				.collect(Collectors.toCollection(ArrayList::new));

		if(operation.getRequestBody().getContent() != null && operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_JSON) && operation.getRequestBody().getRequired()) {
			new ParameterFeatures("body", "body", Boolean.TRUE);

		} else if(operation.getRequestBody().getContent() != null && operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ||
				operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_MULTIPART_FORM_DATA)) {

			MediaType mediaType = operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ?
					operation.getRequestBody().getContent().get(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) :
					operation.getRequestBody().getContent().get(MEDIA_TYPE_MULTIPART_FORM_DATA);

			if(mediaType.getSchema().getRequired() != null && !mediaType.getSchema().getRequired().isEmpty()) {

				for(Object o : mediaType.getSchema().getProperties().keySet()) {
					Schema s = ((Map.Entry<String, Schema>) o).getValue();
					if(mediaType.getSchema().getRequired().contains(s.getName())) {
						requiredParameters.add(new ParameterFeatures(s, true));
					}
				}
			}
		}

		return requiredParameters;
	}

	/**
	 * Returns the parameters that are required for the operation and are not path parameters.
	 * @param operation Operation in the specification
	 * @return
	 */
	public static List<ParameterFeatures> getRequiredNotPathParameters(Operation operation) {
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
	public static List<ParameterFeatures> getParametersSubjectToInvalidValueChange(Operation operation) {
		List<ParameterFeatures> result = operation.getParameters().stream()
				.map(ParameterFeatures::new)
				.filter(p -> (p.getType().equals("integer") || p.getType().equals("number")
							|| p.getType().equals(BOOLEAN_TYPE) || (p.getType().equals("string")
							&& (p.getMinLength() != null || p.getMaxLength() != null
							|| p.getFormat() != null)) || p.getEnumValues() != null))
				.collect(Collectors.toCollection(ArrayList::new));

		if(operation.getRequestBody().getContent() != null && operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ||
				operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_MULTIPART_FORM_DATA)) {

			MediaType mediaType = operation.getRequestBody().getContent().containsKey(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) ?
					operation.getRequestBody().getContent().get(MEDIA_TYPE_APPLICATION_X_WWW_FORM_URLENCODED) :
					operation.getRequestBody().getContent().get(MEDIA_TYPE_MULTIPART_FORM_DATA);

			for(Object o : mediaType.getSchema().getProperties().keySet()) {

				Schema s = ((Map.Entry<String, Schema>) o).getValue();
				ParameterFeatures p = new ParameterFeatures(s, mediaType.getSchema().getRequired() != null && mediaType.getSchema().getRequired().contains(s.getName()));

				if ((p.getType().equals("integer") || p.getType().equals("number")
						|| p.getType().equals(BOOLEAN_TYPE) || (p.getType().equals("string")
						&& (p.getMinLength() != null || p.getMaxLength() != null
						|| p.getFormat() != null)) || p.getEnumValues() != null)) {
					result.add(p);
				}
			}
		}

		return result;
	}

	public static Boolean hasDependencies(Operation operation) {
		try {
			List<String> dependencies = (List<String>)operation.getExtensions().get("x-dependencies");
			return dependencies != null && !dependencies.isEmpty();
		} catch (Exception e) { // If the "x-dependencies" extension is not correctly used
			return false;
		}
	}

	public static List<Parameter> getNonEnumParameters(Operation operation) {
		return operation.getParameters().stream()
				.filter(p -> {
					ParameterFeatures pFeatures = new ParameterFeatures(p);
					return (pFeatures.getEnumValues() == null && !pFeatures.getType().equals(BOOLEAN_TYPE));
				})
				.collect(Collectors.toList());
	}
}















