package es.us.isa.restest.main;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.generators.RandomTestCaseGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.testcases.writters.RESTAssuredWritter;


public class BikeWise {

	static int numTestCases = 1;												// Number of test cases per operation
	static String OAISpecPath = "src/test/resources/Bikewise/swagger.yaml";		// Path to OAS specification file
	static String confPath = "src/test/resources/Bikewise/fullConf.yaml";		// Path to test configuration file
	static String targetDir = "src/generation/java/restassured";				// Directory where test will be generated
	static String testClassName = "Bikewise";									// Prefix of the test class to be generated
	static String packageName = "restassured";									// Package name
	
	
	public static void main(String[] args) throws ClassNotFoundException, IOException {

		// Test generation (RESTAssured)
		testGeneration();
		
		// Test execution
		testExecution();
	}
	
	private static void testGeneration() {
	    
		// Load specification
        OpenAPISpecification spec = new OpenAPISpecification(OAISpecPath);

        // Load configuration
        TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(confPath);

        // Create generator and filter
        AbstractTestCaseGenerator generator = new RandomTestCaseGenerator(spec, conf, numTestCases);

        Collection<TestCase> testCases = generator.generate();

        // Write RESTAssured test cases
        RESTAssuredWritter writer = new RESTAssuredWritter();
        writer.setOAIValidation(true);
        writer.setLogging(true);
        writer.setAllureReport(true);
        String basePath = spec.getSpecification().getSchemes().get(0).name() + "://" + spec.getSpecification().getHost() + spec.getSpecification().getBasePath();
        writer.write(OAISpecPath, targetDir, testClassName, packageName, basePath.toLowerCase(), testCases);
	}
	
	
	private static void testExecution() throws ClassNotFoundException, IOException {
		
		

	    
		/*
		// Load class
		// Create a File object on the root of the directory containing the class file
		File file = new File("src\\generation\\java\\");

		try {
		    // Convert File to a URL
		    URL url = file.toURI().toURL();           // file:/c:/myclasses/
		    URL[] urls = new URL[]{url};
		    
		    for(int i=0;i<urls.length;i++)
		    	System.out.println("URL " + i + ":" + urls[i]);

		    // Create a new class loader with the directory
		    ClassLoader cl = new URLClassLoader(urls);
		    

		    // Load in the class; MyClass.class should be located in
		    // the directory file:/c:/myclasses/com/mycompany
		    Class cls = cl.loadClass("restassured.BikewiseTest");
		} catch (MalformedURLException e) {
		} catch (ClassNotFoundException e) {
		}
		
		*/
		
		File sourceFile = new File("src/generation/java/restassured/BikewiseTest.java");
		
		// compile the source file
		 JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		 if (compiler==null)
			 System.err.println("COMPILER IS NULL");
		 StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		 File parentDirectory = sourceFile.getParentFile();
		 fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(parentDirectory));
		 Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
		 compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();
		 fileManager.close();
		 
		 // load the compiled class
		 URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { parentDirectory.toURI().toURL() });
		 Class<?> testClass = classLoader.loadClass("restassured.BikewiseTest");
		 
		 // call a method on the loaded class
		// Method helloMethod = helloClass.getDeclaredMethod("hello");
		 //helloMethod.invoke(helloClass.newInstance());
		
		
		//Class<?> testClass = Class.forName(packageName + "." + testClassName + "Test");
		
		System.setProperty("allure.results.directory", "target/allure-results");
		
		JUnitCore junit = new JUnitCore();
		//junit.addListener(new TextListener(System.out));
		junit.addListener(new io.qameta.allure.junit4.AllureJunit4());
		junit.run(testClass);
	}
	
	
}