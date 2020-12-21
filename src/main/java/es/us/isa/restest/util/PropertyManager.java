package es.us.isa.restest.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 
 * @author Sergio Segura
 */
public class PropertyManager {

	static String propertyFilePath = "src/main/resources/config.properties";
	static Properties properties = null;
	static Properties experimentProperties = null;

	private static Logger logger = LogManager.getLogger(PropertyManager.class.getName());
	
	static public String readProperty(String name) {
	
		if (properties==null) {
			 properties = new Properties();
			 try(FileInputStream defaultProperties = new FileInputStream(propertyFilePath)) {
				 properties.load(defaultProperties);
			 } catch (IOException e) {
				 logger.error("Error reading property file: {}", e.getMessage());
				 logger.error("Exception: ", e);
			 }
		}
		
		return properties.getProperty(name);
		
	}

	public static String readProperty(String evalPropertiesFilePath, String name) {

		if (experimentProperties ==null) {
			experimentProperties = new Properties();
			try(FileInputStream experimentProperties = new FileInputStream(evalPropertiesFilePath)) {
				PropertyManager.experimentProperties.load(experimentProperties);
			} catch (IOException e) {
				logger.error("Error reading property file: {}", e.getMessage());
				logger.error("Exception: ", e);
			}
		}

		return experimentProperties.getProperty(name);
	}


}
