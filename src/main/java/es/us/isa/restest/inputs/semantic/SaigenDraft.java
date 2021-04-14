package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.SemanticOperation;
import es.us.isa.restest.configuration.pojos.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.writers.postman.pojos.Query;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.droidmate.saigen.Lib;
import org.droidmate.saigen.storage.QueryResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.SemanticInputGenerator.getSemanticOperations;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.updateTestConf;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;
import static es.us.isa.restest.util.FileManager.*;
import static es.us.isa.restest.util.PropertyManager.readProperty;


public class SaigenDraft {

    public static void main(String[] args) throws IOException {

        List<String> sampleParameters = new ArrayList<>();
        sampleParameters.add("countryCode");
        sampleParameters.add("latitude");
        sampleParameters.add("longitude");

        List<QueryResult> results = Lib.Companion.getInputsForLabels(sampleParameters);

        for(QueryResult result: results) {
            System.out.println("-------------------------------------");
            System.out.println(result.getLabel());
            System.out.println(result.getValues());
            System.out.println("-------------------------------------");
        }



    }


}
