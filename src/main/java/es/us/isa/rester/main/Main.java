package es.us.isa.rester.main;

import java.util.ArrayList;
import java.util.List;

import es.us.isa.rester.configuration.generators.DefaultTestConfigurationGenerator;
import es.us.isa.rester.specification.OpenAPISpecification;
import es.us.isa.rester.util.TestConfigurationFilter;

public class Main {

	
	public static void main(String[] args) {

		// Generate default test configuration file for Playlist API
		generatePlaylistConfig();

	}
	
	
	private static void generatePlaylistConfig() {
		
		String specPath="src/main/resources/Playlist/spec.json";
		String confPath="src/main/resources/Playlist/defaultConf.json";
		OpenAPISpecification spec = new OpenAPISpecification(specPath);
		
		List<TestConfigurationFilter> filters = new ArrayList<TestConfigurationFilter>();
		TestConfigurationFilter filter = new TestConfigurationFilter();
		filter.setPath("/songs/{id}");		// Set null for all paths
		filter.addGetMethod();
		filters.add(filter);
		
		DefaultTestConfigurationGenerator gen = new DefaultTestConfigurationGenerator();
		gen.generate(spec, confPath, filters);
	}
}