package es.us.isa.restest.specification;

import io.swagger.parser.SwaggerParser;
import io.swagger.models.Swagger;

public class OpenAPISpecification 
{
	Swagger specification;
	private String path;
	
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
