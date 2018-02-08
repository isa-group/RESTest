package es.us.isa.rester.configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.specification.OpenAPISpecification;

/** Utility class to load and save test configuration files
 * 
 * @author Sergio Segura
 *
 */
public class TestConfigurationIO {

	/** Load test configuration file
	 */
	public static TestConfigurationObject loadConfiguration(String path) {
	    ObjectMapper mapper = new ObjectMapper();
	    TestConfigurationObject conf=null;
		try {
			conf = mapper.readValue(new File(path), TestConfigurationObject.class);
			
			// Print with format
			//String prettyConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf);
			//System.out.println(prettyConf);
		} catch (Exception e) {
			System.err.println("Error parsing configuration file: " + e.getMessage());
			e.printStackTrace();
		}
		
	    return conf;
	}
	
	public static String toString (TestConfigurationObject conf) {
		ObjectMapper mapper = new ObjectMapper();
		String jsonConf=null;
		try {
			jsonConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf);
		} catch (JsonProcessingException e) {
			System.err.println("Error converting configuration object to a string: " + e.getMessage());
			e.printStackTrace();
		}
		return jsonConf;
	}
	
	public static void toFile (TestConfigurationObject conf, String path) {
		FileWriter confFile = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			confFile = new FileWriter(path);
			confFile.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf));
			confFile.flush();
			confFile.close();
		} catch (IOException e) {
			System.err.println("Error converting configuration object to a file: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
