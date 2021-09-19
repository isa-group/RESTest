package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.inputs.semantic.objects.SemanticOperation;
import es.us.isa.restest.inputs.semantic.objects.SemanticParameter;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.specification.OpenAPISpecification;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static es.us.isa.restest.configuration.TestConfigurationIO.loadConfiguration;
import static es.us.isa.restest.inputs.semantic.objects.SemanticOperation.getSemanticOperationsWithValuesFromPreviousIterations;
import static es.us.isa.restest.inputs.semantic.TestConfUpdate.*;
import static es.us.isa.restest.main.TestGenerationAndExecution.getExperimentName;
import static org.junit.Assert.assertEquals;

public class TestConfUpdateTest {

//    @Test
//    public void testUpdateTestConf() {
//
//        // newConf (Semantic), semanticParameter, path, opIndex
//
//    }

    @Test
    public void testUpdateTestConfWithIncreasedNumberOfTries() {

        String confPath = "src/test/resources/semanticAPITests/sampleSemanticAPI/";
        String confPathOriginal = confPath + "testConf.yaml";
        String confPathSemantic = confPath + "testConfSemantic.yaml";
        OpenAPISpecification specification = new OpenAPISpecification("src/test/resources/semanticAPITests/sampleSemanticAPI/swagger.yaml");
//        TestConfigurationObject testConfSemanticOriginal = loadConfiguration(confPath, specification);
        TestConfigurationObject testConfSemantic = loadConfiguration(confPathSemantic, specification);

        List<Operation> operations = testConfSemantic.getTestConfiguration().getOperations();
        SemanticOperation semanticOperation = new ArrayList<>(getSemanticOperationsWithValuesFromPreviousIterations(operations, getExperimentName())).get(0);

        assertEquals(1, semanticOperation.getSemanticParameters().size());

//        SemanticParameter semanticParameterOriginal = new ArrayList<>(semanticOperation.getSemanticParameters()).get(0);
        SemanticParameter semanticParameter = new ArrayList<>(semanticOperation.getSemanticParameters()).get(0);

        // Check that the number of tries is 0
        assertEquals(0, semanticParameter.getNumberOfTriesToGenerateRegex());

        // Call the function under test
        updateTestConfWithIncreasedNumberOfTries(testConfSemantic, confPathSemantic, semanticOperation, semanticParameter);

        // Check that the value is increased
        testConfSemantic = loadConfiguration(confPathSemantic, specification);

        operations = testConfSemantic.getTestConfiguration().getOperations();
        semanticOperation = new ArrayList<>(getSemanticOperationsWithValuesFromPreviousIterations(operations, getExperimentName())).get(0);

        assertEquals(1, semanticOperation.getSemanticParameters().size());


        semanticParameter = new ArrayList<>(semanticOperation.getSemanticParameters()).get(0);

        // Check that the number of tries is 0
        assertEquals(1, semanticParameter.getNumberOfTriesToGenerateRegex());

        // Rewrite the old value
        updateTestConfWithNewPredicates(testConfSemantic, confPathSemantic, semanticOperation, semanticParameter, new HashSet<>());


    }
}
