package es.us.isa.restest.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * 
 * @author Sergio Segura
 */
public class PropertyManager {

	static String propertyFilePath = "config.properties";
	static 	Properties properties = null;
	
	static public String readProperty(String name) {
	
		if (properties==null) {
			 properties = new Properties();
			 try {
				 properties.load(new FileInputStream(propertyFilePath));
			 } catch (FileNotFoundException e) {
				 System.err.println("Error reading property file: " + e.getMessage());
				 e.printStackTrace();
			 } catch (IOException e) {
				 System.err.println("Error reading property file: " + e.getMessage());
				 e.printStackTrace();
			 }
		}
		
		return properties.getProperty(name);
		
	}
}
