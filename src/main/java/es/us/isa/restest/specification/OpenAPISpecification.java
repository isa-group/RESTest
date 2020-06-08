package es.us.isa.restest.specification;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * Class to deserialize and manage a specification of a RESTful API described with the OAS language.
 */
public class OpenAPISpecification {

	OpenAPI specification;
	private String path;

	/**
	 * This constructor deserializes an OpenAPI specification from a file that is stored in <i>location</i>.
	 * @param location The location of the file
	 */
	public OpenAPISpecification(String location) {
		ParseOptions options = new ParseOptions();
		options.setResolveFully(true);
		this.specification = new OpenAPIV3Parser().read(location);
		this.path = location;
	}

	public OpenAPI getSpecification() {
		return specification;
	}

	public String getPath() {
		return path;
	}
}
