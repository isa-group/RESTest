package es.us.isa.restest.util;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;

/**
 * Helper class which implements the Allure confidentiality filter. It basically censors the API keys showed in the Allure report.
 *
 * @author José Ramón Fernández
 */
public class AllureAuthManager {

    private AllureAuthManager() {}

    public static List<String> findAuthProperties(OpenAPISpecification spec, String testConfPath) {
        TestConfigurationObject conf = loadConfiguration(testConfPath, spec);
        List<String> properties = new ArrayList<>();

        if(!conf.getAuth().getHeaderParams().isEmpty()) {
            properties.addAll(conf.getAuth().getHeaderParams().keySet());
        } else if(!conf.getAuth().getQueryParams().isEmpty()) {
            properties.addAll(conf.getAuth().getQueryParams().keySet());
        } else if(conf.getAuth().getApiKeysPath()!=null) {
            properties.addAll(new AuthManager(conf.getAuth().getApiKeysPath()).getAuthPropertyNames());
        } else if(conf.getAuth().getHeadersPath()!=null) {
            properties.addAll(new AuthManager(conf.getAuth().getHeadersPath()).getAuthPropertyNames());
        }

        return properties;
    }

    public static void confidentialityFilter(List<String> authProperties, String allurePath) throws IOException {
        File resultsDir = new File(allurePath);
        String[] allowedExtensions = {"html"};

        for (Iterator<File> it = FileUtils.iterateFiles(resultsDir, allowedExtensions, false); it.hasNext(); ) {
            File f = it.next();
            String fileString = FileManager.readFile(f.getPath());

            for (String authProperty : authProperties) {
                if (fileString != null) {
                    fileString = fileString.replaceAll("(" + authProperty + "([=:]))([^<&'])*([<&'])", "$1CENSORED$4");
                }
            }

            PrintWriter pw = new PrintWriter(f);
            pw.print(fileString);
            pw.close();
        }
    }
}
