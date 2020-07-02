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

public class CreateTestConf {

    private static final Logger log = LogManager.getLogger(CreateTestConf.class);
    private static String openApiSpecPath;
    private static String confPath;

    public static void main(String[] args) {

        if(args.length > 0) {
            openApiSpecPath = args[0];

        } else {
            openApiSpecPath = "src/test/resources/Stripe/spec3.yaml";
        }

        generateTestConfPath();

        OpenAPISpecification spec = new OpenAPISpecification(openApiSpecPath);
        DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator(spec);

        if(args.length > 1) {
            List<TestConfigurationFilter> filters = generateFilters(Arrays.copyOfRange(args,1, args.length));
            gen.generate(confPath, filters);
        } else {
            gen.generate(confPath);
        }

        log.info("Test configuration file generated in path {}", confPath);

    }

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
        }

        return filters;
    }

    private static void generateTestConfPath() {
        String[] sp = openApiSpecPath.split("/");
        int end = sp[sp.length-1].isEmpty()? sp.length-2 : sp.length-1;
        confPath = Arrays.stream(sp, 0, end).collect(Collectors.joining("/", "", "/testConfOAS3.yaml"));
    }
}
