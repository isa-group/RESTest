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
import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.PathItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class to load and save test configuration files
 * 
 * @author Sergio Segura
 *
 */
public class TestConfigurationIO {

	private static Logger logger = LogManager.getLogger(TestConfigurationIO.class);

	private TestConfigurationIO() {
	}

	/**
	 * Load test configuration file in YAML format
	 * @param path The path where the test configuration file is located
	 * @param spec The OpenAPI specification related to the test configuration file
	 * @return the test configuration as an object
	 */
	public static TestConfigurationObject loadConfiguration(String path, OpenAPISpecification spec) {
		YAMLMapper mapper = new YAMLMapper();
		TestConfigurationObject conf = null;
		try {
			conf = mapper.readValue(new File(path), TestConfigurationObject.class);

			conf.getTestConfiguration().getOperations().forEach(x -> {
				PathItem pathItem = spec.getSpecification().getPaths().get(x.getTestPath());
				switch (x.getMethod().toLowerCase()) {
				case "get":
					x.setOpenApiOperation(pathItem.getGet());
					break;
				case "post":
					x.setOpenApiOperation(pathItem.getPost());
					break;
				case "put":
					x.setOpenApiOperation(pathItem.getPut());
					break;
				case "delete":
					x.setOpenApiOperation(pathItem.getDelete());
					break;
				default:
					throw new IllegalArgumentException("Method type not supported: " + x.getMethod());
				}
			});

			// Print with format
			// String prettyConf =
			// mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf);
			// System.out.println(prettyConf);
		} catch (Exception e) {
			logger.error("Error parsing configuration file: {}", e.getMessage());
		}

		return conf;
	}

	public static String toString(TestConfigurationObject conf) {
		ObjectMapper mapper = new ObjectMapper();
		String jsonConf = null;
		try {
			jsonConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf);
		} catch (JsonProcessingException e) {
			logger.error("Error converting configuration object to a string: {}", e.getMessage());
		}
		return jsonConf;
	}

	/**
	 *  Save a test configuration object in a YAML file
	 * @param conf The test configuration object
	 * @param path The path where the test configuration file will be generated
	 *
	 */
	public static void toFile(TestConfigurationObject conf, String path) {
		ObjectMapper mapper = new ObjectMapper();
		try (FileWriter confFile = new FileWriter(path)) {
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(conf);
			JsonNode jsonNode = mapper.readTree(json);

			// Convert JSON to YAML and write file
			YAMLMapper yamlMapper = new YAMLMapper();
			yamlMapper.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
			String yaml = yamlMapper.writeValueAsString(jsonNode);
			confFile.write(yaml);
			confFile.flush();
		} catch (IOException e) {
			logger.error("Error converting configuration object to a file: {}", e.getMessage());
		}
	}
}
