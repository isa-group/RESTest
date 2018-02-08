package es.us.isa.rester.configuration;

import org.junit.Test;
import static org.junit.Assert.*;

import es.us.isa.rester.configuration.TestConfigurationIO;
import es.us.isa.rester.configuration.pojos.TestConfigurationObject;

public class TestConfigurationTest {

	@Test
	public void testLoadConfiguration() {
		String path = "src/main/resources/TestConfigurationMetamodel/configuration-model";
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(path +".json");
		assertEquals("Wrong deseralization", 2, conf.getTestConfiguration().getTestPaths().get(0).getOperations().get(0).getTestParameters().get(0).getGenerator().getGenParameters().size());
		//System.out.println(TestConfigurationIO.toString(conf)); // Print to String
		TestConfigurationIO.toFile(conf, path + "-output.json");
	}

}
