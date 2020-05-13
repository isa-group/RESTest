package es.us.isa.restest.mutation.operators;

import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
     * @param tc
     * @param param
     */
    private static Boolean setParameterToInvalidValue(TestCase tc, Parameter param) {
        ParameterFeatures pFeatures = new ParameterFeatures(param);
        String randomBigInt = Integer.toString(ThreadLocalRandom.current().nextInt(1000, 10001));
        Integer randomSmallInt = ThreadLocalRandom.current().nextInt(1, 10);
        String randomString = RandomStringUtils.randomAlphabetic(10, 20);
        String randomBoolean = Boolean.toString(ThreadLocalRandom.current().nextBoolean());
        Float randomSelection = ThreadLocalRandom.current().nextFloat();
        Integer alt; // Number of total alternatives when mutating the parameter
        List<Boolean> mutationsTried; // In case one mutation cannot be applied, try others


        if (pFeatures.getEnumValues() != null) { // Value of enum range
            alt = 3;
            if(randomSelection < 1f/alt && !pFeatures.getEnumValues().contains(randomBoolean))
                setParameterToValue(tc, param, randomBoolean); // Boolean enum
            else if(randomSelection < 2f/alt)
                setParameterToValue(tc, param, randomString); // String enum
            else
                setParameterToValue(tc, param, randomBigInt); // Number enum
        } else if (pFeatures.getType().equals("boolean")) { // Boolean parameter with different type (e.g. string)
            alt = 2;
            if(randomSelection < 1f/alt)
                setParameterToValue(tc, param, randomString); //Boolean parameter with string type
            else
                setParameterToValue(tc, param, randomBigInt); // Boolean parameter with int type
        } else if ((pFeatures.getType().equals("integer") || pFeatures.getType().equals("number"))) { // Number
            alt = 4;
            mutationsTried = new ArrayList<>(Arrays.asList(new Boolean[alt]));
            Collections.fill(mutationsTried, Boolean.FALSE);
            while (mutationsTried.contains(Boolean.FALSE)) {
                randomSelection = ThreadLocalRandom.current().nextFloat();
                if (!mutationsTried.get(0) && randomSelection < 1f / alt) { // Number with min constraint. Violate it
                    if (pFeatures.getMin() != null) {
                        if (pFeatures.getType().equals("number"))
                            setParameterToValue(tc, param, Double.toString(pFeatures.getMin().doubleValue() - randomSmallInt));
                        else if (pFeatures.getType().equals("integer"))
                            setParameterToValue(tc, param, Long.toString(pFeatures.getMin().longValue() - randomSmallInt));
                        return true;
                    } else
                        mutationsTried.set(0, true); // This mutation cannot be applied, do not try again
                } else if (!mutationsTried.get(1) && randomSelection < 2f / alt) { // Number with max constraint. Violate it
                    if (pFeatures.getMax() != null) {
                        if (pFeatures.getType().equals("number"))
                            setParameterToValue(tc, param, Double.toString(pFeatures.getMax().doubleValue() + randomSmallInt));
                        else if (pFeatures.getType().equals("integer"))
                            setParameterToValue(tc, param, Long.toString(pFeatures.getMax().longValue() + randomSmallInt));
                        return true;
                    } else
                        mutationsTried.set(1, true); // This mutation cannot be applied, do not try again
                } else if (!mutationsTried.get(2) && randomSelection < 3f / alt) { // Number parameter with string type
                    setParameterToValue(tc, param, randomString);
                    return true;
                } else { // Number parameter with boolean type
                    setParameterToValue(tc, param, randomBoolean);
                    return true;
                }
            }
        } else if (pFeatures.getType().equals("string")) { // String
            alt = 3;
            mutationsTried = new ArrayList<>(Arrays.asList(new Boolean[alt]));
            Collections.fill(mutationsTried, Boolean.FALSE);
            while (mutationsTried.contains(Boolean.FALSE)) {
                randomSelection = ThreadLocalRandom.current().nextFloat();
                if (!mutationsTried.get(0) && randomSelection < 1f / alt) { // String with format (URL, email, etc.). Violate format using random string
                    if (pFeatures.getFormat() != null) {
                        setParameterToValue(tc, param, randomString);
                        return true;
                    } else
                        mutationsTried.set(0, true); // This mutation cannot be applied, do not try again
                } else if (!mutationsTried.get(1) && randomSelection < 2f / alt) { // Replace with string with fewer chars than minLength
                    if (pFeatures.getMinLength() != null && pFeatures.getMinLength() > 1) {
                        setParameterToValue(tc, param, RandomStringUtils.randomAlphabetic(pFeatures.getMinLength() - 1));
                        return true;
                    } else
                        mutationsTried.set(1, true); // This mutation cannot be applied, do not try again
                } else if (!mutationsTried.get(2) && randomSelection < 3f / alt) { // Replace with string with more chars than maxLength
                    if (pFeatures.getMaxLength() != null) {
                        setParameterToValue(tc, param, RandomStringUtils.randomAlphabetic(pFeatures.getMaxLength() + randomSmallInt));
                        return true;
                    } else
                        mutationsTried.set(2, true); // This mutation cannot be applied, do not try again
                } else {
                    return false;
                }
            }
            return false;
        } else {
            return false;
        }

        return true;
    }
}
