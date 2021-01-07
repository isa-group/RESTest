
package es.us.isa.restest.configuration.pojos;


import java.util.List;

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

}
