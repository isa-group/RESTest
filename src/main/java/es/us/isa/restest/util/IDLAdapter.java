package es.us.isa.restest.util;

import es.us.isa.restest.testcases.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * This class implements a set of methods for adapting RESTest objects to IDL or
 * IDLReasoner objects, e.g., test cases.
 *
 * @author Alberto Martin-Lopez
 */
public class IDLAdapter {

//    public static TestCase idl2restestTestCase(Map<String, String> request) {
//
//    }

    public static Map<String, String> restest2idlTestCase(TestCase tc) {
        Map<String, String> request = new HashMap<>();

        tc.getPathParameters().forEach(request::put);
        tc.getHeaderParameters().forEach(request::put);
        tc.getQueryParameters().forEach(request::put);
        tc.getFormParameters().forEach(request::put);

        return request;
    }
}
