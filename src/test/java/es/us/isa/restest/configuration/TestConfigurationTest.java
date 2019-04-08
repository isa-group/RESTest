package es.us.isa.restest.configuration;

import org.junit.Test;
import static org.junit.Assert.*;

import es.us.isa.restest.configuration.TestConfigurationIO;
import es.us.isa.restest.configuration.pojos.TestConfigurationObject;

public class TestConfigurationTest {

	@Test
	public void testLoadConfiguration() {
		String path = "src/main/resources/TestConfigurationMetamodel/configuration-model";
		TestConfigurationObject conf = TestConfigurationIO.loadConfiguration(path +".yaml");
		assertEquals("Wrong deseralization", 2, conf.getTestConfiguration().getTestPaths().get(0).getOperations().get(0).getTestParameters().get(0).getGenerator().getGenParameters().size());
		//System.out.println(TestConfigurationIO.toString(conf)); // Print to String
		TestConfigurationIO.toFile(conf, path + "-output.yaml");
	}

}
