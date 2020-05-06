package es.us.isa.restest.configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;

/** Utility class to load and save test configuration files
 * 
 * @author Sergio Segura
 *
 */
public class TestConfigurationIO {

	/** Load test configuration file
	 */
	public static TestConfigurationObject loadConfiguration(String path) {
	    YAMLMapper mapper = new YAMLMapper();
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
		ObjectMapper mapper = new ObjectMapper();
		try(FileWriter confFile = new FileWriter(path)) {
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf);
			JsonNode jsonNode = mapper.readTree(json);

			// Convert JSON to YAML and write file
			YAMLMapper yamlMapper = new YAMLMapper();
			yamlMapper.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
			String yaml = yamlMapper.writeValueAsString(jsonNode);
			confFile.write(yaml);
			confFile.flush();
		} catch (IOException e) {
			System.err.println("Error converting configuration object to a file: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
