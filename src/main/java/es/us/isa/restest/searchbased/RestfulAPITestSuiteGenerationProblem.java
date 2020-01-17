/**
 * 
 */
package es.us.isa.restest.searchbased;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.impl.AbstractGenericProblem;

public class RestfulAPITestSuiteGenerationProblem extends AbstractGenericProblem<RestfulAPITestSuiteSolution>
{

    @Override
    public void evaluate(RestfulAPITestSuiteSolution s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public RestfulAPITestSuiteSolution createSolution() {
        return new RestfulAPITestSuiteSolution(this);
    }
    
}