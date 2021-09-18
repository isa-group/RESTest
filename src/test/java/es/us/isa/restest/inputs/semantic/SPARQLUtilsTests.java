package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import java.util.*;
import java.util.regex.Pattern;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.objects.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.SPARQLUtils.getNewValues;
import static es.us.isa.restest.main.TestGenerationAndExecution.getExperimentName;
import static org.junit.Assert.assertTrue;


public class SPARQLUtilsTests {

    @Test
    public void testGetNewValues() {

        String confPath = "src/test/resources/semanticAPITests/sampleSemanticAPI/testConfSemantic.yaml";
        OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/sampleSemanticAPI/swagger.yaml");
        TestConfigurationObject conf = loadConfiguration(confPath, specification);

        List<Operation> operations = conf.getTestConfiguration().getOperations();
        List<SemanticOperation> semanticOperations = new ArrayList<>(getSemanticOperationsWithValuesFromPreviousIterations(operations, getExperimentName()));

        SemanticParameter semanticParameter = new ArrayList<>(semanticOperations.get(0).getSemanticParameters()).get(0);
        String regex = "^\\w\\w\\w$";

        Set<String> newValues = getNewValues(semanticParameter,
                Collections.singleton("http://dbpedia.org/property/currency"),
                regex);

        // Check that the new values are compliant with the regular expression
        assertTrue("Not all the values in the filtered csv satisfy the regex", newValues.stream().allMatch(Pattern.compile(regex).asPredicate()));

    }

}
