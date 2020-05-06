package es.us.isa.restest.util;

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
			System.err.println("Error loading class: " + e.getMessage());
			e.printStackTrace();
		} catch (NullPointerException e) {
			System.err.println("Error loading class. Make sure JDK is used: " + e.getMessage());
			e.printStackTrace();
		}
		
		return loadedClass;
	}

	private static Class<?> loadClass(File parentDirectory, String className) {
		Class<?> loadedClass= null;
		try(URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { parentDirectory.toURI().toURL() })) {
			loadedClass = classLoader.loadClass(className);
		} catch (IOException e) {
			System.err.println("Error loading class: " + e.getMessage());
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.err.println("Class not found: " + e.getMessage());
			e.printStackTrace();
		}

		return loadedClass;
	}
}
