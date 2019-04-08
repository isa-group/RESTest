package es.us.isa.restest.inputs.boundary;

import static org.junit.Assert.*;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.inputs.boundary.BoundaryStringConfigurator;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BoundaryStringConfiguratorTest {

    @Test
    public void testConstructor() {
        BoundaryStringConfigurator boundStrConf = new BoundaryStringConfigurator();
        assertEquals("delta property should be 2 by default", 2, boundStrConf.getDelta());
        assertEquals("maxLength property should be 1024 by default", 1024, boundStrConf.getMaxLength());
        assertEquals("minLength property should be 0 by default", 0, boundStrConf.getMinLength());
        assertEquals("includeEmptyString property should be true by default", true, boundStrConf.getIncludeEmptyString());
        assertEquals("includeNullCharacter property should be true by default", true, boundStrConf.getIncludeNullCharacter());
    }

    @Test
    public void testReturnedValuesSizeDefault() {
        BoundaryStringConfigurator boundStrConf = new BoundaryStringConfigurator();
        assertEquals("Wrong number of values returned", 14, boundStrConf.returnValues().size());
    }

    @Test
    public void testReturnedValuesSizeNoSpecialCases() {
        BoundaryStringConfigurator boundStrConf = new BoundaryStringConfigurator();
        boundStrConf.setIncludeNullCharacter(false);
        boundStrConf.setIncludeEmptyString(false);
        assertEquals("Wrong number of values returned", 12, boundStrConf.returnValues().size());
    }

    @Test
    public void testReturnedValuesSizeWithMinLengthMinusDeltaCases() {
        BoundaryStringConfigurator boundStrConf = new BoundaryStringConfigurator();
        boundStrConf.setMinLength(4);
        assertEquals("Wrong number of values returned", 16, boundStrConf.returnValues().size());
    }

    @Test
    public void testValuesIterator() {
        // Create generator and parameters
        Generator gen = new Generator();
        List<GenParameter> genParams = new ArrayList<>();

        // Set minLength parameter so that minLength-delta cases can be tested too
        GenParameter minLengthParam = new GenParameter();
        minLengthParam.setName("minLength");
        List<String> paramValues = new ArrayList<>();
        paramValues.add("4");
        minLengthParam.setValues(paramValues);

        genParams.add(minLengthParam);
        gen.setGenParameters(genParams);
        gen.setType("BoundaryString");

        // Generate InputValueIterator of boundary strings
        ITestDataGenerator boundStrIter = TestDataGeneratorFactory.createTestDataGenerator(gen);

        assertEquals("Wrong next value obtained", "", boundStrIter.nextValue());
        assertEquals("Wrong next value obtained", "\0", boundStrIter.nextValue());
        assertEquals("Wrong string length for next value", 4, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 6, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 1024, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 1026, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 514, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 4, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 6, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 1024, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 1026, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 514, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 2, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 2, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 1022, boundStrIter.nextValueAsString().length());
        assertEquals("Wrong string length for next value", 1022, boundStrIter.nextValueAsString().length());
    }

}
