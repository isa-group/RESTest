package es.us.isa.restest.searchbased;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;

public abstract class AbstractSearchBasedTest {
	// Paths to OAS specification files
	List<String> OAISpecPaths = List.of("src/test/resources/Bikewise/swagger.yaml");		    
	// Path to test configuration files
	List<String> confPaths = List.of("src/test/resources/Bikewise/fullConf.yaml");		    
	List<String> resourcePaths =List.of("/v2/incidents");
	String targetDir ="src/generation/java/searchbasedtests";
	List<String> methods =List.of("GET");
	
	public List<RestfulAPITestSuiteGenerationProblem> createTestProblems(){
		List<RestfulAPITestSuiteGenerationProblem> problems=new ArrayList<>();
		for(int i=0;i<OAISpecPaths.size();i++)
		{
			problems.add(
					SearchBasedTestSuiteGenerator.
						buildProblem(OAISpecPaths.get(i),
							Optional.of(confPaths.get(i)),
							Optional.of(resourcePaths.get(i)),
							Optional.of(methods.get(i)),
							List.of(new SuiteSize()), 
							targetDir, 2, 4)					
					);
		}
		return problems;
	}

}
