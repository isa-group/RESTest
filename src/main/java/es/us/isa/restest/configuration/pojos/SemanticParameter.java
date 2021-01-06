package es.us.isa.restest.configuration.pojos;

import java.util.HashSet;
import java.util.Set;

public class SemanticParameter {

    private TestParameter testParameter;
    private Set<String> predicates;
    private Set<String> values;


    public SemanticParameter(TestParameter testParameter){
        Set<String> predicates = new HashSet<>();
        Set<String> values = new HashSet<>();

        this.testParameter = testParameter;
        this.predicates = predicates;
        this.values = values;
    }

    public TestParameter getTestParameter() {
        return testParameter;
    }

    public void setTestParameter(TestParameter testParameter) {
        this.testParameter = testParameter;
    }

    public Set<String> getPredicates() {
        return predicates;
    }

    public void setPredicates(Set<String> predicates) {
        this.predicates = predicates;
    }

    public Set<String> getValues() {
        return values;
    }

    public void setValues(Set<String> values) {
        this.values = values;
    }

    public void addValues(Set<String> values) {
        this.values.addAll(values);
    }

    public static Set<SemanticParameter> generateSemanticParameters(Set<TestParameter> testParameters){
        Set<SemanticParameter> res = new HashSet<>();

        for(TestParameter testParameter: testParameters){
            SemanticParameter generatedSemanticParameter = new SemanticParameter(testParameter);
            res.add(generatedSemanticParameter);
        }

        return res;
    }
}
