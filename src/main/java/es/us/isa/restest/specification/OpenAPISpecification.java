package es.us.isa.restest.specification;

import io.swagger.parser.SwaggerParser;
import io.swagger.models.Swagger;

public class OpenAPISpecification 
{
	Swagger specification;
	
	public OpenAPISpecification(String location) {
		specification = new SwaggerParser().read(location);
	}
	
	public Swagger getSpecification() {
	   return specification;
   }
}
