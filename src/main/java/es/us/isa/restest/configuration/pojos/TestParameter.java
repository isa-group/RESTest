
package es.us.isa.restest.configuration.pojos;


import java.util.Collections;
import java.util.List;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.GEN_PARAM_REG_EXP;
import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.RANDOM_INPUT_VALUE;

public class TestParameter {

    private String name;
    private String in;
    private Float weight;
    private List<Generator> generators;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public Float getWeight() {
        return weight;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }

    public List<Generator> getGenerators() {
        return generators;
    }

    public void setGenerators(List<Generator> generators) {
        this.generators = generators;
    }

    public void addRegexToSemanticParameter(String regex){

        List<Generator> generators = this.generators;

        for(Generator generator: generators){
            if(generator.isValid() && generator.getType().equals(RANDOM_INPUT_VALUE)){
                for(GenParameter genParameter: generator.getGenParameters()){
                    if(genParameter.getName().equals("predicates")){

                        GenParameter regexGenParameter = new GenParameter();
                        regexGenParameter.setName(GEN_PARAM_REG_EXP);
                        regexGenParameter.setValues(Collections.singletonList(regex));

                        generator.addGenParameter(regexGenParameter);

                        break;

                    }
                }

            }

        }

        this.generators = generators;

    }

}
