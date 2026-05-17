package es.us.isa.restest.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 
 * @author Sergio Segura
 */
public class PropertyManager {

	static String globalPropertyFilePath = "src/main/resources/config.properties";
	static Properties globalProperties = null;
	static Properties userProperties = null;

	private static Map<String, Properties> userPropertiesMap= new HashMap<>();



	private static Logger logger = LogManager.getLogger(PropertyManager.class.getName());

	/**
	 * Reads a property from the global properties file
	 * @param name Property name
	 * @return
	 */
	static public String readProperty(String name) {
	
		if (globalProperties ==null) {
			 globalProperties = new Properties();
			 try(FileInputStream defaultProperties = new FileInputStream(globalPropertyFilePath)) {
				 globalProperties.load(defaultProperties);
			 } catch (IOException e) {
				 logger.error("Error reading property file: {}", e.getMessage());
				 logger.error("Exception: ", e);
			 }
		}
		
		return globalProperties.getProperty(name);
		
	}

	/**
	 * Reads a property from the property file located in evalPropertiesFilePath
	 * @param evalPropertiesFilePath Path to the user properties file
	 * @param name Property name
	 * @return
	 */
	public static String readProperty(String evalPropertiesFilePath, String name) {

		Properties userProperties = userPropertiesMap.get(evalPropertiesFilePath);

		if (userProperties ==null) {
			userProperties = new Properties();
			try(FileInputStream experimentProperties = new FileInputStream(evalPropertiesFilePath)) {
				userProperties.load(experimentProperties);
				userPropertiesMap.put(evalPropertiesFilePath, userProperties);
			} catch (IOException e) {
				logger.error("Error reading property file: {}", e.getMessage());
				logger.error("Exception: ", e);
			}
		}

		return userProperties.getProperty(name);
	}

	// Setters

	public static void setUserPropertiesFilePath(String evalPropertiesFilePath) {
		Properties userProperties = new Properties();
		try (FileInputStream experimentProperties = new FileInputStream(evalPropertiesFilePath)) {
			userProperties.load(experimentProperties);
			userPropertiesMap.put(evalPropertiesFilePath, userProperties);
		} catch (IOException e) {
			logger.error("Error reading property file: {}", e.getMessage());
			logger.error("Exception: ", e);
		}
	}

}
