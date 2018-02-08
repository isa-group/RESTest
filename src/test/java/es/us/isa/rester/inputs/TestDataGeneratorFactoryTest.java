package es.us.isa.rester.inputs;

import static org.junit.Assert.*;

import org.junit.Test;

import es.us.isa.rester.configuration.TestConfigurationIO;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;
import es.us.isa.rester.configuration.pojos.TestParameter;
import es.us.isa.rester.inputs.random.RandomInputValueIterator;
import es.us.isa.rester.util.TestConfigurationVisitor;

public class TestDataGeneratorFactoryTest {

	@Test
	public void testCreateTestDataGenerator() {
		
		// Load configuration
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration("src/test/resources/testconf/amadeusTestConf.json");
		
		// Get test parameter
		TestParameter param = TestConfigurationVisitor.getTestParameter(conf, "HotelAirportSearch", "location");
		
		// Create generator
		RandomInputValueIterator<String> gen = (RandomInputValueIterator<String>) TestDataGeneratorFactory.createTestDataGenerator(param.getGenerator());
		
		assertEquals("Wrong number of values", 521, gen.getValues().size());
		
		for(int i=0; i<100; i++) {
			System.out.println("Value " + i + ": " + gen.nextValueAsString());
		}
		
	}

}
