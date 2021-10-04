package es.us.isa.restest.main;

import es.us.isa.restest.configuration.TestConfigurationFilter;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
 * The sample class creates a default test configuration file for the OAS file provided as input (or, as a default, for "src/test/resources/Comments/swagger.yaml")
 * 
 */

public class CreateTestConf {

    private static final Logger log = LogManager.getLogger(CreateTestConf.class);
    private static String openApiSpecPath = "src/test/resources/Folder/openapi.yaml";			                                // OAS file path
    private static String confPath;																// Test configuration path

    
    /*
     * This main method can receive two types of arguments (optional):
     * 		1. Path of the OAS specification file for which the test configuration file will be generated
     *      2. One ore more filters specifying the operations for which test configuration data must be created. Format: "path1:HTTPMethod1,path2:HTTPMethod2,..."
     */
    public static void main(String[] args) {

    	List<TestConfigurationFilter> filters=null;
    	
    	// Read input OAS specification file path (if any)
        if(args.length > 1) {				// Read input OAS specification file path and filters
            openApiSpecPath = args[0];
            filters = generateFilters(Arrays.copyOfRange(args,1, args.length));
        } else if (args.length == 1)		// Read input OAS specification file
        	openApiSpecPath = args[0];

        // Generate target path if it does not exist
        generateTestConfPath();

        // Load OAS specification
        OpenAPISpecification spec = new OpenAPISpecification(openApiSpecPath);
        
        // Create test configuration generator
        DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);

        // Generate test configuration file
        if (filters!=null)
        	gen.generate(confPath, filters);
        else
        	gen.generate(confPath);
        

        log.info("Test configuration file generated in path {}", confPath);

    }

    /*
     * Generate test configuration filters from the information provided in the list of arguments. 
     * Each filter must follow the format "path:httpmethod".
     */
    private static List<TestConfigurationFilter> generateFilters(String[] filtersArr) {
        List<TestConfigurationFilter> filters = new ArrayList<>();

        for(String s : filtersArr) {
            TestConfigurationFilter filter = new TestConfigurationFilter();
            String[] sp = s.split(":");

            if(sp.length != 2) {
                throw new IllegalArgumentException("Invalid format: a filter must be specified with the format 'path:HTTPMethod1,HTTPMethod2,...'");
            }

            filter.setPath(sp[0]);
            String[] methods = sp[1].split(",");

            for(String method : methods) {
                switch (method.toLowerCase()) {
                    case "get":
                        filter.addGetMethod();
                        break;
                    case "post":
                        filter.addPostMethod();
                        break;
                    case "put":
                        filter.addPutMethod();
                        break;
                    case "delete":
                        filter.addDeleteMethod();
                        break;
                    case "all":
                        filter.addAllMethods();
                        break;
                    default:
                        throw new IllegalArgumentException("HTTP method not supported: " + method);
                }
            }

            filters.add(filter);
        }

        return filters;
    }

    /*
     * Generate the path for test configuration file if it does not exist
     */
    private static void generateTestConfPath() {
        String[] sp = openApiSpecPath.split("/");
        int end = sp[sp.length-1].isEmpty()? sp.length-2 : sp.length-1;
        confPath = Arrays.stream(sp, 0, end).collect(Collectors.joining("/", "", "/testConf.yaml"));
    }
}
