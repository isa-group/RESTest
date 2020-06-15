package es.us.isa.restest.mutation.operators;

import es.us.isa.restest.specification.ParameterFeatures;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.v3.oas.models.Operation;
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

    public static final String INTEGER_TYPE = "integer";
    public static final String BOOLEAN_TYPE = "boolean";
    public static final String STRING_TYPE = "string";
    public static final String NUMBER_TYPE = "number";

    /**
     * If possible, inserts an invalid value into some parameter of a test case.
     * For example, inserts a string value into an integer parameter.
     *
     * @param tc Test case to mutate. NOTE: If the mutation is applied, the original
     *           {@code tc} object will not be preserved, it should be cloned before
     *           calling this method.
     * @param specOperation OpenAPI operation related to the test case. Necessary
     *                      to extract all required parameters.
     * @return True if the mutation was applied, false otherwise.
     */

    public static Boolean mutate(TestCase tc, Operation specOperation) {
        List<ParameterFeatures> candidateParameters = getParametersSubjectToInvalidValueChange(specOperation); // Parameters that can be mutated to create a faulty test case
        if (!candidateParameters.isEmpty()) {
            ParameterFeatures selectedParam = candidateParameters.get(ThreadLocalRandom.current().nextInt(0, candidateParameters.size())); // Select one randomly
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
    private static Boolean setParameterToInvalidValue(TestCase tc, ParameterFeatures param) {
        String randomBigInt = Integer.toString(ThreadLocalRandom.current().nextInt(1000, 10001));
        Integer randomSmallInt = ThreadLocalRandom.current().nextInt(1, 10);
        String randomString = RandomStringUtils.randomAlphabetic(10, 20);
        String randomBoolean = Boolean.toString(ThreadLocalRandom.current().nextBoolean());


        if (param.getEnumValues() != null) { // Value of enum range
            setEnumParameterToInvalidValue(tc, param, randomBigInt, randomString, randomBoolean);
        } else if (param.getType().equals(BOOLEAN_TYPE)) { // Boolean parameter with different type (e.g. string)
            setBooleanParameterToInvalidValue(tc, param, randomBigInt, randomString);
        } else if ((param.getType().equals(INTEGER_TYPE) || param.getType().equals(NUMBER_TYPE))) { // Number
            if (setNumberParameterToInvalidValue(tc, param, randomSmallInt, randomString, randomBoolean)) return true;
        } else if (param.getType().equals(STRING_TYPE)) { // String
            return setStringParameterToInvalidValue(tc, param, randomSmallInt, randomString);
        } else {
            return false;
        }

        return true;
    }

    private static void setEnumParameterToInvalidValue(TestCase tc, ParameterFeatures param, String randomBigInt, String randomString, String randomBoolean) {
        float randomSelection = ThreadLocalRandom.current().nextFloat();
        int alt = 3; // Number of total alternatives when mutating the parameter

        if(randomSelection < 1f/alt && !param.getEnumValues().contains(randomBoolean))
            tc.addParameter(param, randomBoolean); // Boolean enum
        else if(randomSelection < 2f/alt)
            tc.addParameter(param, randomString); // String enum
        else
            tc.addParameter(param, randomBigInt); // Number enum
    }

    private static void setBooleanParameterToInvalidValue(TestCase tc, ParameterFeatures param, String randomBigInt, String randomString) {
        float randomSelection = ThreadLocalRandom.current().nextFloat();
        int alt = 2; // Number of total alternatives when mutating the parameter

        if(randomSelection < 1f/alt)
            tc.addParameter(param, randomString); //Boolean parameter with string type
        else
            tc.addParameter(param, randomBigInt); // Boolean parameter with int type
    }

    private static boolean setNumberParameterToInvalidValue(TestCase tc, ParameterFeatures param, Integer randomSmallInt, String randomString, String randomBoolean) {
        int alt = 4; // Number of total alternatives when mutating the parameter
        List<Boolean> mutationsTried = new ArrayList<>(Arrays.asList(new Boolean[alt])); // In case one mutation cannot be applied, try others
        Collections.fill(mutationsTried, Boolean.FALSE);
        float randomSelection;

        while (mutationsTried.contains(Boolean.FALSE)) {
            randomSelection = ThreadLocalRandom.current().nextFloat();
            if (!mutationsTried.get(0) && randomSelection < 1f / alt) { // Number with min constraint. Violate it
                if (violateMinConstraintOfNumber(tc, param, randomSmallInt, mutationsTried)) return true;
            } else if (!mutationsTried.get(1) && randomSelection < 2f / alt) { // Number with max constraint. Violate it
                if (violateMaxConstraintOfNumber(tc, param, randomSmallInt, mutationsTried)) return true;
            } else if (!mutationsTried.get(2) && randomSelection < 3f / alt) { // Number parameter with string type
                tc.addParameter(param, randomString);
                return true;
            } else { // Number parameter with boolean type
                tc.addParameter(param, randomBoolean);
                return true;
            }
        }
        return false;
    }

    private static boolean violateMaxConstraintOfNumber(TestCase tc, ParameterFeatures param, Integer randomSmallInt, List<Boolean> mutationsTried) {
        if (param.getMax() != null) {
            if (param.getType().equals(NUMBER_TYPE))
                tc.addParameter(param, Double.toString(param.getMax().doubleValue() + randomSmallInt));
            else if (param.getType().equals(INTEGER_TYPE))
                tc.addParameter(param, Long.toString(param.getMax().longValue() + randomSmallInt));
            return true;
        } else
            mutationsTried.set(1, true); // This mutation cannot be applied, do not try again
        return false;
    }

    private static boolean violateMinConstraintOfNumber(TestCase tc, ParameterFeatures param, Integer randomSmallInt, List<Boolean> mutationsTried) {
        if (param.getMin() != null) {
            if (param.getType().equals(NUMBER_TYPE))
                tc.addParameter(param, Double.toString(param.getMin().doubleValue() - randomSmallInt));
            else if (param.getType().equals(INTEGER_TYPE))
                tc.addParameter(param, Long.toString(param.getMin().longValue() - randomSmallInt));
            return true;
        } else
            mutationsTried.set(0, true); // This mutation cannot be applied, do not try again
        return false;
    }

    //TODO: Refactoring
    private static Boolean setStringParameterToInvalidValue(TestCase tc, ParameterFeatures param, Integer randomSmallInt, String randomString) {
        int alt; // Number of total alternatives when mutating the parameter
        List<Boolean> mutationsTried; // In case one mutation cannot be applied, try others
        float randomSelection;
        alt = 3;
        mutationsTried = new ArrayList<>(Arrays.asList(new Boolean[alt]));
        Collections.fill(mutationsTried, Boolean.FALSE);
        while (mutationsTried.contains(Boolean.FALSE)) {
            randomSelection = ThreadLocalRandom.current().nextFloat();
            if (!mutationsTried.get(0) && randomSelection < 1f / alt) { // String with format (URL, email, etc.). Violate format using random string
                if (param.getFormat() != null) {
                    tc.addParameter(param, randomString);
                    return true;
                } else
                    mutationsTried.set(0, true); // This mutation cannot be applied, do not try again
            } else if (!mutationsTried.get(1) && randomSelection < 2f / alt) { // Replace with string with fewer chars than minLength
                if (param.getMinLength() != null && param.getMinLength() > 1) {
                    tc.addParameter(param, RandomStringUtils.randomAlphabetic(param.getMinLength() - 1));
                    return true;
                } else
                    mutationsTried.set(1, true); // This mutation cannot be applied, do not try again
            } else if (!mutationsTried.get(2) && randomSelection < 3f / alt) { // Replace with string with more chars than maxLength
                if (param.getMaxLength() != null) {
                    tc.addParameter(param, RandomStringUtils.randomAlphabetic(param.getMaxLength() + randomSmallInt));
                    return true;
                } else
                    mutationsTried.set(2, true); // This mutation cannot be applied, do not try again
            } else {
                return false;
            }
        }
        return false;
    }


}
