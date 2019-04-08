

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import es.us.isa.restest.configuration.*;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGeneratorTest;
import es.us.isa.restest.generators.*;
import es.us.isa.restest.inputs.boundary.*;
import es.us.isa.restest.inputs.fixed.InputValueIteratorTest;
import es.us.isa.restest.inputs.random.*;
import es.us.isa.restest.specification.*;
import es.us.isa.restest.testcases.writters.RESTAssuredWritterTest;

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
