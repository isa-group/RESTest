package es.us.isa.restest.util;

import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.allOf;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.headerContainsSubstring;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageContainsSubstring;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageHasKey;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;

import es.us.isa.restest.specification.OpenAPISpecification;

public class OASAPIValidator {

	private static OpenApiInteractionValidator validator;						// OAS validator
	private static OpenAPISpecification spec = null;							// OAS specification
	
	private static OASAPIValidator instance = null;							// Singleton object
	
	
	private OASAPIValidator(OpenAPISpecification spec) {
		
		this.spec = spec;
		
		// Test case validator:
		// Whitelist: Fix for swagger-validation library: formData parameters defined as string should not
		// violate the schema when using numbers or booleans, since those are still strings.
		ValidationErrorsWhitelist whitelist = ValidationErrorsWhitelist.create()
				.withRule(
						"Ignore non-strings for string-type formData parameters",
						allOf(
								headerContainsSubstring("Content-Type", "application/x-www-form-urlencoded"),
								messageHasKey("validation.request.body.schema.type"),
								messageContainsSubstring("does not match any allowed primitive type (allowed: [\"string\"])")
						)
				);
		this.validator = OpenApiInteractionValidator.createFor(spec.getPath()).withWhitelist(whitelist).build();
	}
	
	
	public static OpenApiInteractionValidator getValidator(OpenAPISpecification oas) {
		
		if (instance==null || !spec.equals(oas)) {
			spec = oas;
			instance = new OASAPIValidator(oas);
		}
		
		return validator;
		
	}
	
}
