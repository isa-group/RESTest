package es.us.isa.restest.inputs.boundary;

import es.us.isa.restest.configuration.pojos.GenParameter;
import es.us.isa.restest.configuration.pojos.Generator;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.inputs.TestDataGeneratorFactory;
import es.us.isa.restest.inputs.boundary.BoundaryNumberConfigurator;
import es.us.isa.restest.util.DataType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class BoundaryNumberConfiguratorTest {

    Generator generator;

    @Before
    public void setupGenerator() {
        generator = new Generator();
        generator.setType("BoundaryNumber");
        generator.setGenParameters(new ArrayList<>());
    }

    @Test
    public void testDefaultConstructor() {
        BoundaryNumberConfigurator boundNumbConf = new BoundaryNumberConfigurator();

        assertEquals("Wrong min value", Integer.MIN_VALUE, boundNumbConf.getMin().intValue());
        assertEquals("Wrong max value", Integer.MAX_VALUE, boundNumbConf.getMax().intValue());
        assertEquals("Wrong delta value", (Double) 1d, boundNumbConf.getDelta());
        assertEquals("Wrong type value", DataType.INTEGER, boundNumbConf.getType());
    }

    @Test
    public void testDefaultReturnedValues() {
        BoundaryNumberConfigurator boundNumbConf = new BoundaryNumberConfigurator();
        List<? extends Number> values = boundNumbConf.returnValues();
        Iterator<? extends Number> iterator = values.iterator();

        assertEquals("Wrong next value", Integer.valueOf(Integer.MIN_VALUE).longValue(), iterator.next());
        assertEquals("Wrong next value", Integer.valueOf(Integer.MIN_VALUE).longValue()-1, iterator.next());
        assertEquals("Wrong next value", Integer.valueOf(Integer.MIN_VALUE).longValue()+1, iterator.next());
        assertEquals("Wrong next value", Integer.valueOf(Integer.MAX_VALUE).longValue(), iterator.next());
        assertEquals("Wrong next value", Integer.valueOf(Integer.MAX_VALUE).longValue()-1, iterator.next());
        assertEquals("Wrong next value", Integer.valueOf(Integer.MAX_VALUE).longValue()+1, iterator.next());
        assertEquals("Wrong next value", Double.valueOf(Math.ceil(Integer.valueOf(Integer.MIN_VALUE+Integer.MAX_VALUE).doubleValue()/2)).longValue(), iterator.next());
    }

    @Test
    public void testValuesIterator() {
        // Create generator and parameters
        Generator gen = new Generator();
        List<GenParameter> genParams = new ArrayList<>();

        // Set type, min and max parameters
        GenParameter typeParam = new GenParameter();
        typeParam.setName("type");
        List<String> typeParamValues = new ArrayList<>();
        typeParamValues.add("int32");
        typeParam.setValues(typeParamValues);
        genParams.add(typeParam);

        GenParameter minParam = new GenParameter();
        minParam.setName("min");
        List<String> minParamValues = new ArrayList<>();
        minParamValues.add("-1024");
        minParam.setValues(minParamValues);
        genParams.add(minParam);

        GenParameter maxParam = new GenParameter();
        maxParam.setName("max");
        List<String> maxParamValues = new ArrayList<>();
        maxParamValues.add("1023");
        maxParam.setValues(maxParamValues);
        genParams.add(maxParam);

        gen.setGenParameters(genParams);
        gen.setType("BoundaryNumber");

        // Generate InputValueIterator of boundary strings
        ITestDataGenerator boundNumbIter = TestDataGeneratorFactory.createTestDataGenerator(gen);

        assertEquals("Wrong next value obtained", "-1024", boundNumbIter.nextValueAsString());
        assertEquals("Wrong next value obtained", "-1025", boundNumbIter.nextValueAsString());
        assertEquals("Wrong next value obtained", "-1023", boundNumbIter.nextValueAsString());
        assertEquals("Wrong next value obtained", "1023", boundNumbIter.nextValueAsString());
        assertEquals("Wrong next value obtained", "1022", boundNumbIter.nextValueAsString());
        assertEquals("Wrong next value obtained", "1024", boundNumbIter.nextValueAsString());
        assertEquals("Wrong next value obtained", "0", boundNumbIter.nextValueAsString());
    }

    @Test
    public void testValuesRandomIterator() {
        // Create generator and parameters
        Generator gen = new Generator();
        List<GenParameter> genParams = new ArrayList<>();

        // Set type, min and max parameters
        GenParameter typeParam = new GenParameter();
        typeParam.setName("type");
        List<String> typeParamValues = new ArrayList<>();
        typeParamValues.add("int32");
        typeParam.setValues(typeParamValues);
        genParams.add(typeParam);

        GenParameter minParam = new GenParameter();
        minParam.setName("min");
        List<String> minParamValues = new ArrayList<>();
        minParamValues.add("-1024");
        minParam.setValues(minParamValues);
        genParams.add(minParam);

        GenParameter maxParam = new GenParameter();
        maxParam.setName("max");
        List<String> maxParamValues = new ArrayList<>();
        maxParamValues.add("1023");
        maxParam.setValues(maxParamValues);
        genParams.add(maxParam);

        gen.setGenParameters(genParams);
        gen.setType("RandomBoundaryNumber");

        // Generate InputValueIterator of boundary strings
        ITestDataGenerator boundNumbIter = TestDataGeneratorFactory.createTestDataGenerator(gen);

        List<String> values = Arrays.asList("-1024", "-1025", "-1023", "1023", "1022", "1024", "0");

        for(int i=0; i<7; i++) {
            String value = boundNumbIter.nextValueAsString();
            assertTrue("Wrong value obtained: " + value, values.contains(value));
        }
    }
}
