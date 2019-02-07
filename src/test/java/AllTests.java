

import es.us.isa.rester.generators.SimpleAPIFullTestCaseGenerator;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import es.us.isa.rester.configuration.*;
import es.us.isa.rester.configuration.generators.DefaultTestConfigurationGeneratorTest;
import es.us.isa.rester.generators.*;
import es.us.isa.rester.inputs.fixed.InputValueIteratorTest;
import es.us.isa.rester.inputs.random.*;
import es.us.isa.rester.inputs.boundary.*;
import es.us.isa.rester.specification.*;
import es.us.isa.rester.testcases.writters.RESTAssuredWritterTest;

@RunWith(Suite.class)
@SuiteClasses({
	DefaultTestConfigurationGeneratorTest.class,
	TestConfigurationTest.class,
	BoundaryNumberConfiguratorTest.class,
	BoundaryStringConfiguratorTest.class,
	InputValueIteratorTest.class,
	RandomDateGeneratorTest.class, 
	RandomEnglishWordGeneratorTest.class, 
	RandomInputValueIteratorTest.class,
	RandomNumberGeneratorTest.class, 
	RandomRegExpGeneratorTest.class,
	RandomStringGeneratorTest.class,
	OpenAPISpecificationTest.class,
	RESTAssuredWritterTest.class,
	AmadeusFullTestCaseGenerator.class,
	AmadeusRandomTestCaseGenerator.class,
	BikewiseFullTestCaseGenerator.class,
	DataAtWorkFullTestCaseGeneratorTest.class,
	PetstoreFullTestCaseGenerator.class,
	PlaylistRandomTestCaseGeneratorTest.class,
	SimpleAPIFullTestCaseGenerator.class,
	SpotifyRandomTestCaseGeneratorTest.class,
})

public class AllTests {

}
