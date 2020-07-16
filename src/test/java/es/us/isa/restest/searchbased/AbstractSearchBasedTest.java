package es.us.isa.restest.searchbased;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import es.us.isa.restest.searchbased.objectivefunction.SuiteSize;

public abstract class AbstractSearchBasedTest {
	// Paths to OAS specification files
	List<String> OAISpecPaths = Arrays.asList("src/test/resources/Bikewise/swagger.yaml");
	// Path to test configuration files
	List<String> confPaths = Arrays.asList("src/test/resources/Bikewise/fullConf.yaml");
	List<String> resourcePaths =Arrays.asList("/v2/incidents");
	String targetDir ="src/generation/java/searchbasedtests";
	List<String> methods =Arrays.asList("GET");
	public List<RestfulAPITestSuiteGenerationProblem> createTestProblems(){
		List<RestfulAPITestSuiteGenerationProblem> problems=new ArrayList<>();
		for(int i=0;i<OAISpecPaths.size();i++)
		{
			problems.add(
					SearchBasedTestSuiteGenerator.
						buildProblem(OAISpecPaths.get(i),
							confPaths.get(i),
								Arrays.asList(new SuiteSize()),
							targetDir, 2, 2)					
					);
		}
		return problems;
	}

}
