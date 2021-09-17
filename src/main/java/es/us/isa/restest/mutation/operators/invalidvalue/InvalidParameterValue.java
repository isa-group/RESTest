package es.us.isa.restest.mutation.operators.invalidvalue;

import es.us.isa.restest.mutation.operators.AbstractMutationOperator;
import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static es.us.isa.restest.util.SpecificationVisitor.getParametersFeaturesSubjectToInvalidValueChange;

/**
 * @author Alberto Martin-Lopez
 */
public class InvalidParameterValue extends AbstractMutationOperator {

    protected static final String INTEGER_TYPE = "integer";
    private static final String BOOLEAN_TYPE = "boolean";
    private static final String STRING_TYPE = "string";
    protected static final String NUMBER_TYPE = "number";

    /**
     * If possible, inserts an invalid value into some parameter of a test case.
     * For example, inserts a string value into an integer parameter.
     *
     * @param tc Test case to mutate. NOTE: If the mutation is applied, the original
     *           {@code tc} object will not be preserved, it should be cloned before
     *           calling this method.
     * @param specOperation OpenAPI operation related to the test case. Necessary
     *                      to extract all required parameters.
     * @return Description of the mutation applied, "" if none applied.
     */
    public static String mutate(TestCase tc, Operation specOperation) {
        List<ParameterFeatures> candidateParameters = getParametersFeaturesSubjectToInvalidValueChange(specOperation); // Parameters that can be mutated to create a faulty test case
        
        if (candidateParameters.isEmpty())
        	return "";
        	
        ParameterFeatures selectedParam = candidateParameters.get(ThreadLocalRandom.current().nextInt(0, candidateParameters.size())); // Select one randomly

        if (selectedParam.getEnumValues() != null) // Value of enum range
            return EnumToInvalid.mutate(tc, selectedParam);
        else if (selectedParam.getType().equals(BOOLEAN_TYPE)) // Boolean
            return BooleanToInvalid.mutate(tc, selectedParam);
        else if (selectedParam.getType().equals(INTEGER_TYPE)) // Integer
            return IntegerToInvalid.mutate(tc, selectedParam);
        else if (selectedParam.getType().equals(NUMBER_TYPE)) // Number
            return NumberToInvalid.mutate(tc, selectedParam);
        else if (selectedParam.getType().equals(STRING_TYPE)) // String
            return StringToInvalid.mutate(tc, selectedParam);

        return "";
        
    }
}
