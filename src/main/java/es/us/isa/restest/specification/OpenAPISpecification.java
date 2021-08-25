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
		ParseOptions parseOptions = new ParseOptions();
		parseOptions.setResolve(true);
		parseOptions.setResolveFully(true);
		parseOptions.setResolveCombinators(true);
//		parseOptions.setFlatten(true);
		this.specification = new OpenAPIV3Parser().read(location, null, parseOptions);
		this.path = location;
	}

	public OpenAPI getSpecification() {
		return specification;
	}

	public String getPath() {
		return path;
	}
	
	
	// Return the specification title
    public String getTitle(boolean capitalize) {
    	String title ="";
    	
    	if (specification!=null) {
	        title = specification.getInfo().getTitle().replaceAll("[^\\p{L}\\p{Nd}\\s]+", "").trim();
	        title = (capitalize? title.substring(0,1).toUpperCase() : title.substring(0,1).toLowerCase()) +
	                (title.length() > 1? formatTitle(title.substring(1).split("\\s")) : "");
    	}
        return title;
    }

    
    private static String formatTitle(String[] sp) {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < sp.length; i++) {
            if(i == 0) {
                builder.append(sp[i]);
            } else {
                builder.append(sp[i].substring(0, 1).toUpperCase());
                if(sp[i].length() > 1) {
                   builder.append(sp[i].substring(1));
                }
            }
        }
        return builder.toString();
    }
}
