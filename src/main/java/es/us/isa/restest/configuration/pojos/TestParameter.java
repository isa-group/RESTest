
package es.us.isa.restest.configuration.pojos;


import java.util.Collections;
import java.util.List;

import static es.us.isa.restest.configuration.generators.DefaultTestConfigurationGenerator.*;

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

    public void addRegexToTestParameter(String regex){

        for(Generator generator: this.generators){
            if(Boolean.TRUE.equals(generator.isValid()) && generator.getType().equals(RANDOM_INPUT_VALUE)){
                for(GenParameter genParameter: generator.getGenParameters()){
                    if(genParameter.getName().equals(PREDICATES)){

                        GenParameter oldGenParameter = generator.getGenParameters().stream().filter(x-> x.getName().equals("regExp")).findFirst()
                                .orElse(null);
                        if(oldGenParameter != null){
                            generator.removeGenParameter(oldGenParameter);
                        }

                        GenParameter regexGenParameter = new GenParameter();
                        regexGenParameter.setName(GEN_PARAM_REG_EXP);
                        regexGenParameter.setValues(Collections.singletonList(regex));

                        generator.addGenParameter(regexGenParameter);

                        break;

                    }
                }

            }

        }

    }

}
