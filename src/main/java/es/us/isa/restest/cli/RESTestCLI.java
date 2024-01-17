package es.us.isa.restest.cli;

import es.us.isa.restest.main.CreateTestConf;
import es.us.isa.restest.runners.RESTestExecutor;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.runners.RESTestRunner;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.PropertyManager;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Properties;

import static es.us.isa.restest.util.FileManager.createDir;


public class RESTestCLI {

    private static final Logger logger = Logger.getLogger(RESTestCLI.class);
    private static final String BASE_PROPERTY_FILE_PATH = "src/main/resources/base.properties";
    private static final String BASE_COPY_PROPERTY_FILE_PATH = "src/main/resources/base-copy.properties";

    public static void main(String[] args) {
        cli(args);
    }

    public static void cli(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("h", "help", false, "Show help");
        options.addOption("oas", "openapi", true, "OpenAPI file");
        options.addOption("p", "property", true, "Property file");
        options.addOption("c", "conf", true, "Test configuration file");
        options.addOption("cc", "create-conf", false, "Create test configuration file");
        options.addOption("g", "generate", false, "Generate test cases");
        options.addOption("e", "execute", false, "Execute test cases");
        options.addOption("ge", "generate-execute", false, "Generate and execute test cases");


        if (args.length == 0) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("RESTestCLI", options);
            return;
        }


        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("cc") && !cmd.hasOption("oas")) {
                throw new RuntimeException("Error: OAS file path is empty");
            }

            if ((cmd.hasOption("g") || cmd.hasOption("e") || cmd.hasOption("ge")) && !cmd.hasOption("p")) {
                throw new RuntimeException("Error: Property file path is empty");
            }

            if ((cmd.hasOption("g") && cmd.hasOption("e")) || (cmd.hasOption("g") && cmd.hasOption("ge")) || (cmd.hasOption("e") && cmd.hasOption("ge"))) {
                throw new RuntimeException("Error: Only one of the following options can be used: -g, -e, -ge");
            }

            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("RESTestCLI", options);
                return;
            }

            if (cmd.hasOption("oas")) {
                String oasFile = checkGetOAS(cmd);

                if (oasFile.trim().isEmpty()){
                    throw new RuntimeException("Error: OAS file path is empty");
                }

                if (cmd.hasOption("cc")) {
                    CreateTestConf.main(new String[]{oasFile});
                    return;
                }

                CreateTestConf c = new CreateTestConf();

                CreateTestConf.main(new String[]{oasFile});

                String confPath = CreateTestConf.getConfPath();

                confPath = confPath.replace("/", "");

                confPath = folderPath(oasFile) + confPath;

                copyFile(BASE_PROPERTY_FILE_PATH, BASE_COPY_PROPERTY_FILE_PATH);

                Path p = Path.of(oasFile);

                oasFile = oasFile.replace("\\", "/");
                confPath = confPath.replace("\\", "/");

                updatePropertyValues(BASE_COPY_PROPERTY_FILE_PATH, oasFile, confPath);

                RESTestRunner runner = new RESTestRunner(BASE_COPY_PROPERTY_FILE_PATH);

                runner.run();

                logger.info(runner.getNumberOfTestCases() + " test cases generated and written to " + runner.getTargetDirJava());
                logger.info("Allure report available at " + runner.getAllureReportsPath());
                logger.info("CSV stats available at " + PropertyManager.readProperty("data.tests.dir") + "/" + runner.getExperimentName());
                logger.info("Coverage report available at " + PropertyManager.readProperty("data.coverage.dir") + "/" + runner.getExperimentName());

                deleteFile(BASE_COPY_PROPERTY_FILE_PATH);


            } else if (cmd.hasOption("p")) {
                String propFile = cmd.getOptionValue("p");

                if (propFile.trim().isEmpty()){
                    throw new RuntimeException("Error: Property file path is empty");
                }

                boolean propFileExists = checkFileExists(propFile);

                if (propFileExists) {

                    if (cmd.hasOption("g")) {

                        RESTestLoader loader = new RESTestLoader(propFile);

                        try {
                            var generator = loader.createGenerator();
                            Collection<TestCase> testCases = generator.generate();

                            createDir(loader.getTargetDirJava());

                            RESTAssuredWriter writer = (RESTAssuredWriter) loader.createWriter();
                            writer.write(testCases);

                            logger.info(testCases.size() + " test cases generated and written to " + loader.getTargetDirJava());

                        } catch (RESTestException e) {
                            logger.error("Error during test generation: " + e.getMessage());
                        }

                    } else if (cmd.hasOption("e")) {
                        RESTestExecutor executor = new RESTestExecutor(propFile);
                        executor.execute();
                    } else if (cmd.hasOption("ge")) {
                        RESTestRunner runner = new RESTestRunner(propFile);
                        runner.run();

                        logger.info(runner.getNumberOfTestCases() + " test cases generated and written to " + runner.getTargetDirJava());
                        logger.info("Allure report available at " + runner.getAllureReportsPath());
                        logger.info("CSV stats available at " + PropertyManager.readProperty("data.tests.dir") + "/" + runner.getExperimentName());
                        logger.info("Coverage report available at " + PropertyManager.readProperty("data.coverage.dir") + "/" + runner.getExperimentName());
                    }

                }
            }
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: " + e.getMessage());
        } catch (RESTestException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private static String folderPath(String path) {
        String[] pathSplit = path.split("[/\\\\]");
        StringBuilder folderPath = new StringBuilder();

        int lastIndex = pathSplit.length - 1;
        while (lastIndex >= 0 && pathSplit[lastIndex].isEmpty()) {
            lastIndex--;
        }

        for (int i = 0; i < lastIndex; i++) {
            if (i == lastIndex || isWindowsPath(path)) {
                folderPath.append(pathSplit[i]).append("\\");
            } else {
                folderPath.append(pathSplit[i]).append("/");
            }
        }

        return folderPath.toString();
    }

    private static boolean isWindowsPath(String path) {
        return path.matches("^[A-Za-z]:\\\\.*$");
    }

    private static String generatePropertyFile(String oasFile, String confPath) {
        String propertyFile = null;
        return propertyFile;
    }

    private static void openFileInTextViewer(String filePath) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process process;

            if (os.contains("win")) {

                ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "start", filePath);
                process = processBuilder.start();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {

                ProcessBuilder processBuilder = new ProcessBuilder("xdg-open", filePath);
                process = processBuilder.start();
            } else {
                System.err.println("Unsupported operating system: " + os);
                return;
            }


            while (true) {
                try {
                    int exitCode = process.exitValue();
                    if (exitCode == 0) {
                        System.out.println("Editor closed. Continuing...");
                    } else {
                        System.err.println("Error opening file. Exit code: " + exitCode);
                    }
                    break;
                } catch (IllegalThreadStateException e) {

                    Thread.sleep(100);
                }
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error opening file: " + e.getMessage());
        }
    }


    public static void copyFile(String srcFile, String dstFile) {

        Path source = Path.of(srcFile);
        Path destination = Path.of(dstFile);

        try {

            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("File copied successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteFile(String filePath) throws IOException {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IOException("Error while trying to delete the file: " + e.getMessage());
        }
    }

    public static void updatePropertyValues(String filePath, String oasPath, String confPath) {
        Properties properties = new Properties();

        try (InputStream input = new FileInputStream(filePath)) {

            properties.load(input);
        } catch (IOException e) {
            System.out.println("Error while trying to load the file: " + e.getMessage());
            return;
        }

        File file = new File(filePath);

        try (OutputStream output = new FileOutputStream(file)) {

            properties.store(output, null);
        } catch (IOException e) {
            System.out.println("Error while trying to create the temporary copy: " + e.getMessage());
            return;
        }


        try (OutputStream output = new FileOutputStream(file)) {
            properties.setProperty("oas.path", oasPath);
            properties.setProperty("conf.path", confPath);


            properties.store(output, null);
        } catch (IOException e) {
            System.out.println("Error while trying to save the updated properties: " + e.getMessage());
            return;
        }
        System.out.println("File updated successfully.");
    }


    // in

    public static void in() {
        String[] args = {"-g", "-p", "src/test/resources/Restcountries/restcountries.properties"};
        cli(args);

    }

}
