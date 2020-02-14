package es.us.isa.restest.specification;

import io.swagger.parser.SwaggerParser;
import io.swagger.models.Swagger;

/**
 * Class to deserialize and manage a specification of a RESTful API described with the OAS language.
 */
public class OpenAPISpecification 
{
	Swagger specification;
	private String path;

	/**
	 * This constructor deserializes an OpenAPI specification from a file that is stored in <i>location</i>.
	 * @param location The location of the file
	 */
	public OpenAPISpecification(String location) {
		this.specification = new SwaggerParser().read(location);
		this.path = location;
	}
	
	public Swagger getSpecification() {
	   return specification;
   }

	public String getPath() {
		return path;
	}
}
