package es.us.isa.restest.inputs.semantic.objects;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.configuration.pojos.Operation;
import es.us.isa.restest.configuration.pojos.TestParameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.PREDICATES;
import static es.us.isa.restest.util.CSVManager.collectionToCSV;

import java.util.*;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;
import static es.us.isa.restest.inputs.semantic.objects.SemanticParameter.generateSemanticParameters;

public class SemanticOperation {
    private String operationName = null;
    private String operationPath = null;
    private String operationMethod = null;
    private String operationId = null;
    private Set<SemanticParameter> semanticParameters = null;

    private static final Logger logger = LogManager.getLogger(SemanticOperation.class.getName());

    // Initial generation
    public SemanticOperation(Operation operation, Set<TestParameter> testParameters){

        this.operationName = operation.getOperationId();
        this.operationPath = operation.getTestPath();
        this.operationMethod = operation.getMethod();
        this.operationId = operation.getOperationId();
        this.semanticParameters = generateSemanticParameters(testParameters);

    }

    // Learn regex
    public SemanticOperation(Operation operation, List<SemanticParameter> semanticParameters) {

        this.operationName = operation.getOperationId();
        this.operationPath = operation.getTestPath();
        this.operationMethod = operation.getMethod();
        this.operationId = operation.getOperationId();
        this.semanticParameters = new HashSet<>(semanticParameters);
    }

    public String getOperationName() {
        return operationName;
    }
    public String getOperationPath() {
        return operationPath;
    }
    public String getOperationId() {
        return operationId;
    }
    public String getOperationMethod() {
        return operationMethod;
    }

    public Set<SemanticParameter> getSemanticParameters() {
        return semanticParameters;
    }

    public void updateSemanticParametersValues(Map<String, Set<String>> result){
        for(SemanticParameter semanticParameter: this.semanticParameters){
            Set<String> values = result.get(semanticParameter.getTestParameter().getName());
            if(values!=null) {
                semanticParameter.addValues(values);
            }
        }
    }

    public static Set<SemanticOperation> getSemanticOperationsWithValuesFromPreviousIterations(List<Operation> operations, String experimentName){
        Set<SemanticOperation> res = new HashSet<>();

        // For each operation
        for(Operation operation: operations) {
            // For each test parameter
            List<SemanticParameter> semanticParameters = new ArrayList<>();
            if (operation.getTestParameters() != null) {
                for(TestParameter testParameter: operation.getTestParameters()) {
                    List<Generator> generatorList = testParameter.getGenerators();
                    for (Generator generator : generatorList) {
                        if (generator.getType().equals(RANDOM_INPUT_VALUE)) {
                            List<GenParameter> genParameters = generator.getGenParameters();
                            for (GenParameter genParameter : genParameters) {
                                // If the test parameter contains a "predicates" genParameter (i.e., It is a SemanticParameter)
                                if (genParameter.getName().equals(PREDICATES)) {
                                    // Add the SemanticParameter to the list
                                    // Includes valid and invalid values from previous iterations (if any)
                                    SemanticParameter semanticParameter = new SemanticParameter(testParameter, genParameters, genParameter.getValues(), experimentName, operation.getOperationId());
                                    semanticParameters.add(semanticParameter);

                                }
                            }

                        }
                    }
                }
            }

        // Add semanticOperation to res
        if(!semanticParameters.isEmpty()) {
            SemanticOperation semanticOperation = new SemanticOperation(operation, semanticParameters);
            res.add(semanticOperation);
        }
        }

        // Return semantic operations
        return res;

    }

    public void updateCSVWithValidAndInvalidValues(String experimentName){
        // Write all the valid and invalid parameter values as CSV
        for(SemanticParameter semanticParameter: this.semanticParameters){

            // Valid and invalid paths
            String validPath = semanticParameter.getValidCSVPath(experimentName, this.operationId);
            String invalidPath = semanticParameter.getInvalidCSVPath(experimentName, this.operationId);

            // Set of valid and invalid values
            Set<String> validValues = semanticParameter.getValidValues();
            Set<String> invalidValues = semanticParameter.getInvalidValues();

            // Delete possible intersections
            Set<String> intersection = new HashSet<>(validValues);
            intersection.retainAll(invalidValues);
            invalidValues.removeAll(intersection);

            // Update CSV files
            // Write the set of values as CSV
            collectionToCSV(validPath, validValues);
            collectionToCSV(invalidPath, invalidValues);


            logger.info("---------------------------------------------------------------------------");
            logger.info("Parameter: {}", semanticParameter.getTestParameter().getName());
            logger.info("Valid values: {}", validValues);
            logger.info("Invalid values: {}", invalidValues);
            logger.info("---------------------------------------------------------------------------");

        }
    }

}
