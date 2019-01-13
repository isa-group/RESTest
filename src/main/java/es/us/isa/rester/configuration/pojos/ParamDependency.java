package es.us.isa.rester.configuration.pojos;

import java.util.List;

public class ParamDependency {

    private String dependency;
    //private List<TestParameter> parameters = null;


    public ParamDependency(String dependency) {
        this.dependency = dependency;
    }

    public String getDependency() {
        return dependency;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    /*public List<TestParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<TestParameter> parameters) {
        this.parameters = parameters;
    }*/

}
