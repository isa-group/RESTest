package es.us.isa.restest.mutation.operators;

import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static es.us.isa.restest.util.SpecificationVisitor.getParametersSubjectToInvalidValueChange;

/**
 * @author Alberto Martin-Lopez
 */
public class InvalidParameterValue extends AbstractMutationOperator {

    /**
     * If possible, inserts an invalid value into some parameter of a test case.
     * For example, inserts a string value into an integer parameter.
     *
     * @param tc Test case to mutate. NOTE: If the mutation is applied, the original
     *           {@code tc} object will not be preserved, it should be cloned before
     *           calling this method.
     * @param specOperation Swagger operation related to the test case. Necessary
     *                      to extract all required parameters.
     * @return True if the mutation was applied, false otherwise.
     */
    public static Boolean mutate(TestCase tc, Operation specOperation) {
        List<Parameter> candidateParameters = getParametersSubjectToInvalidValueChange(specOperation); // Parameters that can be mutated to create a faulty test case
        if (candidateParameters.size() != 0) {
            Parameter selectedParam = candidateParameters.get(ThreadLocalRandom.current().nextInt(0, candidateParameters.size())); // Select one randomly
            setParameterToInvalidValue(tc, selectedParam); // Mutate it to create a faulty test case
            return true; // Mutation applied
        } else {
            return false; // Mutation not applied
        }
    }

    /**
     * Receives a test case and a parameter to mutate and mutates it.
     * TODO: Improve the mutation alternatives.
     * @param tc
     * @param param
     */
    private static void setParameterToInvalidValue(TestCase tc, Parameter param) {
        ParameterFeatures pFeatures = new ParameterFeatures(param);
        String randomBigInt = Integer.toString(ThreadLocalRandom.current().nextInt(1000, 10001));
        Integer randomSmallInt = ThreadLocalRandom.current().nextInt(1, 10);
        String randomString = RandomStringUtils.randomAscii(ThreadLocalRandom.current().nextInt(10, 20));

        if (pFeatures.getEnumValues() != null) { // Value of enum range
            if (pFeatures.getType().equals("integer") || pFeatures.getType().equals("number"))
                setParameterToValue(tc, param, randomBigInt); // Number enum
            else if (pFeatures.getType().equals("string"))
                setParameterToValue(tc, param, randomString); // String enum
        } else if (pFeatures.getType().equals("boolean")) { // Boolean parameter with different type (e.g. string)
            setParameterToValue(tc, param, randomString);
        } else if ((pFeatures.getType().equals("integer") || pFeatures.getType().equals("number"))) { // Number
            if (pFeatures.getMin() != null) { // Number with min constraint. Violate it
                if (pFeatures.getType().equals("number"))
                    setParameterToValue(tc, param, Float.toString(pFeatures.getMin().floatValue()-randomSmallInt));
                else if (pFeatures.getType().equals("integer"))
                    setParameterToValue(tc, param, Integer.toString(pFeatures.getMin().intValue()-randomSmallInt));
            } else if (pFeatures.getMax() != null) { // Number with max constraint. Violate it
                if (pFeatures.getType().equals("number"))
                    setParameterToValue(tc, param, Float.toString(pFeatures.getMax().floatValue()+randomSmallInt));
                else if (pFeatures.getType().equals("integer"))
                    setParameterToValue(tc, param, Integer.toString(pFeatures.getMax().intValue()+randomSmallInt));
            } else // Number parameter with different type (e.g. string)
                setParameterToValue(tc, param, randomString);
        } else if (pFeatures.getType().equals("string")) { // String
            if (pFeatures.getFormat() != null) // String with format (URL, email, etc.). Violate format using random string
                setParameterToValue(tc, param, randomString);
            else if (pFeatures.getMinLength() != null && pFeatures.getMinLength() > 1) // Replace with string with fewer chars than minLength
                setParameterToValue(tc, param, RandomStringUtils.randomAscii(pFeatures.getMinLength()-1));
            else if (pFeatures.getMaxLength() != null) // Replace with string with more chars than maxLength
                setParameterToValue(tc, param, RandomStringUtils.randomAscii(pFeatures.getMaxLength() + randomSmallInt));
        }
    }
}
