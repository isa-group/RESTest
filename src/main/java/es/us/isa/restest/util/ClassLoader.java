package es.us.isa.restest.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * 
 * @author Sergio Segura
 */
public class ClassLoader {

	private static final Logger logger = LogManager.getLogger(ClassLoader.class.getName());

	public static Class<?> loadClass(String filePath, String className) {
		File sourceFile = new File(filePath);
		Class<?> loadedClass= null;
		
		// Compile the source file 
		try {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		File parentDirectory = sourceFile.getParentFile();
		fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(parentDirectory));
		Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
		compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();
		fileManager.close();
		
		 // load the compiled class
		 loadedClass = loadClass(parentDirectory, className);

		} catch (IOException e) {
			logger.error("Error loading class");
			logger.error("Exception: ", e);
		} catch (NullPointerException e) {
			logger.error("Error loading class. Make sure JDK is used");
			logger.error("Exception: ", e);
		}
		
		return loadedClass;
	}

	private static Class<?> loadClass(File parentDirectory, String className) {
		Class<?> loadedClass= null;
		try(URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { parentDirectory.toURI().toURL() })) {
			loadedClass = classLoader.loadClass(className);
		} catch (IOException e) {
			logger.error("Error loading class");
			logger.error("Exception: ", e);
		} catch (ClassNotFoundException e) {
			logger.error("Class not found");
			logger.error("Exception: ", e);
		}

		return loadedClass;
	}
}
