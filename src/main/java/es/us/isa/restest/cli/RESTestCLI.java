package es.us.isa.restest.cli;

import es.us.isa.restest.main.CreateTestConf;
import es.us.isa.restest.main.TestGenerationAndExecution;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.util.RESTestException;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.nio.file.Path;


public class RESTestCLI {

    private static final Logger logger = Logger.getLogger(RESTestCLI.class);

    public static void main(String[] args) {

        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("h", "help", false, "Show help");
        options.addOption("oas", "openapi", true, "OpenAPI file");
        options.addOption("p", "property", true, "Property file");
        options.addOption("c", "conf", true, "Test configuration file");
        options.addOption("cc", "create-conf", false, "Create test configuration file");
        options.addOption("g", "generate", false, "Generate test cases");

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("RESTestCLI", options);
                return;
            }

            if (cmd.hasOption("oas")) {
                String oasFile = checkGetOAS(cmd);

                if (cmd.hasOption("cc")) {
                    CreateTestConf.main(new String[]{oasFile});
                }

            } else if (cmd.hasOption("p")) {
                String propFile = cmd.getOptionValue("p");
                boolean propFileExists = checkFileExists(propFile);

                if (propFileExists) {

                    if (cmd.hasOption("g")) {


                        RESTestLoader conf = new RESTestLoader(propFile);

                        try {
                            var gen = conf.createGenerator().generate();



                        } catch (RESTestException e) {
                            logger.error("Error during test generation: " + e.getMessage());
                        }

                    }

                    try {
                        TestGenerationAndExecution.main(new String[]{propFile});
                    } catch (RESTestException e) {
                        logger.error("Error during test generation and execution: " + e.getMessage());
                    }
                }

            }
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: " + e.getMessage());
        }

    }

    private static boolean checkFileExists(String fileName) {

        Path path = Path.of(fileName);

        if (path.toFile().exists()) {
            return true;
        } else {
            logger.error("File " + fileName + " does not exist.");
        }

        return false;
    }

    private static String checkGetOAS(CommandLine cmd) {
        String oasFile = cmd.getOptionValue("oas");
        boolean oasFileExists = checkFileExists(oasFile);

        if (oasFileExists) {
            return oasFile;
        } else {
            return null;
        }
    }

}
