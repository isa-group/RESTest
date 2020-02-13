package es.us.isa.restest.mutation.operators;

import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static es.us.isa.restest.util.SpecificationVisitor.getRequiredNotPathParameters;

/**
 * @author Alberto Martin-Lopez
 */
public class RemoveRequiredParameter extends AbstractMutationOperator {

    /**
     * If possible, removed a required parameter from a test case.
     *
     * @param tc Test case to mutate. NOTE: If the mutation is applied, the original
     *           {@code tc} object will not be preserved, it should be cloned before
     *           calling this method.
     * @param specOperation Swagger operation related to the test case. Necessary
     *                      to extract all parameters that are subject to an invalid
     *                      value change.
     * @return True if the mutation was applied, false otherwise.
     */
    public static Boolean mutate(TestCase tc, Operation specOperation) {
        List<Parameter> candidateParameters = getRequiredNotPathParameters(specOperation); // Path parameters cannot be removed
        if (candidateParameters.size() != 0) {
            Parameter selectedParam = candidateParameters.get(ThreadLocalRandom.current().nextInt(0, candidateParameters.size()));
            removeParameter(tc, selectedParam);
            return true; // Mutation applied
        } else {
            return false; // Mutation not applied
        }
    }
}
