package es.us.isa.restest.util;

import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.testcases.TestCase;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import java.util.HashMap;
import java.util.Map;

import static es.us.isa.restest.util.SpecificationVisitor.findParameter;

/**
 * This class implements a set of methods for adapting RESTest objects to IDL or
 * IDLReasoner objects, e.g., test cases.
 *
 * @author Alberto Martin-Lopez
 */
public class IDLAdapter {

    public static void idl2restestTestCase(TestCase tc, Map<String, String> request, Operation specOperation) {
        for (Map.Entry<String, String> parameter: request.entrySet()) {
            Parameter specParameter = findParameter(specOperation, parameter.getKey());
            switch (specParameter.getIn()) {
                case "header":
                    tc.addHeaderParameter(parameter.getKey(), parameter.getValue());
                    break;
                case "query":
                    tc.addQueryParameter(parameter.getKey(), parameter.getValue());
                    break;
                case "path":
                    tc.addPathParameter(parameter.getKey(), parameter.getValue());
                    break;
                case "formData":
                    tc.addFormParameter(parameter.getKey(), parameter.getValue());
                    break;
                default:
                    throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
            }
        }
    }

    public static Map<String, String> restest2idlTestCase(TestCase tc) {
        Map<String, String> request = new HashMap<>();

        tc.getPathParameters().forEach(request::put);
        tc.getHeaderParameters().forEach(request::put);
        tc.getQueryParameters().forEach(request::put);
        tc.getFormParameters().forEach(request::put);

        return request;
    }
}
