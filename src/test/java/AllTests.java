

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import es.us.isa.rester.configuration.*;
import es.us.isa.rester.configuration.generators.DefaultTestConfigurationGeneratorTest;
import es.us.isa.rester.generators.AmadeusRandomTestCaseGenerator;
import es.us.isa.rester.generators.PlaylistRandomTestCaseGeneratorTest;
import es.us.isa.rester.generators.SpotifyRandomTestCaseGeneratorTest;
import es.us.isa.rester.inputs.fixed.InputValueIteratorTest;
import es.us.isa.rester.inputs.random.*;
import es.us.isa.rester.specification.*;
import es.us.isa.rester.testcases.writters.RESTAssuredWritterTest;

@RunWith(Suite.class)
@SuiteClasses({ 
	TestConfigurationTest.class,
	InputValueIteratorTest.class,
	RandomDateGeneratorTest.class, 
	RandomEnglishWordGeneratorTest.class, 
	RandomInputValueIteratorTest.class,
	RandomNumberGeneratorTest.class, 
	RandomRegExpGeneratorTest.class,
	OpenAPISpecificationTest.class,
	RESTAssuredWritterTest.class,
	DefaultTestConfigurationGeneratorTest.class,
	SpotifyRandomTestCaseGeneratorTest.class,
	PlaylistRandomTestCaseGeneratorTest.class,
	AmadeusRandomTestCaseGenerator.class})

public class AllTests {

}
