

import es.us.isa.restest.inputs.perturbation.ObjectPerturbatorTest;
import es.us.isa.restest.mutation.TestCaseMutationTest;
import es.us.isa.restest.util.*;
import es.us.isa.restest.testcases.restassured.filters.NominalOrFaultyTestCaseFilterTest;
import es.us.isa.restest.testcases.restassured.filters.ResponseValidationFilterTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import es.us.isa.restest.configuration.*;
import es.us.isa.restest.configuration.generators.DefaultTestConfigurationGeneratorTest;
import es.us.isa.restest.coverage.CoverageGathererTest;
import es.us.isa.restest.coverage.CoverageMeterTest;
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
	RandomObjectGeneratorTest.class,
	RandomStringGeneratorTest.class,
	ObjectPerturbatorTest.class,
	OpenAPISpecificationTest.class,
	CoverageGathererTest.class,
	CoverageMeterTest.class,
	TestCaseMutationTest.class,
	RESTAssuredWritterTest.class,
//	AmadeusFullTestCaseGenerator.class,
	AmadeusRandomTestCaseGenerator.class,
	BikewiseFullTestCaseGenerator.class,
//	DataAtWorkFullTestCaseGeneratorTest.class,
	PetstoreFullTestCaseGenerator.class,
	PlaylistRandomTestCaseGeneratorTest.class,
	SimpleAPIFullTestCaseGenerator.class,
	SpotifyRandomTestCaseGeneratorTest.class,
	CommentsObjectPerturbationTestCaseGeneratorTest.class,
	CommentsRandomTestCaseGeneratorTest.class,
	TravelRandomTestCaseGenerator.class,
	AllureReportManagerTest.class,
	CSVManagerTest.class,
	FileManagerTest.class,
	IDGeneratorTest.class,
	JSONManagerTest.class,
	SpecificationVisitorTest.class,
	IDLAdapterTest.class,
	NominalOrFaultyTestCaseFilterTest.class,
	ResponseValidationFilterTest.class,
	TimerTest.class,
	JsonMutatorTest.class,
	TestManagerTest.class
})

public class AllTests {

}
